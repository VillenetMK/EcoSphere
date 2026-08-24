create function private.profile_identity_allowed(
  p_username text,
  p_email text,
  p_method text
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    (select auth.uid()) is not null
    and lower(trim(p_email)) = lower(coalesce((select auth.jwt()->>'email'), ''))
    and lower(trim(p_method)) = lower(coalesce((select auth.jwt()->'app_metadata'->>'provider'), 'email'))
    and lower(trim(p_method)) in ('email', 'google', 'github')
    and not exists (
      select 1
      from private.reserved_usernames r
      where r.username = lower(trim(p_username))
        and (
          r.reserved_for_email is null
          or r.reserved_for_email <> lower(trim(p_email))
        )
    );
$$;

revoke all on function private.profile_identity_allowed(text, text, text)
  from public, anon;
grant execute on function private.profile_identity_allowed(text, text, text)
  to authenticated;

grant insert (
  user_id,
  username,
  full_name,
  dni,
  phone,
  email,
  registration_method
) on private.user_profiles to authenticated;

grant update (
  username,
  full_name,
  dni,
  phone,
  email,
  registration_method,
  updated_at
) on private.user_profiles to authenticated;

create policy "users_insert_own_valid_profile"
  on private.user_profiles
  for insert
  to authenticated
  with check (
    (select auth.uid()) = user_id
    and status = 'pending'
    and role = 'viewer'
    and private.profile_identity_allowed(username, email, registration_method)
  );

create policy "users_update_own_valid_identity"
  on private.user_profiles
  for update
  to authenticated
  using (
    (select auth.uid()) = user_id
    and status <> 'blocked'
  )
  with check (
    (select auth.uid()) = user_id
    and status in ('pending', 'approved')
    and role in ('viewer', 'operator', 'admin')
    and private.profile_identity_allowed(username, email, registration_method)
  );

alter function public.complete_user_profile(text, text, text, text, text)
  security invoker;

create policy "deny_direct_reserved_usernames"
  on private.reserved_usernames
  for all
  to authenticated
  using (false)
  with check (false);

create policy "deny_direct_username_login_limits"
  on private.username_login_limits
  for all
  to authenticated
  using (false)
  with check (false);
