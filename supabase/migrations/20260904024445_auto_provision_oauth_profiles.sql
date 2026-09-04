-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.


-- Google and GitHub do not provide a DNI or a phone number. Keep those fields
-- mandatory for email registrations, while allowing verified OAuth identities
-- to be provisioned without inventing personal data.
alter table private.user_profiles
  drop constraint user_profiles_dni,
  drop constraint user_profiles_phone;

alter table private.user_profiles
  add constraint user_profiles_dni check (
    (
      dni is null
      and (
        role = 'admin'
        or registration_method in ('google', 'github')
      )
    )
    or (dni is not null and dni ~ '^[0-9]{8}$')
  ),
  add constraint user_profiles_phone check (
    (
      phone is null
      and (
        role = 'admin'
        or registration_method in ('google', 'github')
      )
    )
    or (phone is not null and phone ~ '^\+[1-9][0-9]{7,14}$')
  );

create function private.provision_oauth_profile(p_user_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_email text;
  v_email_confirmed_at timestamptz;
  v_app_metadata jsonb;
  v_user_metadata jsonb;
  v_provider text;
  v_providers jsonb;
  v_full_name_source text;
  v_first_name text;
  v_last_name text;
  v_username_base text;
  v_username text;
  v_uuid_hex text;
begin
  select
    lower(trim(coalesce(auth_user.email, ''))),
    auth_user.email_confirmed_at,
    coalesce(auth_user.raw_app_meta_data, '{}'::jsonb),
    coalesce(auth_user.raw_user_meta_data, '{}'::jsonb)
  into
    v_email,
    v_email_confirmed_at,
    v_app_metadata,
    v_user_metadata
  from auth.users as auth_user
  where auth_user.id = p_user_id;

  if not found or v_email_confirmed_at is null or v_email = ''
     or char_length(v_email) > 254 then
    return false;
  end if;

  v_provider := lower(coalesce(v_app_metadata->>'provider', ''));
  v_providers := coalesce(v_app_metadata->'providers', '[]'::jsonb);

  -- app_metadata is controlled by Supabase Auth. user_metadata is deliberately
  -- used only for display names and never for status, role, or authorization.
  if v_provider not in ('google', 'github') then
    if v_providers ? 'google' then
      v_provider := 'google';
    elsif v_providers ? 'github' then
      v_provider := 'github';
    else
      return false;
    end if;
  end if;

  if exists (
    select 1
    from private.user_profiles as profile
    where profile.user_id = p_user_id
  ) then
    return false;
  end if;

  v_full_name_source := regexp_replace(
    trim(coalesce(
      nullif(v_user_metadata->>'full_name', ''),
      nullif(v_user_metadata->>'name', ''),
      split_part(v_email, '@', 1)
    )),
    '[[:space:]]+',
    ' ',
    'g'
  );

  v_first_name := coalesce(
    nullif(trim(v_user_metadata->>'given_name'), ''),
    nullif(trim(v_user_metadata->>'first_name'), ''),
    split_part(v_full_name_source, ' ', 1)
  );
  v_last_name := coalesce(
    nullif(trim(v_user_metadata->>'family_name'), ''),
    nullif(trim(v_user_metadata->>'last_name'), ''),
    nullif(regexp_replace(v_full_name_source, '^[^[:space:]]+[[:space:]]*', ''), '')
  );

  v_first_name := regexp_replace(coalesce(v_first_name, ''), '[^[:alpha:] .''’-]', '', 'g');
  v_first_name := regexp_replace(trim(v_first_name), '[[:space:]]+', ' ', 'g');
  v_first_name := regexp_replace(v_first_name, '^[^[:alpha:]]+|[^[:alpha:]]+$', '', 'g');
  v_first_name := left(v_first_name, 80);
  v_first_name := regexp_replace(v_first_name, '[^[:alpha:]]+$', '');

  v_last_name := regexp_replace(coalesce(v_last_name, ''), '[^[:alpha:] .''’-]', '', 'g');
  v_last_name := regexp_replace(trim(v_last_name), '[[:space:]]+', ' ', 'g');
  v_last_name := regexp_replace(v_last_name, '^[^[:alpha:]]+|[^[:alpha:]]+$', '', 'g');
  v_last_name := left(v_last_name, 80);
  v_last_name := regexp_replace(v_last_name, '[^[:alpha:]]+$', '');

  if char_length(v_first_name) not between 2 and 80
     or v_first_name !~ '^[[:alpha:]][[:alpha:] .''’-]*[[:alpha:]]$' then
    v_first_name := 'Usuario';
  end if;

  if char_length(v_last_name) not between 2 and 80
     or v_last_name !~ '^[[:alpha:]][[:alpha:] .''’-]*[[:alpha:]]$' then
    v_last_name := case v_provider when 'google' then 'Google' else 'GitHub' end;
  end if;

  v_uuid_hex := replace(p_user_id::text, '-', '');
  v_username_base := regexp_replace(
    lower(split_part(v_email, '@', 1)),
    '[^a-z0-9._-]',
    '',
    'g'
  );
  if v_username_base !~ '^[a-z]' then
    v_username_base := 'u' || v_username_base;
  end if;
  if char_length(v_username_base) < 2 then
    v_username_base := 'usuario';
  end if;
  v_username := left(v_username_base, 19) || '_' || left(v_uuid_hex, 12);

  if exists (
    select 1
    from private.user_profiles as profile
    where lower(profile.username) = lower(v_username)
  ) or exists (
    select 1
    from private.reserved_usernames as reserved
    where reserved.username = lower(v_username)
  ) then
    v_username := 'u' || substring(v_uuid_hex from 2 for 31);
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
    p_user_id,
    v_username,
    v_first_name,
    v_last_name,
    v_first_name || ' ' || v_last_name,
    null,
    null,
    v_email,
    v_provider,
    'approved',
    'operator'
  )
  on conflict (user_id) do nothing;

  return found;
end;
$function$;

revoke all on function private.provision_oauth_profile(uuid)
  from public, anon, authenticated, service_role;

create function private.provision_oauth_profile_from_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
  perform private.provision_oauth_profile(new.id);
  return new;
end;
$function$;

revoke all on function private.provision_oauth_profile_from_auth_user()
  from public, anon, authenticated, service_role;

create trigger provision_ecosphere_oauth_profile
after insert or update of email_confirmed_at, raw_app_meta_data
on auth.users
for each row
execute function private.provision_oauth_profile_from_auth_user();

-- Repair any previously created verified OAuth identity that still lacks its
-- private EcoSphere profile. Existing profiles are never modified.
select private.provision_oauth_profile(auth_user.id)
from auth.users as auth_user
left join private.user_profiles as profile on profile.user_id = auth_user.id
where profile.user_id is null
  and auth_user.email_confirmed_at is not null
  and (
    lower(coalesce(auth_user.raw_app_meta_data->>'provider', '')) in ('google', 'github')
    or coalesce(auth_user.raw_app_meta_data->'providers', '[]'::jsonb) ?| array['google', 'github']
  );
