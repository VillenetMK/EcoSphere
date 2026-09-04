-- EcoSphere production security hardening.
--
-- Security model after this migration:
--   * anon has no direct table/view access.
--   * the ESP32 can only use the pairing and sync RPCs.
--   * a verified registration becomes an approved read-only viewer.
--   * only an AAL2 administrator can grant operator access.
--   * human actuator changes use the validated control_command RPC.
--   * controller pairing is possible only during an AAL2 admin window.
--   * repeated/out-of-order ESP32 heartbeats never mutate state or telemetry.

select pg_catalog.pg_advisory_xact_lock(
  pg_catalog.hashtextextended('ecosphere.production_security_hardening.v1', 0)
);

-- ---------------------------------------------------------------------------
-- 1. Permanently retire the unauthenticated legacy table API.
-- ---------------------------------------------------------------------------

update private.ecosystems
set legacy_writes_allowed = false,
    updated_at = now()
where legacy_writes_allowed;

alter table private.ecosystems
  alter column legacy_writes_allowed set default false;

drop policy if exists "allow_legacy_select_device_control"
  on public.device_control;
drop policy if exists "allow_legacy_update_device_control"
  on public.device_control;
drop policy if exists "allow_legacy_select_sensor_records"
  on public.sensor_records;
drop policy if exists "allow_legacy_insert_sensor_records"
  on public.sensor_records;

revoke all on table public.device_control from anon;
revoke all on table public.sensor_records from anon;
revoke all on table public.sensor_history_months from anon;

drop function if exists public.legacy_controller_writes_allowed();

-- Direct telemetry writes never belong to human clients. Direct control
-- updates are replaced below by control_command so protected columns such as
-- heartbeat_seq and active_controller_id cannot be forged from DevTools.
revoke insert, update, delete on table public.sensor_records from authenticated;
revoke insert, update, delete on table public.device_control from authenticated;

grant select on table public.sensor_records to authenticated;
grant select on table public.device_control to authenticated;
grant select on table public.sensor_history_months to authenticated;

alter table public.sensor_records enable row level security;
alter table public.device_control enable row level security;
alter view public.sensor_history_months set (security_invoker = true);

-- ---------------------------------------------------------------------------
-- 2. Session-aware authorization helpers.
-- ---------------------------------------------------------------------------

create or replace function private.current_session_is_active(
  p_require_aal2 boolean default false
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
  select
    (select auth.uid()) is not null
    and coalesce((select auth.jwt()) ->> 'session_id', '') <> ''
    and coalesce((select auth.jwt()) ->> 'is_anonymous', 'false') = 'false'
    and (not p_require_aal2 or coalesce((select auth.jwt()) ->> 'aal', '') = 'aal2')
    and exists (
      select 1
      from auth.users as auth_user
      where auth_user.id = (select auth.uid())
        and auth_user.deleted_at is null
        and coalesce(auth_user.is_anonymous, false) = false
        and (auth_user.banned_until is null or auth_user.banned_until <= now())
    )
    and exists (
      select 1
      from auth.sessions as session
      where session.id = nullif((select auth.jwt()) ->> 'session_id', '')::uuid
        and session.user_id = (select auth.uid())
        and (session.not_after is null or session.not_after > now())
        and (
          not p_require_aal2
          or coalesce(session.aal::text, '') = 'aal2'
        )
    );
$function$;

revoke all on function private.current_session_is_active(boolean)
  from public, anon, authenticated;
grant execute on function private.current_session_is_active(boolean)
  to authenticated;

create or replace function private.require_admin_aal2()
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $function$
declare
  v_user_id uuid := (select auth.uid());
begin
  if v_user_id is null
     or not private.current_session_is_active(true) then
    raise exception 'active two-factor session required'
      using errcode = '42501';
  end if;

  if not exists (
    select 1
    from private.user_profiles as profile
    where profile.user_id = v_user_id
      and profile.status = 'approved'
      and profile.role = 'admin'
  ) then
    raise exception 'administrator access required'
      using errcode = '42501';
  end if;

  return v_user_id;
end;
$function$;

revoke all on function private.require_admin_aal2()
  from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 3. RLS rebuilt around an active session and live database roles.
-- ---------------------------------------------------------------------------

drop policy if exists "approved_users_read_sensor_records"
  on public.sensor_records;
create policy "approved_users_read_sensor_records"
  on public.sensor_records
  for select
  to authenticated
  using (
    (select private.current_session_is_active(false))
    and exists (
      select 1
      from private.user_profiles as profile
      where profile.user_id = (select auth.uid())
        and profile.status = 'approved'
    )
  );

drop policy if exists "admin_mfa_required_sensor_records"
  on public.sensor_records;
create policy "admin_mfa_required_sensor_records"
  on public.sensor_records
  as restrictive
  for select
  to authenticated
  using (
    not exists (
      select 1
      from private.user_profiles as profile
      where profile.user_id = (select auth.uid())
        and profile.status = 'approved'
        and profile.role = 'admin'
    )
    or (select private.current_session_is_active(true))
  );

drop policy if exists "approved_users_read_device_control"
  on public.device_control;
create policy "approved_users_read_device_control"
  on public.device_control
  for select
  to authenticated
  using (
    (select private.current_session_is_active(false))
    and exists (
      select 1
      from private.user_profiles as profile
      where profile.user_id = (select auth.uid())
        and profile.status = 'approved'
    )
  );

drop policy if exists "admin_mfa_required_device_control_read"
  on public.device_control;
create policy "admin_mfa_required_device_control_read"
  on public.device_control
  as restrictive
  for select
  to authenticated
  using (
    not exists (
      select 1
      from private.user_profiles as profile
      where profile.user_id = (select auth.uid())
        and profile.status = 'approved'
        and profile.role = 'admin'
    )
    or (select private.current_session_is_active(true))
  );

drop policy if exists "operators_update_device_control"
  on public.device_control;
drop policy if exists "admin_mfa_required_device_control_update"
  on public.device_control;

drop policy if exists "users_read_own_private_profile"
  on private.user_profiles;
create policy "users_read_own_private_profile"
  on private.user_profiles
  for select
  to authenticated
  using (
    (select private.current_session_is_active(false))
    and user_id = (select auth.uid())
  );

-- Profile creation/update is RPC-only after this point.
drop policy if exists "users_insert_own_valid_profile"
  on private.user_profiles;
drop policy if exists "users_update_own_valid_identity"
  on private.user_profiles;

revoke insert (
  user_id, username, first_name, last_name, full_name, dni, phone,
  email, registration_method, status, role, created_at, updated_at
) on private.user_profiles from authenticated;
revoke update (
  user_id, username, first_name, last_name, full_name, dni, phone,
  email, registration_method, status, role, created_at, updated_at
) on private.user_profiles from authenticated;
grant select on table private.user_profiles to authenticated;

drop function if exists private.profile_identity_allowed(text, text, text);

drop policy if exists "admin_mfa_read_control_audit"
  on private.control_audit_log;
alter table private.control_audit_log enable row level security;
alter table private.control_audit_log force row level security;
create policy "admin_mfa_read_control_audit"
  on private.control_audit_log
  for select
  to authenticated
  using (
    (select private.current_session_is_active(true))
    and exists (
      select 1
      from private.user_profiles as profile
      where profile.user_id = (select auth.uid())
        and profile.status = 'approved'
        and profile.role = 'admin'
    )
  );

-- ---------------------------------------------------------------------------
-- 4. Verified registrations receive viewer access, never actuator access.
-- ---------------------------------------------------------------------------

alter table private.user_profiles
  alter column status set default 'pending',
  alter column role set default 'viewer';

create or replace function private.complete_user_profile_impl(
  p_username text,
  p_first_name text,
  p_last_name text,
  p_dni text,
  p_phone text,
  p_expected_email text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_user_id uuid := (select auth.uid());
  v_auth_email text;
  v_email_confirmed_at timestamptz;
  v_method text;
  v_username text;
  v_first_name text;
  v_last_name text;
  v_dni text;
  v_phone_input text;
  v_phone_digits text;
  v_phone text;
  v_reserved_email text;
begin
  if v_user_id is null
     or not private.current_session_is_active(false) then
    raise exception 'active authenticated session required'
      using errcode = '42501';
  end if;

  if octet_length(coalesce(p_username, '')) > 64
     or octet_length(coalesce(p_first_name, '')) > 256
     or octet_length(coalesce(p_last_name, '')) > 256
     or octet_length(coalesce(p_dni, '')) > 32
     or octet_length(coalesce(p_phone, '')) > 64
     or octet_length(coalesce(p_expected_email, '')) > 320 then
    raise exception 'registration field exceeds maximum length'
      using errcode = '22023';
  end if;

  v_username := btrim(coalesce(p_username, ''));
  v_first_name := regexp_replace(
    btrim(coalesce(p_first_name, '')), '[[:space:]]+', ' ', 'g'
  );
  v_last_name := regexp_replace(
    btrim(coalesce(p_last_name, '')), '[[:space:]]+', ' ', 'g'
  );
  v_phone_input := btrim(coalesce(p_phone, ''));

  if btrim(coalesce(p_dni, '')) !~ '^[0-9]{8}$' then
    raise exception 'DNI must contain exactly 8 digits'
      using errcode = '22023';
  end if;
  v_dni := btrim(p_dni);

  if v_phone_input !~ '^[+]?[0-9][0-9[:space:]-]*$' then
    raise exception 'phone contains unsupported characters'
      using errcode = '22023';
  end if;
  v_phone_digits := regexp_replace(v_phone_input, '[^0-9]', '', 'g');

  select lower(coalesce(auth_user.email, '')),
         auth_user.email_confirmed_at,
         lower(coalesce(auth_user.raw_app_meta_data ->> 'provider', 'email'))
    into v_auth_email, v_email_confirmed_at, v_method
  from auth.users as auth_user
  where auth_user.id = v_user_id;

  if not found or v_email_confirmed_at is null then
    raise exception 'verified email required' using errcode = '42501';
  end if;

  if v_auth_email = ''
     or v_auth_email <> lower(btrim(coalesce(p_expected_email, ''))) then
    raise exception 'verified email does not match registration email'
      using errcode = '22023';
  end if;

  if v_method not in ('email', 'google', 'github') then
    raise exception 'unsupported authentication provider'
      using errcode = '22023';
  end if;

  if v_username !~ '^[A-Za-z][A-Za-z0-9._-]{2,31}$' then
    raise exception 'invalid username format' using errcode = '22023';
  end if;

  if exists (
    select 1
    from private.user_profiles as existing_profile
    where existing_profile.user_id = v_user_id
      and existing_profile.role = 'admin'
  ) then
    perform private.require_admin_aal2();
  end if;

  select reserved.reserved_for_email
    into v_reserved_email
  from private.reserved_usernames as reserved
  where reserved.username = lower(v_username);

  if found
     and (v_reserved_email is null or v_reserved_email <> v_auth_email) then
    raise exception 'username is reserved' using errcode = '23505';
  end if;

  if char_length(v_first_name) not between 2 and 80
     or v_first_name !~ '^[[:alpha:]][[:alpha:] .''’-]*[[:alpha:]]$' then
    raise exception 'invalid first name format' using errcode = '22023';
  end if;

  if char_length(v_last_name) not between 2 and 80
     or v_last_name !~ '^[[:alpha:]][[:alpha:] .''’-]*[[:alpha:]]$' then
    raise exception 'invalid last name format' using errcode = '22023';
  end if;

  -- Peru is the UI default. An explicit international E.164 value remains valid.
  if v_phone_input ~ '^[+]' then
    v_phone := '+' || v_phone_digits;
  elsif v_phone_digits ~ '^9[0-9]{8}$' then
    v_phone := '+51' || v_phone_digits;
  elsif v_phone_digits ~ '^51[0-9]{9}$' then
    v_phone := '+' || v_phone_digits;
  else
    raise exception 'phone must use E.164 format or a 9-digit Peru mobile number'
      using errcode = '22023';
  end if;

  if v_phone !~ '^[+][1-9][0-9]{7,14}$' then
    raise exception 'invalid E.164 phone number' using errcode = '22023';
  end if;

  insert into private.user_profiles (
    user_id, username, first_name, last_name, full_name, dni, phone,
    email, registration_method, status, role
  )
  values (
    v_user_id, v_username, v_first_name, v_last_name,
    v_first_name || ' ' || v_last_name,
    v_dni, v_phone, v_auth_email, v_method, 'approved', 'viewer'
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
           and private.user_profiles.role = 'viewer'
          then 'approved'
          else private.user_profiles.status
        end,
        updated_at = now()
    where private.user_profiles.status <> 'blocked';

  if not found then
    raise exception 'blocked profiles cannot be changed'
      using errcode = '42501';
  end if;

  return true;
end;
$function$;

revoke all on function private.complete_user_profile_impl(
  text, text, text, text, text, text
) from public, anon, authenticated;
grant execute on function private.complete_user_profile_impl(
  text, text, text, text, text, text
) to authenticated;

create or replace function public.complete_user_profile(
  p_username text,
  p_first_name text,
  p_last_name text,
  p_dni text,
  p_phone text,
  p_expected_email text
)
returns boolean
language sql
volatile
security invoker
set search_path = ''
as $function$
  select private.complete_user_profile_impl($1, $2, $3, $4, $5, $6);
$function$;

revoke all on function public.complete_user_profile(
  text, text, text, text, text, text
) from public, anon, authenticated;
grant execute on function public.complete_user_profile(
  text, text, text, text, text, text
) to authenticated;

-- ---------------------------------------------------------------------------
-- 5. AAL2-only user administration with an immutable audit trail.
-- ---------------------------------------------------------------------------

create table if not exists private.user_access_audit (
  id bigint generated always as identity primary key,
  actor_user_id uuid references auth.users(id) on delete set null,
  actor_username text not null,
  target_user_id uuid references auth.users(id) on delete set null,
  target_username text not null,
  status_before text not null,
  status_after text not null,
  role_before text not null,
  role_after text not null,
  created_at timestamptz not null default now(),
  constraint user_access_audit_status_before
    check (status_before in ('pending', 'approved', 'blocked')),
  constraint user_access_audit_status_after
    check (status_after in ('pending', 'approved', 'blocked')),
  constraint user_access_audit_role_before
    check (role_before in ('viewer', 'operator', 'admin')),
  constraint user_access_audit_role_after
    check (role_after in ('viewer', 'operator', 'admin'))
);

create index if not exists user_access_audit_created_at_idx
  on private.user_access_audit (created_at desc);
create index if not exists user_access_audit_target_idx
  on private.user_access_audit (target_user_id, created_at desc)
  where target_user_id is not null;

alter table private.user_access_audit enable row level security;
alter table private.user_access_audit force row level security;

drop policy if exists "admin_mfa_read_user_access_audit"
  on private.user_access_audit;
create policy "admin_mfa_read_user_access_audit"
  on private.user_access_audit
  for select
  to authenticated
  using (
    (select private.current_session_is_active(true))
    and exists (
      select 1
      from private.user_profiles as profile
      where profile.user_id = (select auth.uid())
        and profile.status = 'approved'
        and profile.role = 'admin'
    )
  );

revoke all on table private.user_access_audit
  from public, anon, authenticated;
revoke all on sequence private.user_access_audit_id_seq
  from public, anon, authenticated;
grant select on table private.user_access_audit to authenticated;

create or replace function private.admin_list_users_impl(
  p_limit integer default 200
)
returns table (
  user_id uuid,
  username text,
  full_name text,
  email text,
  status text,
  role text,
  created_at timestamptz,
  updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $function$
begin
  perform private.require_admin_aal2();

  return query
    select profile.user_id, profile.username, profile.full_name,
           profile.email, profile.status, profile.role,
           profile.created_at, profile.updated_at
    from private.user_profiles as profile
    order by profile.created_at desc, profile.user_id
    limit greatest(1, least(coalesce(p_limit, 200), 500));
end;
$function$;

create or replace function private.admin_set_user_access_impl(
  p_user_id uuid,
  p_role text,
  p_status text
)
returns table (
  user_id uuid,
  username text,
  full_name text,
  email text,
  status text,
  role text,
  created_at timestamptz,
  updated_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_actor_id uuid := private.require_admin_aal2();
  v_actor_username text;
  v_target private.user_profiles%rowtype;
  v_role text := lower(btrim(coalesce(p_role, '')));
  v_status text := lower(btrim(coalesce(p_status, '')));
begin
  if p_user_id is null then
    raise exception 'target user is required' using errcode = '22023';
  end if;

  if p_user_id = v_actor_id then
    raise exception 'administrators cannot change their own access'
      using errcode = '42501';
  end if;

  if v_role not in ('viewer', 'operator')
     or v_status not in ('approved', 'blocked') then
    raise exception 'allowed roles: viewer/operator; allowed status: approved/blocked'
      using errcode = '22023';
  end if;

  select * into v_target
  from private.user_profiles as profile
  where profile.user_id = p_user_id
  for update;

  if not found then
    raise exception 'target profile not found' using errcode = 'P0002';
  end if;

  if v_target.role = 'admin' then
    raise exception 'administrator accounts cannot be changed through this RPC'
      using errcode = '42501';
  end if;

  select profile.username into v_actor_username
  from private.user_profiles as profile
  where profile.user_id = v_actor_id;

  update private.user_profiles as profile
  set role = v_role,
      status = v_status,
      updated_at = now()
  where profile.user_id = p_user_id;

  if v_target.role is distinct from v_role
     or v_target.status is distinct from v_status then
    insert into private.user_access_audit (
      actor_user_id, actor_username, target_user_id, target_username,
      status_before, status_after, role_before, role_after
    ) values (
      v_actor_id, v_actor_username, v_target.user_id, v_target.username,
      v_target.status, v_status, v_target.role, v_role
    );
  end if;

  return query
    select profile.user_id, profile.username, profile.full_name,
           profile.email, profile.status, profile.role,
           profile.created_at, profile.updated_at
    from private.user_profiles as profile
    where profile.user_id = p_user_id;
end;
$function$;

revoke all on function private.admin_list_users_impl(integer)
  from public, anon, authenticated;
revoke all on function private.admin_set_user_access_impl(uuid, text, text)
  from public, anon, authenticated;
grant execute on function private.admin_list_users_impl(integer)
  to authenticated;
grant execute on function private.admin_set_user_access_impl(uuid, text, text)
  to authenticated;

create or replace function public.admin_list_users(p_limit integer default 200)
returns table (
  user_id uuid, username text, full_name text, email text,
  status text, role text, created_at timestamptz, updated_at timestamptz
)
language sql
stable
security invoker
set search_path = ''
as $function$
  select * from private.admin_list_users_impl($1);
$function$;

create or replace function public.admin_set_user_access(
  p_user_id uuid,
  p_role text,
  p_status text
)
returns table (
  user_id uuid, username text, full_name text, email text,
  status text, role text, created_at timestamptz, updated_at timestamptz
)
language sql
volatile
security invoker
set search_path = ''
as $function$
  select * from private.admin_set_user_access_impl($1, $2, $3);
$function$;

revoke all on function public.admin_list_users(integer)
  from public, anon, authenticated;
revoke all on function public.admin_set_user_access(uuid, text, text)
  from public, anon, authenticated;
grant execute on function public.admin_list_users(integer)
  to authenticated;
grant execute on function public.admin_set_user_access(uuid, text, text)
  to authenticated;

create or replace function private.admin_control_audit_impl(
  p_limit integer default 200
)
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
language plpgsql
stable
security definer
set search_path = ''
as $function$
begin
  perform private.require_admin_aal2();

  return query
    select audit.id, audit.actor_username, audit.actor_role,
           audit.auto_mode_before, audit.auto_mode_after,
           audit.fan_power_before, audit.fan_power_after,
           audit.led_power_before, audit.led_power_after,
           audit.pump_requested, audit.pump_duration_ms, audit.created_at
    from private.control_audit_log as audit
    order by audit.created_at desc, audit.id desc
    limit greatest(1, least(coalesce(p_limit, 200), 500));
end;
$function$;

revoke all on function private.admin_control_audit_impl(integer)
  from public, anon, authenticated, service_role;
grant execute on function private.admin_control_audit_impl(integer)
  to authenticated;

create or replace function public.admin_control_audit(
  p_limit integer default 200
)
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
  select * from private.admin_control_audit_impl($1);
$function$;

revoke all on function public.admin_control_audit(integer)
  from public, anon, authenticated, service_role;
grant execute on function public.admin_control_audit(integer)
  to authenticated;

-- ---------------------------------------------------------------------------
-- 6. The only human write path for physical controls.
-- ---------------------------------------------------------------------------

alter table public.device_control
  add column if not exists pump_expires_at timestamptz;

-- Commands created before TTL support must never become executable later.
update public.device_control
set pump_expires_at = null
where pump_expires_at is not null;

create or replace function private.control_command_impl(
  p_action text,
  p_value integer default null
)
returns setof public.device_control
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_user_id uuid := (select auth.uid());
  v_role text;
  v_action text;
  v_control public.device_control%rowtype;
  v_soil_humidity double precision;
  v_water_level text;
  v_telemetry_at timestamptz;
  v_duration integer;
begin
  if octet_length(coalesce(p_action, '')) > 32 then
    raise exception 'control action is too long' using errcode = '22023';
  end if;
  v_action := lower(btrim(coalesce(p_action, '')));

  if v_user_id is null
     or not private.current_session_is_active(false) then
    raise exception 'active authenticated session required'
      using errcode = '42501';
  end if;

  select profile.role into v_role
  from private.user_profiles as profile
  where profile.user_id = v_user_id
    and profile.status = 'approved'
    and profile.role in ('operator', 'admin');

  if not found then
    raise exception 'operator access required' using errcode = '42501';
  end if;

  if v_role = 'admin' then
    perform private.require_admin_aal2();
  end if;

  -- Reject malformed and unsupported commands before taking the singleton
  -- control lock. This keeps unproductive requests from serializing valid
  -- operators and makes every accepted request either an audited state change
  -- or a deliberately audited pump command.
  if v_action not in ('auto_mode', 'fan_power', 'led_power', 'pump') then
    raise exception 'unsupported control action'
      using errcode = '22023';
  end if;

  if v_action = 'auto_mode'
     and (p_value is null or p_value not in (0, 1)) then
    raise exception 'auto_mode value must be 0 or 1'
      using errcode = '22023';
  elsif v_action in ('fan_power', 'led_power')
        and (p_value is null or p_value not between 0 and 100) then
    raise exception '% power must be between 0 and 100',
      case when v_action = 'fan_power' then 'fan' else 'LED' end
      using errcode = '22023';
  elsif v_action = 'pump' then
    v_duration := coalesce(p_value, 3000);
    if v_duration not between 500 and 10000 then
      raise exception 'pump duration must be between 500 and 10000 ms'
        using errcode = '22023';
    end if;
  end if;

  select * into v_control
  from public.device_control as control
  where control.id = 1
  for update;

  if not found then
    raise exception 'device control row not found' using errcode = 'P0002';
  end if;

  -- A repeated set operation is not a command: it must not generate WAL,
  -- silently clear a pending pump TTL, or evade the immutable audit trail.
  -- Power equality alone is intentional: a legacy target/power mismatch may
  -- only be repaired by an explicit, auditable transition through a new value.
  if (v_action = 'auto_mode'
      and v_control.auto_mode is not distinct from (p_value = 1))
     or (v_action = 'fan_power'
         and v_control.fan_power is not distinct from p_value)
     or (v_action = 'led_power'
         and v_control.led_power is not distinct from p_value) then
    raise exception 'control state is unchanged' using errcode = '55000';
  end if;

  if v_action in ('auto_mode', 'fan_power', 'led_power') then
    if exists (
      select 1
      from private.control_audit_log as audit
      where audit.created_at > now() - interval '1 second'
        and (
          (v_action = 'auto_mode' and audit.auto_mode_after is not null)
          or (v_action = 'fan_power' and audit.fan_power_after is not null)
          or (v_action = 'led_power' and audit.led_power_after is not null)
        )
    ) then
      raise exception 'control denied: system command cooldown is active'
        using errcode = '55000';
    end if;

    if exists (
      select 1
      from private.control_audit_log as audit
      where audit.actor_user_id = v_user_id
        and audit.created_at > now() - interval '2 seconds'
        and (
          (v_action = 'auto_mode' and audit.auto_mode_after is not null)
          or (v_action = 'fan_power' and audit.fan_power_after is not null)
          or (v_action = 'led_power' and audit.led_power_after is not null)
        )
    ) then
      raise exception 'control denied: operator command cooldown is active'
        using errcode = '55000';
    end if;
  end if;

  case v_action
    when 'auto_mode' then
      update public.device_control
      set auto_mode = (p_value = 1),
          pump_expires_at = null
      where id = 1;

    when 'fan_power' then
      if v_control.auto_mode then
        raise exception 'manual fan control is disabled in automatic mode'
          using errcode = '55000';
      end if;

      update public.device_control
      set fan_power = p_value,
          fan_target = (p_value > 0)
      where id = 1;

    when 'led_power' then
      if v_control.auto_mode then
        raise exception 'manual LED control is disabled in automatic mode'
          using errcode = '55000';
      end if;

      update public.device_control
      set led_power = p_value,
          led_target = (p_value > 0)
      where id = 1;

    when 'pump' then
      if v_control.auto_mode then
        raise exception 'manual watering is disabled in automatic mode'
          using errcode = '55000';
      end if;

      -- The locked singleton control row serializes this check with the audit
      -- trigger, so concurrent taps cannot race around either cooldown.
      if exists (
        select 1
        from private.control_audit_log as audit
        where audit.pump_requested
          and audit.created_at > now() - interval '10 seconds'
      ) then
        raise exception 'watering denied: system pump cooldown is active'
          using errcode = '55000';
      end if;

      if exists (
        select 1
        from private.control_audit_log as audit
        where audit.pump_requested
          and audit.actor_user_id = v_user_id
          and audit.created_at > now() - interval '60 seconds'
      ) then
        raise exception 'watering denied: operator pump cooldown is active'
          using errcode = '55000';
      end if;

      select record.soil_humidity, lower(record.water_level), record.created_at
        into v_soil_humidity, v_water_level, v_telemetry_at
      from public.sensor_records as record
      where record.controller_id = v_control.active_controller_id
      order by record.created_at desc, record.id desc
      limit 1;

      if not found or v_telemetry_at < now() - interval '30 seconds' then
        raise exception 'watering denied: current telemetry is unavailable'
          using errcode = '55000';
      end if;
      if v_soil_humidity is null then
        raise exception 'watering denied: soil sensor is unavailable'
          using errcode = '55000';
      end if;
      if v_soil_humidity >= 60 then
        raise exception 'watering denied: soil humidity is already 60 percent or higher'
          using errcode = '55000';
      end if;
      if v_water_level is distinct from 'high' then
        raise exception 'watering denied: water level is not sufficient'
          using errcode = '55000';
      end if;

      update public.device_control
      set pump_request = pump_request + 1,
          pump_duration_ms = v_duration,
          pump_expires_at = now() + interval '15 seconds'
      where id = 1;

  end case;

  return query
    select control.*
    from public.device_control as control
    where control.id = 1;
end;
$function$;

revoke all on function private.control_command_impl(text, integer)
  from public, anon, authenticated;
grant execute on function private.control_command_impl(text, integer)
  to authenticated;

create or replace function public.control_command(
  p_action text,
  p_value integer default null
)
returns setof public.device_control
language sql
volatile
security invoker
set search_path = ''
as $function$
  select * from private.control_command_impl($1, $2);
$function$;

revoke all on function public.control_command(text, integer)
  from public, anon, authenticated;
grant execute on function public.control_command(text, integer)
  to authenticated;

-- ---------------------------------------------------------------------------
-- 7. Pairing is closed by default and opened briefly by an AAL2 admin.
-- ---------------------------------------------------------------------------

alter table private.ecosystems
  add column if not exists pairing_open_until timestamptz,
  add column if not exists pairing_expected_hardware_uid text,
  add column if not exists pairing_expected_claim_proof text,
  add column if not exists strict_controller_protocol boolean
    not null default false;

do $constraint$
begin
  if not exists (
    select 1
    from pg_catalog.pg_constraint as constraint_row
    where constraint_row.conrelid = 'private.ecosystems'::regclass
      and constraint_row.conname = 'ecosystems_pairing_expected_uid_format'
  ) then
    alter table private.ecosystems
      add constraint ecosystems_pairing_expected_uid_format check (
        pairing_expected_hardware_uid is null
        or pairing_expected_hardware_uid ~ '^[0-9A-F]{12}$'
      );
  end if;

  if not exists (
    select 1
    from pg_catalog.pg_constraint as constraint_row
    where constraint_row.conrelid = 'private.ecosystems'::regclass
      and constraint_row.conname = 'ecosystems_pairing_expected_proof_format'
  ) then
    alter table private.ecosystems
      add constraint ecosystems_pairing_expected_proof_format check (
        pairing_expected_claim_proof is null
        or pairing_expected_claim_proof ~ '^[0-9A-F]{24}$'
      );
  end if;
end;
$constraint$;

alter table private.ecosystems
  alter column strict_controller_protocol set default false,
  alter column strict_controller_protocol set not null;

-- Once a controller proves support for the nonce-based protocol, downgrade is
-- permanently disabled. Even a privileged application function cannot flip
-- this bit back by accident.
create or replace function private.prevent_controller_protocol_downgrade()
returns trigger
language plpgsql
set search_path = ''
as $function$
begin
  if old.strict_controller_protocol
     and not new.strict_controller_protocol then
    raise exception 'strict controller protocol cannot be disabled'
      using errcode = '42501';
  end if;
  return new;
end;
$function$;

revoke all on function private.prevent_controller_protocol_downgrade()
  from public, anon, authenticated, service_role;

drop trigger if exists prevent_controller_protocol_downgrade
  on private.ecosystems;
create trigger prevent_controller_protocol_downgrade
before update of strict_controller_protocol on private.ecosystems
for each row execute function private.prevent_controller_protocol_downgrade();

update private.ecosystems
set pairing_open_until = null,
    pairing_expected_hardware_uid = null,
    pairing_expected_claim_proof = null,
    updated_at = now()
where pairing_open_until is not null
   or pairing_expected_hardware_uid is not null
   or pairing_expected_claim_proof is not null;

do $constraint$
begin
  if not exists (
    select 1
    from pg_catalog.pg_constraint as constraint_row
    where constraint_row.conrelid = 'private.ecosystems'::regclass
      and constraint_row.conname = 'ecosystems_pairing_window_complete'
  ) then
    alter table private.ecosystems
      add constraint ecosystems_pairing_window_complete check (
        (
          pairing_open_until is null
          and pairing_expected_hardware_uid is null
          and pairing_expected_claim_proof is null
        )
        or (
          pairing_open_until is not null
          and pairing_expected_hardware_uid is not null
          and pairing_expected_claim_proof is not null
        )
      );
  end if;
end;
$constraint$;

-- Invalidate codes issued before the pairing-window model.
update private.device_controllers
set status = case when status = 'pending' then 'standby' else status end,
    pairing_code_hash = null,
    pairing_expires_at = null,
    updated_at = now()
where status <> 'active'
  and (pairing_code_hash is not null or pairing_expires_at is not null);

-- Preserve only the newest pending replacement per ecosystem before enforcing
-- the invariant. Historical duplicates remain auditable as revoked rows.
with ranked_pending as (
  select controller.id,
         row_number() over (
           partition by controller.ecosystem_id
           order by controller.updated_at desc, controller.id desc
         ) as position
  from private.device_controllers as controller
  where controller.status = 'pending'
)
update private.device_controllers as controller
set status = 'revoked',
    pairing_code_hash = null,
    pairing_expires_at = null,
    updated_at = now()
from ranked_pending
where ranked_pending.id = controller.id
  and ranked_pending.position > 1;

update private.device_controllers
set status = 'standby',
    pairing_code_hash = null,
    pairing_expires_at = null,
    updated_at = now()
where status = 'pending'
  and (
    pairing_code_hash is null
    or pairing_expires_at is null
    or pairing_expires_at <= now()
  );

create unique index if not exists device_controllers_one_pending_idx
  on private.device_controllers (ecosystem_id)
  where status = 'pending';

alter table private.controller_events
  drop constraint if exists controller_events_type;
alter table private.controller_events
  add constraint controller_events_type check (
    event_type in (
      'pairing_window_opened',
      'pairing_started',
      'controller_activated',
      'controller_replaced',
      'secure_mode_enabled',
      'strict_protocol_enabled'
    )
  );

create or replace function private.controller_supports_strict_protocol(
  p_firmware_version text
)
returns boolean
language plpgsql
immutable
security invoker
set search_path = ''
as $function$
declare
  v_version text := btrim(coalesce(p_firmware_version, ''));
  v_without_build text;
  v_core text;
  v_major numeric;
  v_minor numeric;
  v_patch numeric;
begin
  if char_length(v_version) not between 5 and 40
     or v_version !~
       '^(0|[1-9][0-9]*)[.](0|[1-9][0-9]*)[.](0|[1-9][0-9]*)([+][0-9A-Za-z-]+([.][0-9A-Za-z-]+)*)?$'
  then
    return false;
  end if;

  v_without_build := split_part(v_version, '+', 1);
  v_core := v_without_build;
  v_major := split_part(v_core, '.', 1)::numeric;
  v_minor := split_part(v_core, '.', 2)::numeric;
  v_patch := split_part(v_core, '.', 3)::numeric;

  return v_major > 2
    or (v_major = 2 and v_minor > 1)
    or (v_major = 2 and v_minor = 1 and v_patch >= 0);
end;
$function$;

revoke all on function private.controller_supports_strict_protocol(text)
  from public, anon, authenticated, service_role;

drop function if exists public.controller_open_pairing_window(integer);
drop function if exists public.controller_open_pairing_window(text, integer);
drop function if exists public.controller_open_pairing_window(text, text, integer);
drop function if exists private.controller_open_pairing_window_impl(integer);
drop function if exists private.controller_open_pairing_window_impl(text, integer);
drop function if exists private.controller_open_pairing_window_impl(text, text, integer);

create function private.controller_open_pairing_window_impl(
  p_expected_hardware_uid text,
  p_expected_claim_proof text,
  p_minutes integer default 2
)
returns table (pairing_open_until timestamptz)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_user_id uuid := private.require_admin_aal2();
  v_expected_hardware_uid text;
  v_expected_claim_proof text;
  v_until timestamptz;
begin
  if octet_length(coalesce(p_expected_hardware_uid, '')) > 64 then
    raise exception 'expected controller hardware uid is too long'
      using errcode = '22023';
  end if;

  if octet_length(coalesce(p_expected_claim_proof, '')) > 64 then
    raise exception 'expected controller claim proof is too long'
      using errcode = '22023';
  end if;

  v_expected_hardware_uid := upper(btrim(coalesce(p_expected_hardware_uid, '')));
  v_expected_claim_proof := upper(btrim(coalesce(p_expected_claim_proof, '')));

  if v_expected_hardware_uid !~ '^[0-9A-F]{12}$' then
    raise exception 'expected controller hardware uid must contain 12 hex characters'
      using errcode = '22023';
  end if;


  if v_expected_claim_proof !~ '^[0-9A-F]{24}$' then
    raise exception 'expected controller claim proof must contain 24 hex characters'
      using errcode = '22023';
  end if;

  if p_minutes is distinct from 2 then
    raise exception 'pairing window is fixed at 2 minutes'
      using errcode = '22023';
  end if;

  -- Global lock order is ecosystem -> controller -> device_control.
  perform 1
  from private.ecosystems as ecosystem
  where ecosystem.id = 1
  for update;

  if not found then
    raise exception 'controller ecosystem is inconsistent'
      using errcode = '55000';
  end if;

  update private.device_controllers
  set status = 'standby',
      pairing_code_hash = null,
      pairing_expires_at = null,
      updated_at = now()
  where ecosystem_id = 1
    and status = 'pending'
    and pairing_expires_at <= now();

  if exists (
    select 1
    from private.device_controllers as controller
    where controller.ecosystem_id = 1
      and controller.status = 'pending'
  ) then
    raise exception 'a controller pairing request is already pending'
      using errcode = '55000';
  end if;

  v_until := now() + pg_catalog.make_interval(mins => p_minutes);

  update private.ecosystems
  set pairing_open_until = v_until,
      pairing_expected_hardware_uid = v_expected_hardware_uid,
      pairing_expected_claim_proof = v_expected_claim_proof,
      updated_at = now()
  where id = 1;

  insert into private.controller_events (
    ecosystem_id, event_type, actor_user_id, details
  ) values (
    1, 'pairing_window_opened', v_user_id,
    pg_catalog.jsonb_build_object('minutes', p_minutes)
  );

  return query select v_until;
end;
$function$;

revoke all on function private.controller_open_pairing_window_impl(text, text, integer)
  from public, anon, authenticated;
grant execute on function private.controller_open_pairing_window_impl(text, text, integer)
  to authenticated;

drop function if exists public.admin_open_pairing_window(integer);

create function public.controller_open_pairing_window(
  p_expected_hardware_uid text,
  p_expected_claim_proof text,
  p_minutes integer default 2
)
returns table (pairing_open_until timestamptz)
language sql
volatile
security invoker
set search_path = ''
as $function$
  select * from private.controller_open_pairing_window_impl($1, $2, $3);
$function$;

revoke all on function public.controller_open_pairing_window(text, text, integer)
  from public, anon, authenticated;
grant execute on function public.controller_open_pairing_window(text, text, integer)
  to authenticated;

create or replace function private.controller_begin_pairing_impl(
  p_hardware_uid text,
  p_device_secret text,
  p_firmware_version text default null
)
returns table (
  pairing_code text,
  expires_at timestamptz,
  controller_status text
)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_hardware_uid text;
  v_secret text;
  v_secret_hash bytea;
  v_claim_proof text;
  v_code_plain text;
  v_code_hash bytea;
  v_controller private.device_controllers%rowtype;
  v_expires_at timestamptz := now() + interval '5 minutes';
  v_pairing_open_until timestamptz;
  v_expected_hardware_uid text;
  v_expected_claim_proof text;
  v_strict_protocol boolean;
begin
  if octet_length(coalesce(p_hardware_uid, '')) > 64
     or octet_length(coalesce(p_device_secret, '')) > 128
     or octet_length(coalesce(p_firmware_version, '')) > 64 then
    raise exception 'controller pairing field exceeds maximum length'
      using errcode = '22023';
  end if;

  v_hardware_uid := upper(btrim(coalesce(p_hardware_uid, '')));
  v_secret := lower(btrim(coalesce(p_device_secret, '')));

  if v_hardware_uid !~ '^[0-9A-F]{12}$' then
    raise exception 'invalid controller hardware uid' using errcode = '22023';
  end if;

  -- Cheap, unlocked rejection avoids controller locks while no matching
  -- admin-authorized window exists. UID and proof are both revalidated under
  -- the ecosystem lock before any controller row is touched.
  select ecosystem.pairing_open_until,
         ecosystem.pairing_expected_hardware_uid,
         ecosystem.pairing_expected_claim_proof
    into v_pairing_open_until, v_expected_hardware_uid,
         v_expected_claim_proof
  from private.ecosystems as ecosystem
  where ecosystem.id = 1;

  if v_pairing_open_until is null
     or v_pairing_open_until <= now()
     or v_expected_hardware_uid is distinct from v_hardware_uid then
    raise exception 'controller pairing is not authorized'
      using errcode = '42501';
  end if;

  if v_secret !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid controller secret' using errcode = '22023';
  end if;
  if p_firmware_version is not null
     and char_length(btrim(p_firmware_version)) not between 1 and 40 then
    raise exception 'invalid firmware version' using errcode = '22023';
  end if;

  v_claim_proof := upper(substr(pg_catalog.encode(
    extensions.digest(
      pg_catalog.convert_to(
        'ecosphere-pairing-v1:' || v_hardware_uid || ':' || v_secret,
        'UTF8'
      ),
      'sha256'
    ),
    'hex'
  ), 1, 24));

  if v_expected_claim_proof is distinct from v_claim_proof then
    raise exception 'controller pairing is not authorized'
      using errcode = '42501';
  end if;

  select ecosystem.pairing_open_until,
         ecosystem.pairing_expected_hardware_uid,
         ecosystem.pairing_expected_claim_proof,
         ecosystem.strict_controller_protocol
    into v_pairing_open_until, v_expected_hardware_uid,
         v_expected_claim_proof, v_strict_protocol
  from private.ecosystems as ecosystem
  where ecosystem.id = 1
  for update;

  if v_pairing_open_until is null
     or v_pairing_open_until <= now()
     or v_expected_hardware_uid is distinct from v_hardware_uid
     or v_expected_claim_proof is distinct from v_claim_proof then
    raise exception 'controller pairing is not authorized'
      using errcode = '42501';
  end if;

  if v_strict_protocol
     and not private.controller_supports_strict_protocol(p_firmware_version) then
    raise exception 'controller firmware does not support the required strict protocol'
      using errcode = '42501';
  end if;

  v_code_plain := upper(pg_catalog.encode(extensions.gen_random_bytes(6), 'hex'));
  v_secret_hash := extensions.digest(v_secret, 'sha256');
  v_code_hash := extensions.digest(v_code_plain, 'sha256');

  select * into v_controller
  from private.device_controllers as controller
  where controller.hardware_uid = v_hardware_uid
  for update;

  if found then
    if v_controller.secret_hash <> v_secret_hash then
      raise exception 'controller identity mismatch' using errcode = '42501';
    end if;
    if v_controller.status = 'active' then
      return query select null::text, null::timestamptz, 'active'::text;
      return;
    end if;
    if v_controller.status = 'revoked' then
      raise exception 'controller has been revoked' using errcode = '42501';
    end if;
  end if;

  -- A window admits exactly one pairing attempt, preventing bulk enrollment.
  update private.ecosystems
  set pairing_open_until = null,
      pairing_expected_hardware_uid = null,
      pairing_expected_claim_proof = null,
      updated_at = now()
  where id = 1;

  if v_controller.id is not null then
    update private.device_controllers
    set pairing_code_hash = v_code_hash,
        pairing_expires_at = v_expires_at,
        status = 'pending',
        firmware_version = coalesce(nullif(btrim(p_firmware_version), ''), firmware_version),
        updated_at = now()
    where id = v_controller.id
    returning * into v_controller;
  else
    insert into private.device_controllers (
      ecosystem_id, hardware_uid, secret_hash, pairing_code_hash,
      pairing_expires_at, firmware_version
    ) values (
      1, v_hardware_uid, v_secret_hash, v_code_hash,
      v_expires_at, nullif(btrim(p_firmware_version), '')
    ) returning * into v_controller;
  end if;

  insert into private.controller_events (
    ecosystem_id, controller_id, event_type, details
  ) values (
    1, v_controller.id, 'pairing_started',
    pg_catalog.jsonb_build_object('firmware_version', v_controller.firmware_version)
  );

  return query
    select substring(v_code_plain from 1 for 4) || '-' ||
           substring(v_code_plain from 5 for 4) || '-' ||
           substring(v_code_plain from 9 for 4),
           v_expires_at,
           'pending'::text;
end;
$function$;

revoke all on function private.controller_begin_pairing_impl(text, text, text)
  from public, anon, authenticated;
grant execute on function private.controller_begin_pairing_impl(text, text, text)
  to anon, service_role;

create or replace function public.controller_begin_pairing(
  p_hardware_uid text,
  p_device_secret text,
  p_firmware_version text default null
)
returns table (
  pairing_code text,
  expires_at timestamptz,
  controller_status text
)
language sql
volatile
security invoker
set search_path = ''
as $function$
  select * from private.controller_begin_pairing_impl($1, $2, $3);
$function$;

revoke all on function public.controller_begin_pairing(text, text, text)
  from public, anon, authenticated;
grant execute on function public.controller_begin_pairing(text, text, text)
  to anon, service_role;

-- ---------------------------------------------------------------------------
-- 8. Per-boot anti-replay controller protocol with irreversible downgrade
--    protection. Rows are intentionally retained for the life of a controller.
-- ---------------------------------------------------------------------------

create table if not exists private.controller_boot_sessions (
  id bigint generated always as identity primary key,
  controller_id bigint not null
    references private.device_controllers(id) on delete restrict,
  boot_nonce text not null,
  max_seq bigint not null,
  started_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  retired_at timestamptz,
  constraint controller_boot_sessions_nonce_format
    check (boot_nonce ~ '^[0-9a-f]{32}$'),
  constraint controller_boot_sessions_max_seq
    check (max_seq >= 0),
  constraint controller_boot_sessions_controller_nonce_key
    unique (controller_id, boot_nonce)
);

alter table private.controller_boot_sessions
  add column if not exists retired_at timestamptz;

create index if not exists controller_boot_sessions_last_seen_idx
  on private.controller_boot_sessions (controller_id, last_seen_at desc);
create unique index if not exists controller_boot_sessions_one_active_idx
  on private.controller_boot_sessions (controller_id)
  where retired_at is null;

alter table private.controller_boot_sessions enable row level security;
alter table private.controller_boot_sessions force row level security;
revoke all on table private.controller_boot_sessions
  from public, anon, authenticated, service_role;
revoke all on sequence private.controller_boot_sessions_id_seq
  from public, anon, authenticated, service_role;

drop function if exists public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer
);
drop function if exists public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
);
drop function if exists private.controller_sync_impl(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer
);
drop function if exists private.controller_sync_impl(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
);

create function private.controller_sync_impl(
  p_hardware_uid text,
  p_device_secret text,
  p_heartbeat_seq bigint,
  p_firmware_version text default null,
  p_has_telemetry boolean default false,
  p_temperature double precision default null,
  p_air_humidity double precision default null,
  p_soil_humidity double precision default null,
  p_light_lux double precision default null,
  p_water_level text default null,
  p_fan_on boolean default null,
  p_pump_on boolean default null,
  p_led_on boolean default null,
  p_reported_auto_mode boolean default null,
  p_reported_fan_power integer default null,
  p_reported_led_power integer default null,
  p_boot_nonce text default null
)
returns table (
  fan_target boolean,
  led_target boolean,
  auto_mode boolean,
  pump_request bigint,
  pump_duration_ms integer,
  fan_power integer,
  led_power integer,
  secure_mode boolean,
  heartbeat_seq bigint,
  pump_authorized boolean,
  pump_expires_at_epoch bigint
)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_hardware_uid text;
  v_secret text;
  v_boot_nonce text;
  v_secret_hash bytea;
  v_controller private.device_controllers%rowtype;
  v_boot_session private.controller_boot_sessions%rowtype;
  v_strict_protocol boolean;
  v_strict_just_enabled boolean := false;
  v_firmware text;
  v_firmware_without_build text;
  v_firmware_prerelease text;
  v_firmware_build text;
  v_firmware_core text;
  v_semver_valid boolean := false;
  v_protocol_v21 boolean := false;
  v_soil_telemetry_safe boolean := false;
  v_major numeric;
  v_minor numeric;
  v_patch numeric;
  v_previous_heartbeat bigint;
  v_accepted_heartbeat bigint;
begin
  if octet_length(coalesce(p_hardware_uid, '')) > 64
     or octet_length(coalesce(p_device_secret, '')) > 128
     or octet_length(coalesce(p_firmware_version, '')) > 64
     or octet_length(coalesce(p_boot_nonce, '')) > 64
     or octet_length(coalesce(p_water_level, '')) > 32 then
    raise exception 'controller sync field exceeds maximum length'
      using errcode = '22023';
  end if;

  v_hardware_uid := upper(btrim(coalesce(p_hardware_uid, '')));
  v_secret := lower(btrim(coalesce(p_device_secret, '')));
  v_boot_nonce := lower(btrim(coalesce(p_boot_nonce, '')));
  v_firmware := btrim(coalesce(p_firmware_version, ''));

  if v_hardware_uid !~ '^[0-9A-F]{12}$'
     or v_secret !~ '^[0-9a-f]{64}$'
     or p_heartbeat_seq is null
     or p_heartbeat_seq < 0 then
    raise exception 'invalid controller credentials or heartbeat'
      using errcode = '22023';
  end if;

  if char_length(v_firmware) between 5 and 40
     and v_firmware ~
       '^(0|[1-9][0-9]*)[.](0|[1-9][0-9]*)[.](0|[1-9][0-9]*)(-[0-9A-Za-z-]+([.][0-9A-Za-z-]+)*)?([+][0-9A-Za-z-]+([.][0-9A-Za-z-]+)*)?$'
  then
    v_firmware_without_build := split_part(v_firmware, '+', 1);
    v_firmware_build := case
      when strpos(v_firmware, '+') > 0 then split_part(v_firmware, '+', 2)
      else null
    end;
    if strpos(v_firmware_without_build, '-') > 0 then
      v_firmware_prerelease := substr(
        v_firmware_without_build,
        strpos(v_firmware_without_build, '-') + 1
      );
      v_firmware_core := substr(
        v_firmware_without_build,
        1,
        strpos(v_firmware_without_build, '-') - 1
      );
    else
      v_firmware_prerelease := null;
      v_firmware_core := v_firmware_without_build;
    end if;
    v_semver_valid := not coalesce(
      v_firmware_prerelease ~ '(^|[.])0[0-9]+($|[.])', false
    );

    if v_semver_valid then
      v_major := split_part(v_firmware_core, '.', 1)::numeric;
      v_minor := split_part(v_firmware_core, '.', 2)::numeric;
      v_patch := split_part(v_firmware_core, '.', 3)::numeric;
      v_protocol_v21 := v_firmware_prerelease is null
        and (
          v_major > 2
          or (v_major = 2 and v_minor > 1)
          or (v_major = 2 and v_minor = 1 and v_patch >= 0)
        );
    end if;
  end if;

  if v_semver_valid then
    v_soil_telemetry_safe := v_firmware = '2.0.5+replaceable'
      or (
        v_protocol_v21
        and coalesce(v_firmware_build, '') ~ '^replaceable([.]|$)'
      );
  end if;

  v_secret_hash := extensions.digest(v_secret, 'sha256');

  select controller.* into v_controller
  from private.device_controllers as controller
  join private.ecosystems as ecosystem
    on ecosystem.id = controller.ecosystem_id
   and ecosystem.active_controller_id = controller.id
  where controller.hardware_uid = v_hardware_uid
    and controller.secret_hash = v_secret_hash
    and controller.status = 'active';

  if not found then
    raise exception 'controller is not active' using errcode = '42501';
  end if;

  select ecosystem.strict_controller_protocol
    into v_strict_protocol
  from private.ecosystems as ecosystem
  where ecosystem.id = v_controller.ecosystem_id
  for update;

  if not found then
    raise exception 'controller ecosystem is inconsistent'
      using errcode = '55000';
  end if;

  -- Revalidate and lock the authenticated controller only after the ecosystem
  -- singleton, preserving the global ecosystem -> controller -> control order.
  select controller.* into v_controller
  from private.device_controllers as controller
  join private.ecosystems as ecosystem
    on ecosystem.id = controller.ecosystem_id
   and ecosystem.active_controller_id = controller.id
  where controller.id = v_controller.id
    and controller.hardware_uid = v_hardware_uid
    and controller.secret_hash = v_secret_hash
    and controller.status = 'active'
  for update of controller;

  if not found then
    raise exception 'controller is not active' using errcode = '42501';
  end if;

  -- A 2.1+ controller claiming the nonce protocol must actually provide it.
  -- Once strict mode is enabled, old firmware and missing/invalid nonces are a
  -- hard authorization failure forever; this blocks protocol downgrade.
  if v_protocol_v21 and v_boot_nonce !~ '^[0-9a-f]{32}$' then
    raise exception 'firmware 2.1.0 or newer requires a 32-hex boot nonce'
      using errcode = '22023';
  end if;

  if v_strict_protocol
     and (not v_protocol_v21 or v_boot_nonce !~ '^[0-9a-f]{32}$') then
    raise exception 'legacy controller protocol is permanently disabled'
      using errcode = '42501';
  end if;

  if not v_protocol_v21 and v_boot_nonce <> '' then
    raise exception 'boot nonce requires firmware 2.1.0 or newer'
      using errcode = '22023';
  end if;

  if p_has_telemetry then
    if p_temperature is not null
       and p_temperature not between -40.0 and 85.0 then
      raise exception 'temperature is outside the accepted sensor range'
        using errcode = '22023';
    end if;
    if p_air_humidity is not null
       and p_air_humidity not between 0.0 and 100.0 then
      raise exception 'air humidity must be between 0 and 100'
        using errcode = '22023';
    end if;
    if p_soil_humidity is not null
       and p_soil_humidity not between 0.0 and 100.0 then
      raise exception 'soil humidity must be between 0 and 100'
        using errcode = '22023';
    end if;
    if p_light_lux is not null
       and p_light_lux not between 0.0 and 200000.0 then
      raise exception 'light level is outside the accepted sensor range'
        using errcode = '22023';
    end if;
    if p_water_level is not null
       and lower(btrim(p_water_level)) not in ('low', 'high') then
      raise exception 'water level must be low, high, or null'
        using errcode = '22023';
    end if;
    if p_reported_fan_power is not null
       and p_reported_fan_power not between 0 and 100 then
      raise exception 'reported fan power must be between 0 and 100'
        using errcode = '22023';
    end if;
    if p_reported_led_power is not null
       and p_reported_led_power not between 0 and 100 then
      raise exception 'reported LED power must be between 0 and 100'
        using errcode = '22023';
    end if;
  end if;

  select control.heartbeat_seq into v_previous_heartbeat
  from public.device_control as control
  where control.id = 1
    and control.active_controller_id = v_controller.id
  for update;

  if not found then
    raise exception 'controller state is inconsistent' using errcode = '55000';
  end if;

  if v_protocol_v21 then
    select boot.* into v_boot_session
    from private.controller_boot_sessions as boot
    where boot.controller_id = v_controller.id
      and boot.retired_at is null
    for update;

    if found and v_boot_session.boot_nonce = v_boot_nonce then
      update private.controller_boot_sessions as boot
      set max_seq = p_heartbeat_seq,
          last_seen_at = now()
      where boot.id = v_boot_session.id
        and p_heartbeat_seq > boot.max_seq
      returning boot.max_seq into v_accepted_heartbeat;

      if not found then
        raise exception 'replayed or out-of-order controller heartbeat'
          using errcode = '42501';
      end if;
    elsif found then
      -- A nonce seen in any earlier boot is permanently retired. A genuinely
      -- new nonce atomically retires the previous active boot session.
      if v_boot_session.started_at > now() - interval '10 seconds' then
        raise exception 'controller boot nonce changed too frequently'
          using errcode = '42900';
      end if;

      if exists (
        select 1
        from private.controller_boot_sessions as old_boot
        where old_boot.controller_id = v_controller.id
          and old_boot.boot_nonce = v_boot_nonce
      ) then
        raise exception 'retired controller boot nonce cannot be reused'
          using errcode = '42501';
      end if;

      update private.controller_boot_sessions
      set retired_at = now(),
          last_seen_at = now()
      where id = v_boot_session.id;

      insert into private.controller_boot_sessions (
        controller_id, boot_nonce, max_seq
      ) values (
        v_controller.id, v_boot_nonce, p_heartbeat_seq
      ) returning max_seq into v_accepted_heartbeat;
    else
      if exists (
        select 1
        from private.controller_boot_sessions as old_boot
        where old_boot.controller_id = v_controller.id
          and old_boot.boot_nonce = v_boot_nonce
      ) then
        raise exception 'retired controller boot nonce cannot be reused'
          using errcode = '42501';
      end if;

      insert into private.controller_boot_sessions (
        controller_id, boot_nonce, max_seq
      ) values (
        v_controller.id, v_boot_nonce, p_heartbeat_seq
      ) returning max_seq into v_accepted_heartbeat;
    end if;

    if not v_strict_protocol then
      update private.ecosystems
      set strict_controller_protocol = true,
          legacy_writes_allowed = false,
          updated_at = now()
      where id = v_controller.ecosystem_id;
      v_strict_just_enabled := true;
    end if;
  else
    -- Transitional 2.0.5 behavior. It remains available only until the first
    -- valid 2.1+ nonce sync permanently enables strict mode. Returning the
    -- accepted sequence lets 2.0.5 recover after a reboot without mutation.
    if p_heartbeat_seq <= v_previous_heartbeat then
      return query
        select control.fan_target, control.led_target, control.auto_mode,
               control.pump_request, control.pump_duration_ms,
               control.fan_power, control.led_power, true,
               control.heartbeat_seq,
               coalesce(control.pump_expires_at > now() + interval '2 seconds', false),
               extract(epoch from control.pump_expires_at)::bigint
        from public.device_control as control
        where control.id = 1;
      return;
    end if;
    v_accepted_heartbeat := p_heartbeat_seq;
  end if;

  update private.device_controllers
  set last_seen_at = now(),
      firmware_version = case
        when v_semver_valid then v_firmware
        else firmware_version
      end,
      updated_at = now()
  where id = v_controller.id;

  update public.device_control
  set heartbeat_seq = v_accepted_heartbeat,
      esp32_online = true,
      last_seen_at = now()
  where id = 1
    and active_controller_id = v_controller.id;

  if p_has_telemetry then
    insert into public.sensor_records (
      controller_id, temperature, air_humidity, soil_humidity, light_lux,
      water_level, fan_on, pump_on, led_on, auto_mode, fan_power, led_power
    ) values (
      v_controller.id,
      p_temperature,
      p_air_humidity,
      case when v_soil_telemetry_safe then p_soil_humidity else null end,
      p_light_lux,
      case when p_water_level is null then null else lower(btrim(p_water_level)) end,
      p_fan_on,
      p_pump_on,
      p_led_on,
      p_reported_auto_mode,
      p_reported_fan_power,
      p_reported_led_power
    );
  end if;

  if v_strict_just_enabled then
    insert into private.controller_events (
      ecosystem_id, controller_id, event_type, details
    ) values (
      v_controller.ecosystem_id,
      v_controller.id,
      'strict_protocol_enabled',
      pg_catalog.jsonb_build_object(
        'firmware_version', v_firmware
      )
    );
  end if;

  return query
    select control.fan_target, control.led_target, control.auto_mode,
           control.pump_request, control.pump_duration_ms,
           control.fan_power, control.led_power, true,
           control.heartbeat_seq,
           coalesce(control.pump_expires_at > now() + interval '2 seconds', false),
           extract(epoch from control.pump_expires_at)::bigint
    from public.device_control as control
    where control.id = 1;
end;
$function$;

revoke all on function private.controller_sync_impl(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) from public, anon, authenticated;
grant execute on function private.controller_sync_impl(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) to anon, service_role;

drop function if exists public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer
);

create function public.controller_sync(
  p_hardware_uid text,
  p_device_secret text,
  p_heartbeat_seq bigint,
  p_firmware_version text default null,
  p_has_telemetry boolean default false,
  p_temperature double precision default null,
  p_air_humidity double precision default null,
  p_soil_humidity double precision default null,
  p_light_lux double precision default null,
  p_water_level text default null,
  p_fan_on boolean default null,
  p_pump_on boolean default null,
  p_led_on boolean default null,
  p_reported_auto_mode boolean default null,
  p_reported_fan_power integer default null,
  p_reported_led_power integer default null,
  p_boot_nonce text default null
)
returns table (
  fan_target boolean,
  led_target boolean,
  auto_mode boolean,
  pump_request bigint,
  pump_duration_ms integer,
  fan_power integer,
  led_power integer,
  secure_mode boolean,
  heartbeat_seq bigint,
  pump_authorized boolean,
  pump_expires_at_epoch bigint
)
language sql
volatile
security invoker
set search_path = ''
as $function$
  select * from private.controller_sync_impl(
    $1, $2, $3, $4, $5, $6, $7, $8,
    $9, $10, $11, $12, $13, $14, $15, $16, $17
  );
$function$;

revoke all on function public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) from public, anon, authenticated;
grant execute on function public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) to anon, service_role;

comment on function public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) is 'Authenticates the active ESP32, enforces a permanent per-boot nonce/sequence replay barrier for firmware 2.1+, quarantines unsafe telemetry, and returns commands plus the accepted heartbeat sequence.';

-- ---------------------------------------------------------------------------
-- 9. Move existing admin definer implementations out of the exposed schema.
-- ---------------------------------------------------------------------------

create or replace function private.controller_admin_status_impl()
returns table (
  controller_id bigint,
  hardware_uid_masked text,
  controller_status text,
  firmware_version text,
  last_seen_at timestamptz,
  secure_mode boolean
)
language plpgsql
stable
security definer
set search_path = ''
as $function$
begin
  perform private.require_admin_aal2();
  return query
    select controller.id,
           case when controller.id is null
             then null else '********' || right(controller.hardware_uid, 4)
           end,
           coalesce(controller.status, 'not_paired'),
           controller.firmware_version,
           controller.last_seen_at,
           not ecosystem.legacy_writes_allowed
    from private.ecosystems as ecosystem
    left join private.device_controllers as controller
      on controller.id = ecosystem.active_controller_id
    where ecosystem.id = 1;
end;
$function$;

create or replace function private.replace_active_controller_impl(
  p_pairing_code text
)
returns table (
  controller_id bigint,
  hardware_uid_masked text,
  controller_status text,
  firmware_version text,
  secure_mode boolean
)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_user_id uuid := private.require_admin_aal2();
  v_code text := upper(regexp_replace(coalesce(p_pairing_code, ''), '[^0-9A-Fa-f]', '', 'g'));
  v_code_hash bytea;
  v_controller private.device_controllers%rowtype;
  v_previous_controller_id bigint;
  v_strict_protocol boolean;
begin
  if v_code !~ '^[0-9A-F]{12}$' then
    raise exception 'invalid pairing code' using errcode = '22023';
  end if;

  v_code_hash := extensions.digest(v_code, 'sha256');

  select ecosystem.active_controller_id,
         ecosystem.strict_controller_protocol
    into v_previous_controller_id, v_strict_protocol
  from private.ecosystems as ecosystem
  where ecosystem.id = 1
  for update;

  select * into v_controller
  from private.device_controllers as controller
  where controller.ecosystem_id = 1
    and controller.status = 'pending'
    and controller.pairing_code_hash = v_code_hash
    and controller.pairing_expires_at > now()
  for update;

  if not found then
    raise exception 'pairing code is invalid or expired'
      using errcode = '22023';
  end if;

  if v_strict_protocol
     and not private.controller_supports_strict_protocol(v_controller.firmware_version) then
    raise exception 'replacement controller firmware does not support the required strict protocol'
      using errcode = '42501';
  end if;

  update private.device_controllers
  set status = 'standby',
      pairing_code_hash = null,
      pairing_expires_at = null,
      updated_at = now()
  where ecosystem_id = 1
    and status = 'active'
    and id <> v_controller.id;

  update private.device_controllers
  set status = 'active',
      pairing_code_hash = null,
      pairing_expires_at = null,
      activated_at = now(),
      activated_by = v_user_id,
      updated_at = now()
  where id = v_controller.id
  returning * into v_controller;

  update private.ecosystems
  set active_controller_id = v_controller.id,
      legacy_writes_allowed = false,
      pairing_open_until = null,
      pairing_expected_hardware_uid = null,
      pairing_expected_claim_proof = null,
      updated_at = now()
  where id = 1;

  update public.device_control
  set active_controller_id = v_controller.id,
      esp32_online = false,
      last_seen_at = null,
      heartbeat_seq = 0,
      -- A replacement never inherits energized outputs or automatic actions
      -- from the previous physical controller. The admin must explicitly
      -- re-enable them after the new board has reported healthy telemetry.
      fan_target = false,
      fan_power = 0,
      led_target = false,
      led_power = 0,
      auto_mode = false,
      pump_expires_at = null
  where id = 1;

  insert into private.controller_events (
    ecosystem_id, controller_id, event_type, actor_user_id, details
  ) values (
    1, v_controller.id,
    case when v_previous_controller_id is null
      then 'controller_activated' else 'controller_replaced'
    end,
    v_user_id,
    pg_catalog.jsonb_build_object(
      'previous_controller_id', v_previous_controller_id
    )
  );

  return query
    select v_controller.id,
           '********' || right(v_controller.hardware_uid, 4),
           v_controller.status,
           v_controller.firmware_version,
           true;
end;
$function$;

revoke all on function private.controller_admin_status_impl()
  from public, anon, authenticated;
revoke all on function private.replace_active_controller_impl(text)
  from public, anon, authenticated;
grant execute on function private.controller_admin_status_impl()
  to authenticated;
grant execute on function private.replace_active_controller_impl(text)
  to authenticated;

create or replace function public.controller_admin_status()
returns table (
  controller_id bigint, hardware_uid_masked text, controller_status text,
  firmware_version text, last_seen_at timestamptz, secure_mode boolean
)
language sql
stable
security invoker
set search_path = ''
as $function$
  select * from private.controller_admin_status_impl();
$function$;

create or replace function public.replace_active_controller(
  p_pairing_code text
)
returns table (
  controller_id bigint, hardware_uid_masked text, controller_status text,
  firmware_version text, secure_mode boolean
)
language sql
volatile
security invoker
set search_path = ''
as $function$
  select * from private.replace_active_controller_impl($1);
$function$;

revoke all on function public.controller_admin_status()
  from public, anon, authenticated;
revoke all on function public.replace_active_controller(text)
  from public, anon, authenticated;
grant execute on function public.controller_admin_status()
  to authenticated;
grant execute on function public.replace_active_controller(text)
  to authenticated;

-- The public wrappers run as the caller. These grants expose only exact private
-- implementations; private tables still have no anon grants and FORCE RLS.
grant usage on schema private to anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 10. Safe defaults for every future migration-owned public object.
-- ---------------------------------------------------------------------------

alter default privileges for role postgres in schema public
  revoke all on tables from public, anon, authenticated, service_role;
alter default privileges for role postgres in schema public
  revoke all on sequences from public, anon, authenticated, service_role;
alter default privileges for role postgres in schema public
  revoke execute on functions from public, anon, authenticated, service_role;

alter default privileges for role postgres in schema private
  revoke all on tables from public, anon, authenticated, service_role;
alter default privileges for role postgres in schema private
  revoke all on sequences from public, anon, authenticated, service_role;
alter default privileges for role postgres in schema private
  revoke execute on functions from public, anon, authenticated, service_role;

-- ALTER DEFAULT PRIVILEGES is not retroactive. Revoke every existing callable
-- in the exposed schema and every private implementation, then build the exact
-- allow-list below. Trigger functions remain executable by their trigger only.
do $block$
declare
  target_function record;
begin
  for target_function in
    select namespace.nspname as schema_name,
           proc.proname as function_name,
           pg_catalog.pg_get_function_identity_arguments(proc.oid) as arguments
    from pg_catalog.pg_proc as proc
    join pg_catalog.pg_namespace as namespace
      on namespace.oid = proc.pronamespace
    where namespace.nspname in ('public', 'private')
      and proc.prokind = 'f'
  loop
    execute pg_catalog.format(
      'revoke all on function %I.%I(%s) from public, anon, authenticated, service_role',
      target_function.schema_name,
      target_function.function_name,
      target_function.arguments
    );
  end loop;
end;
$block$;

-- Reassert the intended public API after changing defaults.
revoke all on all tables in schema public from public, anon, authenticated;
revoke all on all sequences in schema public from public, anon, authenticated;
revoke all on all tables in schema private
  from public, anon, authenticated, service_role;
revoke all on all sequences in schema private
  from public, anon, authenticated, service_role;

grant select on table public.sensor_records to authenticated;
grant select on table public.device_control to authenticated;
grant select on table public.sensor_history_months to authenticated;
grant select on table private.user_profiles to authenticated;
grant select on table private.control_audit_log to authenticated;
grant select on table private.user_access_audit to authenticated;

grant execute on function public.complete_user_profile(
  text, text, text, text, text, text
) to authenticated;
grant execute on function public.control_command(text, integer)
  to authenticated;
grant execute on function public.admin_list_users(integer)
  to authenticated;
grant execute on function public.admin_set_user_access(uuid, text, text)
  to authenticated;
grant execute on function public.admin_control_audit(integer)
  to authenticated;
grant execute on function public.my_profile()
  to authenticated;
grant execute on function public.controller_open_pairing_window(text, text, integer)
  to authenticated;
grant execute on function public.controller_admin_status()
  to authenticated;
grant execute on function public.replace_active_controller(text)
  to authenticated;
grant execute on function public.controller_begin_pairing(text, text, text)
  to anon, service_role;
grant execute on function public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) to anon, service_role;

grant execute on function public.username_login_lookup(text)
  to service_role;
grant execute on function public.username_login_begin(text)
  to service_role;
grant execute on function public.username_login_failure(text)
  to service_role;
grant execute on function public.username_login_clear(text)
  to service_role;

grant execute on function private.current_session_is_active(boolean)
  to authenticated;
grant execute on function private.complete_user_profile_impl(
  text, text, text, text, text, text
) to authenticated;
grant execute on function private.control_command_impl(text, integer)
  to authenticated;
grant execute on function private.admin_list_users_impl(integer)
  to authenticated;
grant execute on function private.admin_set_user_access_impl(uuid, text, text)
  to authenticated;
grant execute on function private.admin_control_audit_impl(integer)
  to authenticated;
grant execute on function private.controller_open_pairing_window_impl(text, text, integer)
  to authenticated;
grant execute on function private.controller_admin_status_impl()
  to authenticated;
grant execute on function private.replace_active_controller_impl(text)
  to authenticated;
grant execute on function private.controller_begin_pairing_impl(text, text, text)
  to anon, service_role;
grant execute on function private.controller_sync_impl(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) to anon, service_role;
