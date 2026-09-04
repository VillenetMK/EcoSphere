/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

-- The version 4 Edge Function uses only the bounded v2 gateway. Remove the
-- superseded SECURITY DEFINER surface and its attacker-controlled legacy rows.
revoke all on function public.username_login_lookup(text)
  from public, anon, authenticated, service_role;
revoke all on function public.username_login_begin(text)
  from public, anon, authenticated, service_role;
revoke all on function public.username_login_failure(text)
  from public, anon, authenticated, service_role;
revoke all on function public.username_login_clear(text)
  from public, anon, authenticated, service_role;

drop function if exists public.username_login_lookup(text);
drop function if exists public.username_login_begin(text);
drop function if exists public.username_login_failure(text);
drop function if exists public.username_login_clear(text);

revoke all on table private.username_login_limits
  from public, anon, authenticated, service_role;
drop table if exists private.username_login_limits;

