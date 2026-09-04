begin;

-- EcoSphere migrations create application objects as postgres. Default-deny
-- that owner so a future migration cannot accidentally publish a new table,
-- sequence or RPC before its explicit grants are reviewed. Supabase reserves
-- the platform-owned supabase_admin defaults; the project role is not allowed
-- to change them, so CI separately enforces an exact post-migration allowlist.
alter default privileges for role postgres in schema public
  revoke all on tables from public, anon, authenticated, service_role;
alter default privileges for role postgres in schema public
  revoke all on sequences from public, anon, authenticated, service_role;
alter default privileges for role postgres in schema public
  revoke execute on functions from public, anon, authenticated, service_role;

alter default privileges for role postgres in schema private
  revoke all on tables from public, anon, authenticated, service_role;
alter default privileges for role postgres in schema private
  revoke all on sequences from public, anon, authenticated, service_role;
alter default privileges for role postgres in schema private
  revoke execute on functions from public, anon, authenticated, service_role;

commit;

