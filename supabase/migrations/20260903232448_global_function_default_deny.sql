begin;

-- PostgreSQL's built-in default grants EXECUTE on every new function to
-- PUBLIC. A schema-local REVOKE cannot subtract that global default, so close
-- it at owner scope. Future application RPCs must be granted explicitly.
alter default privileges for role postgres
  revoke execute on functions from public;

commit;

