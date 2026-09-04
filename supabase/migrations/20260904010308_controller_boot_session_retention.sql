-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

-- Keep the replay journal bounded per controller. A captured controller
-- request already contains the device credential and is protected in transit
-- by TLS; retaining 90 days plus 4095 retired boots therefore preserves a
-- substantial replay window without allowing unbounded database growth.

begin;

create index if not exists controller_boot_sessions_retired_retention_idx
  on private.controller_boot_sessions (
    controller_id,
    last_seen_at desc,
    id desc
  )
  where retired_at is not null;

create function private.prune_controller_boot_sessions_after_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
  -- Remove expired retired sessions only for the controller that just opened
  -- a new boot. The active row is never eligible for either deletion.
  delete from private.controller_boot_sessions as boot
  where boot.controller_id = new.controller_id
    and boot.retired_at is not null
    and boot.last_seen_at < now() - interval '90 days';

  -- Retain at most 4095 retired rows plus the one active row. The ordering and
  -- id tie-breaker make eviction deterministic under equal timestamps.
  delete from private.controller_boot_sessions as boot
  where boot.id in (
    select retired.id
    from private.controller_boot_sessions as retired
    where retired.controller_id = new.controller_id
      and retired.retired_at is not null
    order by retired.last_seen_at desc, retired.id desc
    offset 4095
  );

  return new;
end;
$function$;

revoke all on function private.prune_controller_boot_sessions_after_insert()
  from public, anon, authenticated, service_role;

drop trigger if exists controller_boot_sessions_retention
  on private.controller_boot_sessions;
create trigger controller_boot_sessions_retention
after insert on private.controller_boot_sessions
for each row execute function private.prune_controller_boot_sessions_after_insert();

-- One-time cleanup for installations that already accumulated retired rows.
delete from private.controller_boot_sessions as boot
where boot.retired_at is not null
  and boot.last_seen_at < now() - interval '90 days';

with ranked_retired as (
  select boot.id,
         row_number() over (
           partition by boot.controller_id
           order by boot.last_seen_at desc, boot.id desc
         ) as retention_rank
  from private.controller_boot_sessions as boot
  where boot.retired_at is not null
)
delete from private.controller_boot_sessions as boot
using ranked_retired as ranked
where boot.id = ranked.id
  and ranked.retention_rank > 4095;

comment on function private.prune_controller_boot_sessions_after_insert() is
  'Bounds retired controller replay sessions to 90 days and 4095 rows per controller; never deletes an active boot.';

commit;

