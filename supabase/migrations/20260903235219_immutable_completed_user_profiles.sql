-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

begin;

-- Profile completion is a one-way transition. A verified account without a
-- profile may still recover its registration, and a pending viewer may finish
-- once. Once approved (or elevated to operator/admin), identity is immutable.
-- Replaying the exact completed payload is intentionally idempotent.
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
  v_existing private.user_profiles%rowtype;
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

  select reserved.reserved_for_email
    into v_reserved_email
  from private.reserved_usernames as reserved
  where reserved.username = lower(v_username);

  if found
     and (v_reserved_email is null or v_reserved_email <> v_auth_email) then
    raise exception 'profile details could not be saved' using errcode = '23505';
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

  -- Serialize completion attempts for this account, including the no-row case.
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('complete-user-profile:' || v_user_id::text, 0)
  );

  select profile.*
    into v_existing
  from private.user_profiles as profile
  where profile.user_id = v_user_id
  for update;

  if found then
    if v_existing.status = 'blocked' then
      raise exception 'profile cannot be completed' using errcode = '42501';
    end if;

    if v_existing.status = 'pending' and v_existing.role = 'viewer' then
      update private.user_profiles as profile
      set username = v_username,
          first_name = v_first_name,
          last_name = v_last_name,
          full_name = v_first_name || ' ' || v_last_name,
          dni = v_dni,
          phone = v_phone,
          email = v_auth_email,
          registration_method = v_method,
          status = 'approved',
          updated_at = now()
      where profile.user_id = v_user_id
        and profile.status = 'pending'
        and profile.role = 'viewer';

      if not found then
        raise exception 'profile cannot be completed' using errcode = '42501';
      end if;

      return true;
    end if;

    if v_existing.username = v_username
       and v_existing.first_name = v_first_name
       and v_existing.last_name = v_last_name
       and v_existing.full_name = v_first_name || ' ' || v_last_name
       and v_existing.dni = v_dni
       and v_existing.phone = v_phone
       and v_existing.email = v_auth_email
       and v_existing.registration_method = v_method then
      return true;
    end if;

    raise exception 'completed profile identity is immutable'
      using errcode = '42501';
  end if;

  insert into private.user_profiles (
    user_id, username, first_name, last_name, full_name, dni, phone,
    email, registration_method, status, role
  )
  values (
    v_user_id, v_username, v_first_name, v_last_name,
    v_first_name || ' ' || v_last_name,
    v_dni, v_phone, v_auth_email, v_method, 'approved', 'viewer'
  );

  return true;
exception
  when unique_violation then
    -- Do not reveal whether username, DNI, phone, email, or user id collided.
    raise exception 'profile details could not be saved' using errcode = '22023';
end;
$function$;

revoke all on function private.complete_user_profile_impl(
  text, text, text, text, text, text
) from public, anon, authenticated, service_role;

-- The public SECURITY DEFINER gateway remains the sole callable entry point.
revoke all on function public.complete_user_profile(
  text, text, text, text, text, text
) from public, anon;
grant execute on function public.complete_user_profile(
  text, text, text, text, text, text
) to authenticated;

commit;

