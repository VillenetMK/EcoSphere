create or replace function public.complete_user_profile(
  p_username text,
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
  v_username text := trim(p_username);
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
