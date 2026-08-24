alter table private.user_profiles
  add column username text;

alter table private.user_profiles
  add constraint user_profiles_username check (
    username ~ '^[A-Za-z][A-Za-z0-9._-]{2,31}$'
  );

create unique index user_profiles_username_unique
  on private.user_profiles (lower(username));

alter table private.user_profiles
  alter column username set not null;

create table private.reserved_usernames (
  username text primary key,
  reserved_for_email text,
  created_at timestamptz not null default now(),
  constraint reserved_usernames_lowercase check (username = lower(username)),
  constraint reserved_usernames_format check (
    username ~ '^[a-z][a-z0-9._-]{2,31}$'
  ),
  constraint reserved_usernames_email check (
    reserved_for_email is null
    or (
      reserved_for_email = lower(reserved_for_email)
      and char_length(reserved_for_email) <= 254
    )
  )
);

alter table private.reserved_usernames enable row level security;
alter table private.reserved_usernames force row level security;
revoke all on table private.reserved_usernames from public, anon, authenticated;

insert into private.reserved_usernames (username, reserved_for_email)
values ('villenetadmin', null);

revoke all on function public.complete_user_profile(text, text, text, text)
  from public, anon, authenticated;
drop function public.complete_user_profile(text, text, text, text);

create function public.complete_user_profile(
  p_username text,
  p_full_name text,
  p_dni text,
  p_phone text,
  p_expected_email text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := (select auth.uid());
  v_email text := lower(coalesce((select auth.jwt()->>'email'), ''));
  v_method text := lower(coalesce((select auth.jwt()->'app_metadata'->>'provider'), 'email'));
  v_username text := trim(p_username);
  v_reserved_email text;
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

  select r.reserved_for_email
    into v_reserved_email
  from private.reserved_usernames r
  where r.username = lower(v_username);

  if found and (v_reserved_email is null or v_reserved_email <> v_email) then
    raise exception 'username is reserved' using errcode = '23505';
  end if;

  insert into private.user_profiles (
    user_id,
    username,
    full_name,
    dni,
    phone,
    email,
    registration_method
  )
  values (
    v_user_id,
    v_username,
    regexp_replace(trim(p_full_name), '[[:space:]]+', ' ', 'g'),
    regexp_replace(p_dni, '[^0-9]', '', 'g'),
    p_phone,
    v_email,
    v_method
  )
  on conflict (user_id) do update
    set username = excluded.username,
        full_name = excluded.full_name,
        dni = excluded.dni,
        phone = excluded.phone,
        email = excluded.email,
        registration_method = excluded.registration_method,
        updated_at = now()
    where private.user_profiles.status <> 'blocked';

  return true;
end;
$$;

revoke all on function public.complete_user_profile(text, text, text, text, text)
  from public, anon;
grant execute on function public.complete_user_profile(text, text, text, text, text)
  to authenticated;

revoke all on function public.my_profile()
  from public, anon, authenticated;
drop function public.my_profile();

create function public.my_profile()
returns table (
  username text,
  full_name text,
  email text,
  registration_method text,
  status text,
  role text
)
language sql
stable
security invoker
set search_path = ''
as $$
  select
    p.username,
    p.full_name,
    p.email,
    p.registration_method,
    p.status,
    p.role
  from private.user_profiles p
  where p.user_id = (select auth.uid())
  limit 1;
$$;

revoke all on function public.my_profile() from public, anon;
grant execute on function public.my_profile() to authenticated;

create table private.username_login_limits (
  attempt_key text primary key,
  failures integer not null default 0,
  window_started_at timestamptz not null default now(),
  blocked_until timestamptz,
  updated_at timestamptz not null default now(),
  constraint username_login_limits_failures check (failures between 0 and 1000)
);

alter table private.username_login_limits enable row level security;
alter table private.username_login_limits force row level security;
revoke all on table private.username_login_limits from public, anon, authenticated;

create function public.username_login_lookup(p_username text)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select p.email
  from private.user_profiles p
  where lower(p.username) = lower(trim(p_username))
  limit 1;
$$;

create function public.username_login_begin(p_attempt_key text)
returns table (allowed boolean, retry_after_seconds integer)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_row private.username_login_limits%rowtype;
begin
  if p_attempt_key !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid attempt key' using errcode = '22023';
  end if;

  select *
    into v_row
  from private.username_login_limits
  where attempt_key = p_attempt_key
  for update;

  if not found then
    return query select true, 0;
    return;
  end if;

  if v_row.blocked_until is not null and v_row.blocked_until > now() then
    return query select false, greatest(1, ceil(extract(epoch from (v_row.blocked_until - now())))::integer);
    return;
  end if;

  if v_row.window_started_at < now() - interval '15 minutes' then
    update private.username_login_limits
      set failures = 0,
          window_started_at = now(),
          blocked_until = null,
          updated_at = now()
    where attempt_key = p_attempt_key;
  end if;

  return query select true, 0;
end;
$$;

create function public.username_login_failure(p_attempt_key text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_attempt_key !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid attempt key' using errcode = '22023';
  end if;

  insert into private.username_login_limits (
    attempt_key,
    failures,
    window_started_at,
    blocked_until,
    updated_at
  )
  values (p_attempt_key, 1, now(), null, now())
  on conflict (attempt_key) do update
    set failures = case
          when private.username_login_limits.window_started_at < now() - interval '15 minutes'
            then 1
          else private.username_login_limits.failures + 1
        end,
        window_started_at = case
          when private.username_login_limits.window_started_at < now() - interval '15 minutes'
            then now()
          else private.username_login_limits.window_started_at
        end,
        blocked_until = case
          when (
            case
              when private.username_login_limits.window_started_at < now() - interval '15 minutes'
                then 1
              else private.username_login_limits.failures + 1
            end
          ) >= 5
            then now() + interval '15 minutes'
          else null
        end,
        updated_at = now();
end;
$$;

create function public.username_login_clear(p_attempt_key text)
returns void
language sql
security definer
set search_path = ''
as $$
  delete from private.username_login_limits
  where attempt_key = p_attempt_key;
$$;

revoke all on function public.username_login_lookup(text) from public, anon, authenticated;
revoke all on function public.username_login_begin(text) from public, anon, authenticated;
revoke all on function public.username_login_failure(text) from public, anon, authenticated;
revoke all on function public.username_login_clear(text) from public, anon, authenticated;
grant execute on function public.username_login_lookup(text) to service_role;
grant execute on function public.username_login_begin(text) to service_role;
grant execute on function public.username_login_failure(text) to service_role;
grant execute on function public.username_login_clear(text) to service_role;

create policy "admin_mfa_required_sensor_records"
  on public.sensor_records
  as restrictive
  for select
  to authenticated
  using (
    not exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role = 'admin'
    )
    or (select auth.jwt()->>'aal') = 'aal2'
  );

create policy "admin_mfa_required_device_control_read"
  on public.device_control
  as restrictive
  for select
  to authenticated
  using (
    not exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role = 'admin'
    )
    or (select auth.jwt()->>'aal') = 'aal2'
  );

create policy "admin_mfa_required_device_control_update"
  on public.device_control
  as restrictive
  for update
  to authenticated
  using (
    not exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role = 'admin'
    )
    or (select auth.jwt()->>'aal') = 'aal2'
  )
  with check (
    not exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role = 'admin'
    )
    or (select auth.jwt()->>'aal') = 'aal2'
  );
