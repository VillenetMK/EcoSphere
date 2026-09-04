-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

-- Phase A of the controller Edge gateway rollout. This migration is safe while
-- deployed controllers still use the legacy anonymous REST RPCs. Do not revoke
-- those RPCs here: the separately gated cutover migration does that only after
-- the firmware fleet has moved to the Edge endpoint.

begin;

create table private.controller_gateway_rate_limits (
  bucket_scope text not null,
  bucket_key text not null,
  request_count integer not null default 0,
  window_started_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (bucket_scope, bucket_key),
  constraint controller_gateway_rate_limits_scope
    check (bucket_scope in ('global', 'source')),
  constraint controller_gateway_rate_limits_key
    check (bucket_key ~ '^[0-9a-f]{64}$'),
  constraint controller_gateway_rate_limits_count
    check (request_count between 0 and 7200)
);

create index controller_gateway_rate_limits_source_cleanup_idx
  on private.controller_gateway_rate_limits (updated_at, bucket_key)
  where bucket_scope = 'source';

alter table private.controller_gateway_rate_limits enable row level security;
alter table private.controller_gateway_rate_limits force row level security;

create policy "deny_direct_controller_gateway_rate_limits"
  on private.controller_gateway_rate_limits
  as restrictive
  for all
  to public
  using (false)
  with check (false);

revoke all on table private.controller_gateway_rate_limits
  from public, anon, authenticated, service_role;

-- Existing sources use only their own non-blocking lock. The global lock is
-- reserved for source admission, TTL cleanup and capacity eviction, so a flood
-- from unrelated addresses cannot queue a healthy controller heartbeat.
-- Server constants intentionally keep quotas and storage budget out of request
-- input.
create function public.controller_gateway_take_rate_limit(
  p_source_bucket text
)
returns table (allowed boolean, retry_after_seconds integer)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_global_bucket constant text := repeat('0', 64);
  v_source private.controller_gateway_rate_limits%rowtype;
  v_global private.controller_gateway_rate_limits%rowtype;
  v_source_count integer;
  v_retry integer;
begin
  if p_source_bucket is null or p_source_bucket !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid controller gateway rate-limit bucket'
      using errcode = '22023';
  end if;

  -- The hot path serializes only requests for this exact hashed source. A
  -- contended source fails fast rather than accumulating database waiters.
  if not pg_catalog.pg_try_advisory_xact_lock(
    pg_catalog.hashtextextended(p_source_bucket, 2026090403)
  ) then
    return query select false, 1;
    return;
  end if;

  select * into v_source
  from private.controller_gateway_rate_limits as limit_row
  where limit_row.bucket_scope = 'source'
    and limit_row.bucket_key = p_source_bucket
  for update;

  if found then
    if v_source.window_started_at <= now() - interval '1 minute' then
      update private.controller_gateway_rate_limits as limit_row
      set request_count = 0,
          window_started_at = now(),
          updated_at = now()
      where limit_row.bucket_scope = 'source'
        and limit_row.bucket_key = p_source_bucket
      returning * into v_source;
    end if;

    if v_source.request_count >= 120 then
      v_retry := greatest(
        1,
        ceil(extract(epoch from (
          v_source.window_started_at + interval '1 minute' - now()
        )))::integer
      );
      return query select false, v_retry;
      return;
    end if;

    update private.controller_gateway_rate_limits as limit_row
    set request_count = limit_row.request_count + 1,
        updated_at = now()
    where limit_row.bucket_scope = 'source'
      and limit_row.bucket_key = p_source_bucket;

    return query select true, 0;
    return;
  end if;

  -- New, attacker-selected source keys alone need the global admission lock.
  -- This prevents an IP-rotation flood from serializing known controller rows.
  if not pg_catalog.pg_try_advisory_xact_lock(20260904030000) then
    return query select false, 1;
    return;
  end if;

  -- TTL cleanup is bounded and happens only on source admission. Existing
  -- controller heartbeats never scan or evict unrelated source rows.
  delete from private.controller_gateway_rate_limits as limit_row
  where limit_row.ctid in (
    select expired.ctid
    from private.controller_gateway_rate_limits as expired
    where expired.bucket_scope = 'source'
      and expired.updated_at < now() - interval '24 hours'
    order by expired.updated_at, expired.bucket_key
    limit 256
  );

  insert into private.controller_gateway_rate_limits (
    bucket_scope, bucket_key, request_count, window_started_at, updated_at
  ) values ('global', v_global_bucket, 0, now(), now())
  on conflict (bucket_scope, bucket_key) do nothing;

  select * into v_global
  from private.controller_gateway_rate_limits as limit_row
  where limit_row.bucket_scope = 'global'
    and limit_row.bucket_key = v_global_bucket
  for update;

  if v_global.window_started_at <= now() - interval '1 minute' then
    update private.controller_gateway_rate_limits as limit_row
    set request_count = 0,
        window_started_at = now(),
        updated_at = now()
    where limit_row.bucket_scope = 'global'
      and limit_row.bucket_key = v_global_bucket
    returning * into v_global;
  end if;

  -- The global counter caps new source admission at 7200/minute. It never
  -- gates an existing controller source; a deployment WAF handles a truly
  -- distributed request flood after source admission.
  if v_global.request_count >= 7200 then
    v_retry := greatest(
      1,
      ceil(extract(epoch from (
        v_global.window_started_at + interval '1 minute' - now()
      )))::integer
    );
    return query select false, v_retry;
    return;
  end if;

  select count(*)::integer into v_source_count
  from private.controller_gateway_rate_limits as limit_row
  where limit_row.bucket_scope = 'source';

  -- Source rows are capped at 4096 even if traffic never becomes old enough
  -- for TTL cleanup. Eviction is deterministic and cannot grow the table.
  if v_source_count >= 4096 then
    delete from private.controller_gateway_rate_limits as limit_row
    where limit_row.ctid = (
      select oldest.ctid
      from private.controller_gateway_rate_limits as oldest
      where oldest.bucket_scope = 'source'
      order by oldest.updated_at, oldest.bucket_key
      limit 1
    );

    if not found then
      return query select false, 60;
      return;
    end if;
  end if;

  insert into private.controller_gateway_rate_limits (
    bucket_scope, bucket_key, request_count, window_started_at, updated_at
  ) values ('source', p_source_bucket, 1, now(), now());

  update private.controller_gateway_rate_limits as limit_row
  set request_count = limit_row.request_count + 1,
      updated_at = now()
  where limit_row.bucket_scope = 'global'
    and limit_row.bucket_key = v_global_bucket;

  return query select true, 0;
end;
$function$;

revoke all on function public.controller_gateway_take_rate_limit(text)
  from public, anon, authenticated, service_role;
grant execute on function public.controller_gateway_take_rate_limit(text)
  to service_role;

comment on table private.controller_gateway_rate_limits is
  'Bounded, TTL-cleaned SHA-256 source/global controller Edge gateway quotas.';
comment on function public.controller_gateway_take_rate_limit(text) is
  'Service-role-only atomic global and source request quota for controller Edge gateway.';

-- Replacement previously sets legacy_writes_allowed=false before the first
-- replay-protected 2.1+ sync. Expose strict_controller_protocol instead, so
-- no client can label that transitional state as secure.
create or replace function public.controller_sync(
  p_hardware_uid text,
  p_device_secret text,
  p_heartbeat_seq bigint,
  p_firmware_version text default null,
  p_has_telemetry boolean default false,
  p_temperature double precision default null,
  p_air_humidity double precision default null,
  p_soil_humidity double precision default null,
  p_light_lux double precision default null,
  p_water_level text default null,
  p_fan_on boolean default null,
  p_pump_on boolean default null,
  p_led_on boolean default null,
  p_reported_auto_mode boolean default null,
  p_reported_fan_power integer default null,
  p_reported_led_power integer default null,
  p_boot_nonce text default null
)
returns table (
  fan_target boolean,
  led_target boolean,
  auto_mode boolean,
  pump_request bigint,
  pump_duration_ms integer,
  fan_power integer,
  led_power integer,
  secure_mode boolean,
  heartbeat_seq bigint,
  pump_authorized boolean,
  pump_expires_at_epoch bigint
)
language sql
volatile
security definer
set search_path = ''
as $function$
  select response.fan_target,
         response.led_target,
         response.auto_mode,
         response.pump_request,
         response.pump_duration_ms,
         response.fan_power,
         response.led_power,
         ecosystem.strict_controller_protocol,
         response.heartbeat_seq,
         response.pump_authorized,
         response.pump_expires_at_epoch
  from private.controller_sync_impl(
    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17
  ) as response
  join private.ecosystems as ecosystem on ecosystem.id = 1;
$function$;

create or replace function public.controller_admin_status()
returns table (
  controller_id bigint,
  hardware_uid_masked text,
  controller_status text,
  firmware_version text,
  last_seen_at timestamptz,
  secure_mode boolean
)
language sql
stable
security definer
set search_path = ''
as $function$
  select response.controller_id,
         response.hardware_uid_masked,
         response.controller_status,
         response.firmware_version,
         response.last_seen_at,
         ecosystem.strict_controller_protocol
  from private.controller_admin_status_impl() as response
  join private.ecosystems as ecosystem on ecosystem.id = 1;
$function$;

create or replace function public.replace_active_controller(
  p_pairing_code text
)
returns table (
  controller_id bigint,
  hardware_uid_masked text,
  controller_status text,
  firmware_version text,
  secure_mode boolean
)
language sql
volatile
security definer
set search_path = ''
as $function$
  select response.controller_id,
         response.hardware_uid_masked,
         response.controller_status,
         response.firmware_version,
         ecosystem.strict_controller_protocol
  from private.replace_active_controller_impl($1) as response
  join private.ecosystems as ecosystem on ecosystem.id = 1;
$function$;

revoke all on function public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) from public, anon, authenticated, service_role;
grant execute on function public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) to anon, service_role;

revoke all on function public.controller_admin_status()
  from public, anon, authenticated;
grant execute on function public.controller_admin_status()
  to authenticated;
revoke all on function public.replace_active_controller(text)
  from public, anon, authenticated;
grant execute on function public.replace_active_controller(text)
  to authenticated;

commit;

