begin;

-- Completing a profile is only possible for an authenticated identity whose
-- verified JWT email matches the submitted registration email. New accounts
-- therefore receive read-only viewer access immediately, while elevated roles
-- and blocked accounts remain protected from automatic changes.
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
    'viewer'
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

  return true;
end;
$function$;

-- Release verified read-only accounts that were waiting before automation.
update private.user_profiles as profile
set status = 'approved',
    updated_at = now()
from auth.users as auth_user
where auth_user.id = profile.user_id
  and auth_user.email_confirmed_at is not null
  and profile.status = 'pending'
  and profile.role = 'viewer';

commit;
