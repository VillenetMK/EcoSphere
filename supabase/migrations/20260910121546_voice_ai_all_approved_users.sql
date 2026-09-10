-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.
-- Voice AI is available to every approved account, including future accounts.
-- Keep session/profile checks and per-account quotas independent of device control.
create or replace function public.my_ai_access()
returns boolean language sql stable security definer set search_path = ''
as $access$
  select private.current_session_is_active(false)
    and exists (
      select 1 from private.user_profiles as profile
      where profile.user_id = (select auth.uid())
        and profile.status = 'approved'
        and profile.role in ('operator', 'admin')
        and (profile.role <> 'admin' or private.current_session_is_active(true))
    );
$access$;
revoke all on function public.my_ai_access() from public, anon, service_role;
grant execute on function public.my_ai_access() to authenticated;

-- Retain the old private allowlist for rollback; it no longer grants or denies AI.
-- manual_watering_permissions and my_control_permissions are unchanged.
