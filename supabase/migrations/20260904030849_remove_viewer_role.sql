-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

begin;

-- Existing read-only accounts become operators without changing their approval
-- or blocked status. Administrator accounts remain untouched.
update private.user_profiles
set role = 'operator',
    updated_at = now()
where role = 'viewer';

alter table private.user_profiles
  alter column role set default 'operator',
  drop constraint user_profiles_role,
  add constraint user_profiles_role
    check (role in ('operator', 'admin'));

-- Older app versions may still submit the former viewer role while completing
-- an email profile. Normalize only a user's own profile creation/update; every
-- other attempt to restore read-only access is rejected.
create or replace function private.enforce_operator_profile_role()
returns trigger
language plpgsql
set search_path = ''
as $function$
begin
  if new.role = 'viewer' then
    if new.user_id = (select auth.uid()) then
      new.role := 'operator';
    else
      raise exception 'viewer role has been removed'
        using errcode = '22023';
    end if;
  end if;

  return new;
end;
$function$;

revoke all on function private.enforce_operator_profile_role()
  from public, anon, authenticated, service_role;

drop trigger if exists enforce_operator_profile_role
  on private.user_profiles;

create trigger enforce_operator_profile_role
before insert or update of role
on private.user_profiles
for each row
execute function private.enforce_operator_profile_role();

-- The administrative API can approve or block users, but the only assignable
-- non-administrator role is now operator. The private implementation retains
-- its MFA, self-change, administrator, and audit protections.
create or replace function public.admin_set_user_access(
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
  v_role text := lower(btrim(coalesce(p_role, '')));
begin
  perform private.require_admin_aal2();

  if v_role <> 'operator' then
    raise exception 'allowed role: operator; allowed status: approved/blocked'
      using errcode = '22023';
  end if;

  return query
    select *
    from private.admin_set_user_access_impl(
      p_user_id,
      'operator',
      p_status
    );
end;
$function$;

revoke all on function public.admin_set_user_access(uuid, text, text)
  from public, anon, service_role;
grant execute on function public.admin_set_user_access(uuid, text, text)
  to authenticated;

commit;
