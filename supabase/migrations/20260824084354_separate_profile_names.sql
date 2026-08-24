alter table private.user_profiles
  add column first_name text,
  add column last_name text;

update private.user_profiles
set first_name = regexp_replace(
      regexp_replace(trim(full_name), '[[:space:]]+[^[:space:]]+$', ''),
      '[[:space:]]+',
      ' ',
      'g'
    ),
    last_name = regexp_replace(
      regexp_replace(trim(full_name), '^.*[[:space:]]+', ''),
      '[[:space:]]+',
      ' ',
      'g'
    );

alter table private.user_profiles
  alter column first_name set not null,
  alter column last_name set not null,
  add constraint user_profiles_first_name check (
    char_length(first_name) between 2 and 80
    and first_name ~ '^[[:alpha:]][[:alpha:] .''’-]*[[:alpha:]]$'
  ),
  add constraint user_profiles_last_name check (
    char_length(last_name) between 2 and 80
    and last_name ~ '^[[:alpha:]][[:alpha:] .''’-]*[[:alpha:]]$'
  ),
  add constraint user_profiles_name_consistency check (
    full_name = first_name || ' ' || last_name
  );

alter table private.user_profiles
  drop constraint user_profiles_full_name,
  add constraint user_profiles_full_name check (
    char_length(full_name) between 5 and 161
    and full_name ~ '^[[:alpha:]][[:alpha:] .''’-]+[[:space:]][[:alpha:] .''’-]+$'
  );

grant insert (
  user_id,
  username,
  first_name,
  last_name,
  full_name,
  dni,
  phone,
  email,
  registration_method
) on private.user_profiles to authenticated;

grant update (
  username,
  first_name,
  last_name,
  full_name,
  dni,
  phone,
  email,
  registration_method,
  updated_at
) on private.user_profiles to authenticated;

revoke all on function public.complete_user_profile(text, text, text, text, text)
  from public, anon, authenticated;
drop function public.complete_user_profile(text, text, text, text, text);

create function public.complete_user_profile(
  p_username text,
  p_first_name text,
  p_last_name text,
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
    registration_method
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
    v_method
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
        updated_at = now()
    where private.user_profiles.status <> 'blocked';

  return true;
end;
$$;

revoke all on function public.complete_user_profile(text, text, text, text, text, text)
  from public, anon;
grant execute on function public.complete_user_profile(text, text, text, text, text, text)
  to authenticated;

revoke all on function public.my_profile()
  from public, anon, authenticated;
drop function public.my_profile();

create function public.my_profile()
returns table (
  username text,
  first_name text,
  last_name text,
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
    p.first_name,
    p.last_name,
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
