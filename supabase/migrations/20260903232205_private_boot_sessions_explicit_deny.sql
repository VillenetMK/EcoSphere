begin;

-- This table is intentionally unreachable from PostgREST and human clients.
-- Keep both layers: no SQL privileges plus an explicit deny-all RLS policy.
alter table private.controller_boot_sessions enable row level security;
alter table private.controller_boot_sessions force row level security;

drop policy if exists "deny_all_controller_boot_sessions"
  on private.controller_boot_sessions;
create policy "deny_all_controller_boot_sessions"
  on private.controller_boot_sessions
  as restrictive
  for all
  to public
  using (false)
  with check (false);

revoke all on table private.controller_boot_sessions
  from public, anon, authenticated, service_role;

commit;

