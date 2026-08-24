alter table private.user_profiles
  alter column dni drop not null,
  alter column phone drop not null,
  drop constraint user_profiles_dni,
  drop constraint user_profiles_phone,
  add constraint user_profiles_dni check (
    (role = 'admin' and dni is null)
    or (dni is not null and dni ~ '^[0-9]{8}$')
  ),
  add constraint user_profiles_phone check (
    (role = 'admin' and phone is null)
    or (phone is not null and phone ~ '^\+[1-9][0-9]{7,14}$')
  );

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
select
  u.id,
  'VillenetADMIN',
  'Administrador',
  'EcoSphere',
  'Administrador EcoSphere',
  null,
  null,
  lower(u.email),
  case lower(coalesce(u.raw_app_meta_data->>'provider', ''))
    when 'google' then 'google'
    when 'github' then 'github'
    else 'email'
  end,
  'approved',
  'admin'
from auth.users u
join private.reserved_usernames r
  on r.username = 'villenetadmin'
 and r.reserved_for_email = lower(u.email)
on conflict (user_id) do update
set username = excluded.username,
    email = excluded.email,
    registration_method = excluded.registration_method,
    status = 'approved',
    role = 'admin',
    updated_at = now();
