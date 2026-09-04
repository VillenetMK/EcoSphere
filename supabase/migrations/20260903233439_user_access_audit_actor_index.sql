-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

create index if not exists user_access_audit_actor_user_id_idx
  on private.user_access_audit (actor_user_id)
  where actor_user_id is not null;
