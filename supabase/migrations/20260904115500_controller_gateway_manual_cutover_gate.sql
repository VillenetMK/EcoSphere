-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

begin;

-- The previously deployed gateway guard followed strict_controller_protocol.
-- That bit is enabled by the first valid nonce-based heartbeat, so coupling the
-- two states could revoke legacy transport implicitly while firmware is being
-- upgraded. Keep protocol security irreversible, but make transport cutover a
-- separate, explicit, and likewise irreversible production decision.
alter table private.ecosystems
  add column if not exists controller_edge_gateway_required boolean
    not null default false;

alter table private.ecosystems
  alter column controller_edge_gateway_required set default false,
  alter column controller_edge_gateway_required set not null;

create or replace function private.prevent_controller_edge_gateway_downgrade()
returns trigger
language plpgsql
set search_path = ''
as $function$
begin
  if old.controller_edge_gateway_required
     and not new.controller_edge_gateway_required then
    raise exception 'controller Edge gateway requirement cannot be disabled'
      using errcode = '42501';
  end if;
  return new;
end;
$function$;

revoke all on function private.prevent_controller_edge_gateway_downgrade()
  from public, anon, authenticated, service_role;

drop trigger if exists prevent_controller_edge_gateway_downgrade
  on private.ecosystems;
create trigger prevent_controller_edge_gateway_downgrade
before update of controller_edge_gateway_required on private.ecosystems
for each row execute function private.prevent_controller_edge_gateway_downgrade();

create or replace function private.controller_gateway_access_allowed()
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
  select coalesce((select auth.role()) = 'service_role', false)
    or coalesce((
      select not ecosystem.controller_edge_gateway_required
      from private.ecosystems as ecosystem
      where ecosystem.id = 1
    ), false);
$function$;

revoke all on function private.controller_gateway_access_allowed()
  from public, anon, authenticated, service_role;

comment on column private.ecosystems.controller_edge_gateway_required is
  'Irreversible explicit transport cutover; false does not weaken nonce-based strict protocol validation.';
comment on function private.controller_gateway_access_allowed() is
  'Allows direct controller RPCs only before reviewed manual Edge cutover; service-role gateway calls always remain available.';

commit;
