-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

alter function public.complete_user_profile(text, text, text, text, text, text) security definer;
alter function public.admin_list_users(integer) security definer;
alter function public.admin_set_user_access(uuid, text, text) security definer;
alter function public.admin_control_audit(integer) security definer;
alter function public.control_command(text, integer) security definer;
alter function public.controller_open_pairing_window(text, text, integer) security definer;
alter function public.controller_begin_pairing(text, text, text) security definer;
alter function public.controller_sync(
  text, text, bigint, text, boolean,
  double precision, double precision, double precision, double precision,
  text, boolean, boolean, boolean, boolean, integer, integer, text
) security definer;
alter function public.controller_admin_status() security definer;
alter function public.replace_active_controller(text) security definer;

revoke execute on all functions in schema private
  from public, anon, authenticated, service_role;
revoke usage on schema private from public, anon, service_role;
grant usage on schema private to authenticated;
grant execute on function private.current_session_is_active(boolean)
  to authenticated;
