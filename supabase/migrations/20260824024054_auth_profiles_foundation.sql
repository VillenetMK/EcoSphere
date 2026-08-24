create schema if not exists private;

revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create table if not exists private.user_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null,
  dni text not null,
  phone text not null,
  email text not null,
  registration_method text not null,
  status text not null default 'pending',
  role text not null default 'viewer',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint user_profiles_full_name check (
    char_length(full_name) between 5 and 160
    and full_name ~ '^[[:alpha:]][[:alpha:] .''-]+[[:space:]][[:alpha:] .''-]+$'
  ),
  constraint user_profiles_dni check (dni ~ '^[0-9]{8}$'),
  constraint user_profiles_phone check (phone ~ '^\+[1-9][0-9]{7,14}$'),
  constraint user_profiles_email check (char_length(email) <= 254 and email = lower(email)),
  constraint user_profiles_method check (registration_method in ('email', 'google', 'github')),
  constraint user_profiles_status check (status in ('pending', 'approved', 'blocked')),
  constraint user_profiles_role check (role in ('viewer', 'operator', 'admin'))
);

create unique index if not exists user_profiles_dni_unique
  on private.user_profiles (dni);
create unique index if not exists user_profiles_phone_unique
  on private.user_profiles (phone);
create unique index if not exists user_profiles_email_unique
  on private.user_profiles (lower(email));

alter table private.user_profiles enable row level security;
alter table private.user_profiles force row level security;

revoke all privileges on table private.user_profiles from public, anon, authenticated;
grant select on table private.user_profiles to authenticated;
grant insert (user_id, full_name, dni, phone, email, registration_method)
  on private.user_profiles to authenticated;
grant update (full_name, dni, phone, email, registration_method, updated_at)
  on private.user_profiles to authenticated;

create policy "users_read_own_private_profile"
  on private.user_profiles
  for select
  to authenticated
  using ((select auth.uid()) = user_id);

create policy "users_insert_own_private_profile"
  on private.user_profiles
  for insert
  to authenticated
  with check (
    (select auth.uid()) = user_id
    and status = 'pending'
    and role = 'viewer'
  );

create policy "users_update_own_private_identity"
  on private.user_profiles
  for update
  to authenticated
  using ((select auth.uid()) = user_id and status <> 'blocked')
  with check (
    (select auth.uid()) = user_id
    and status in ('pending', 'approved')
    and role in ('viewer', 'operator', 'admin')
  );

create or replace function public.complete_user_profile(
  p_full_name text,
  p_dni text,
  p_phone text,
  p_expected_email text
)
returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_user_id uuid := (select auth.uid());
  v_email text := lower(coalesce((select auth.jwt()->>'email'), ''));
  v_method text := lower(coalesce((select auth.jwt()->'app_metadata'->>'provider'), 'email'));
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

  insert into private.user_profiles (
    user_id,
    full_name,
    dni,
    phone,
    email,
    registration_method
  )
  values (
    v_user_id,
    regexp_replace(trim(p_full_name), '[[:space:]]+', ' ', 'g'),
    regexp_replace(p_dni, '[^0-9]', '', 'g'),
    p_phone,
    v_email,
    v_method
  )
  on conflict (user_id) do update
    set full_name = excluded.full_name,
        dni = excluded.dni,
        phone = excluded.phone,
        email = excluded.email,
        registration_method = excluded.registration_method,
        updated_at = now()
    where private.user_profiles.status <> 'blocked';

  return true;
end;
$$;

create or replace function public.my_profile()
returns table (
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
    p.full_name,
    p.email,
    p.registration_method,
    p.status,
    p.role
  from private.user_profiles p
  where p.user_id = (select auth.uid())
  limit 1;
$$;

revoke execute on function public.complete_user_profile(text, text, text, text)
  from public, anon;
revoke execute on function public.my_profile()
  from public, anon;
grant execute on function public.complete_user_profile(text, text, text, text)
  to authenticated;
grant execute on function public.my_profile()
  to authenticated;

alter default privileges for role postgres in schema private
  revoke all privileges on tables from public, anon, authenticated;
alter default privileges for role postgres in schema private
  revoke execute on functions from public, anon, authenticated;
