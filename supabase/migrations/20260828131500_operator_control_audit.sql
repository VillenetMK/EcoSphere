begin;

-- Every verified EcoSphere member receives operator controls automatically.
-- Existing administrators and blocked accounts are never downgraded or released.
create or replace function public.complete_user_profile(
  p_username text,
  p_first_name text,
  p_last_name text,
  p_dni text,
  p_phone text,
  p_expected_email text
)
returns boolean
language plpgsql
set search_path = ''
as $function$
declare
  v_user_id uuid := (select auth.uid());
  v_email text := lower(coalesce((select auth.jwt()->>'email'), ''));
  v_method text := lower(coalesce((select auth.jwt()->'app_metadata'->>'provider'), 'email'));
  v_username text := trim(p_username);
  v_first_name text := regexp_replace(trim(p_first_name), '[[:space:]]+', ' ', 'g');
  v_last_name text := regexp_replace(trim(p_last_name), '[[:space:]]+', ' ', 'g');
begin
  if v_user_id is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;

  if v_email = '' or v_email <> lower(trim(p_expected_email)) then
    raise exception 'verified email does not match registration email' using errcode = '22023';
  end if;

  if v_method not in ('email', 'google', 'github') then
    raise exception 'unsupported authentication provider' using errcode = '22023';
  end if;

  if v_username !~ '^[A-Za-z][A-Za-z0-9._-]{2,31}$' then
    raise exception 'invalid username format' using errcode = '22023';
  end if;

  if char_length(v_first_name) not between 2 and 80
     or v_first_name !~ '^[[:alpha:]][[:alpha:] .''’-]*[[:alpha:]]$' then
    raise exception 'invalid first name format' using errcode = '22023';
  end if;

  if char_length(v_last_name) not between 2 and 80
     or v_last_name !~ '^[[:alpha:]][[:alpha:] .''’-]*[[:alpha:]]$' then
    raise exception 'invalid last name format' using errcode = '22023';
  end if;

  insert into private.user_profiles (
    user_id,
    username,
    first_name,
    last_name,
    full_name,
    dni,
    phone,
    email,
    registration_method,
    status,
    role
  )
  values (
    v_user_id,
    v_username,
    v_first_name,
    v_last_name,
    v_first_name || ' ' || v_last_name,
    regexp_replace(p_dni, '[^0-9]', '', 'g'),
    p_phone,
    v_email,
    v_method,
    'approved',
    'operator'
  )
  on conflict (user_id) do update
    set username = excluded.username,
        first_name = excluded.first_name,
        last_name = excluded.last_name,
        full_name = excluded.full_name,
        dni = excluded.dni,
        phone = excluded.phone,
        email = excluded.email,
        registration_method = excluded.registration_method,
        status = case
          when private.user_profiles.status = 'pending'
           and private.user_profiles.role in ('viewer', 'operator')
          then 'approved'
          else private.user_profiles.status
        end,
        role = case
          when private.user_profiles.role = 'viewer'
           and private.user_profiles.status <> 'blocked'
          then 'operator'
          else private.user_profiles.role
        end,
        updated_at = now()
    where private.user_profiles.status <> 'blocked';

  return true;
end;
$function$;

-- Upgrade previously verified viewer accounts without touching administrators.
update private.user_profiles as profile
set status = 'approved',
    role = 'operator',
    updated_at = now()
from auth.users as auth_user
where auth_user.id = profile.user_id
  and auth_user.email_confirmed_at is not null
  and profile.status <> 'blocked'
  and profile.role = 'viewer';

create table private.control_audit_log (
  id bigint generated always as identity primary key,
  actor_user_id uuid references auth.users(id) on delete set null,
  actor_username text not null,
  actor_role text not null,
  auto_mode_before boolean,
  auto_mode_after boolean,
  fan_power_before integer,
  fan_power_after integer,
  led_power_before integer,
  led_power_after integer,
  pump_requested boolean not null default false,
  pump_duration_ms integer,
  created_at timestamptz not null default now(),
  constraint control_audit_actor_role
    check (actor_role in ('operator', 'admin')),
  constraint control_audit_fan_power_before
    check (fan_power_before is null or fan_power_before between 0 and 100),
  constraint control_audit_fan_power_after
    check (fan_power_after is null or fan_power_after between 0 and 100),
  constraint control_audit_led_power_before
    check (led_power_before is null or led_power_before between 0 and 100),
  constraint control_audit_led_power_after
    check (led_power_after is null or led_power_after between 0 and 100),
  constraint control_audit_pump_duration
    check (pump_duration_ms is null or pump_duration_ms between 500 and 30000),
  constraint control_audit_has_change check (
    auto_mode_before is not null
    or fan_power_before is not null
    or led_power_before is not null
    or pump_requested
  )
);

create index control_audit_log_created_at_idx
  on private.control_audit_log (created_at desc);
create index control_audit_log_actor_user_id_idx
  on private.control_audit_log (actor_user_id, created_at desc)
  where actor_user_id is not null;

alter table private.control_audit_log enable row level security;

create policy "admin_mfa_read_control_audit"
  on private.control_audit_log
  for select
  to authenticated
  using (
    coalesce((select auth.jwt()->>'aal'), '') = 'aal2'
    and exists (
      select 1
      from private.user_profiles as profile
      where profile.user_id = (select auth.uid())
        and profile.status = 'approved'
        and profile.role = 'admin'
    )
  );

revoke all on table private.control_audit_log from public, anon, authenticated;
revoke all on sequence private.control_audit_log_id_seq from public, anon, authenticated;
grant select on table private.control_audit_log to authenticated;

create function private.log_device_control_changes()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_user_id uuid := (select auth.uid());
  v_username text;
  v_role text;
begin
  -- Controller RPCs and legacy ESP32 requests do not have a human auth.uid().
  if v_user_id is null then
    return new;
  end if;

  select profile.username, profile.role
  into v_username, v_role
  from private.user_profiles as profile
  where profile.user_id = v_user_id
    and profile.status = 'approved'
    and profile.role in ('operator', 'admin');

  if not found then
    return new;
  end if;

  if new.auto_mode is not distinct from old.auto_mode
     and new.fan_power is not distinct from old.fan_power
     and new.led_power is not distinct from old.led_power
     and new.pump_request is not distinct from old.pump_request then
    return new;
  end if;

  insert into private.control_audit_log (
    actor_user_id,
    actor_username,
    actor_role,
    auto_mode_before,
    auto_mode_after,
    fan_power_before,
    fan_power_after,
    led_power_before,
    led_power_after,
    pump_requested,
    pump_duration_ms
  )
  values (
    v_user_id,
    v_username,
    v_role,
    case when new.auto_mode is distinct from old.auto_mode then old.auto_mode end,
    case when new.auto_mode is distinct from old.auto_mode then new.auto_mode end,
    case when new.fan_power is distinct from old.fan_power then old.fan_power end,
    case when new.fan_power is distinct from old.fan_power then new.fan_power end,
    case when new.led_power is distinct from old.led_power then old.led_power end,
    case when new.led_power is distinct from old.led_power then new.led_power end,
    new.pump_request > old.pump_request,
    case when new.pump_request > old.pump_request then new.pump_duration_ms end
  );

  return new;
end;
$function$;

revoke all on function private.log_device_control_changes()
  from public, anon, authenticated;

create trigger log_human_device_control_changes
after update of auto_mode, fan_power, led_power, pump_request
on public.device_control
for each row
execute function private.log_device_control_changes();

create function public.admin_control_audit(p_limit integer default 200)
returns table (
  id bigint,
  actor_username text,
  actor_role text,
  auto_mode_before boolean,
  auto_mode_after boolean,
  fan_power_before integer,
  fan_power_after integer,
  led_power_before integer,
  led_power_after integer,
  pump_requested boolean,
  pump_duration_ms integer,
  created_at timestamptz
)
language sql
stable
security invoker
set search_path = ''
as $function$
  select
    audit.id,
    audit.actor_username,
    audit.actor_role,
    audit.auto_mode_before,
    audit.auto_mode_after,
    audit.fan_power_before,
    audit.fan_power_after,
    audit.led_power_before,
    audit.led_power_after,
    audit.pump_requested,
    audit.pump_duration_ms,
    audit.created_at
  from private.control_audit_log as audit
  order by audit.created_at desc, audit.id desc
  limit greatest(1, least(coalesce(p_limit, 200), 500));
$function$;

revoke all on function public.admin_control_audit(integer)
  from public, anon;
grant execute on function public.admin_control_audit(integer)
  to authenticated;

commit;
