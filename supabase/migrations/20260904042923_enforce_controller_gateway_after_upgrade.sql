begin;

create or replace function private.controller_gateway_access_allowed()
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
  select coalesce((select auth.role()) = 'service_role', false)
    or coalesce((
      select not ecosystem.strict_controller_protocol
      from private.ecosystems as ecosystem
      where ecosystem.id = 1
    ), false);
$function$;

revoke all on function private.controller_gateway_access_allowed()
  from public, anon, authenticated, service_role;

create or replace function public.controller_begin_pairing(
  p_hardware_uid text,
  p_device_secret text,
  p_firmware_version text default null
)
returns table (
  pairing_code text,
  expires_at timestamptz,
  controller_status text
)
language plpgsql
volatile
security definer
set search_path = ''
as $function$
begin
  if not private.controller_gateway_access_allowed() then
    raise exception 'controller edge gateway required' using errcode = '42501';
  end if;

  return query
  select response.pairing_code, response.expires_at, response.controller_status
  from private.controller_begin_pairing_impl($1, $2, $3) as response;
end;
$function$;

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
language plpgsql
volatile
security definer
set search_path = ''
as $function$
begin
  if not private.controller_gateway_access_allowed() then
    raise exception 'controller edge gateway required' using errcode = '42501';
  end if;

  return query
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
    $1, $2, $3, $4, $5, $6, $7, $8, $9,
    $10, $11, $12, $13, $14, $15, $16, $17
  ) as response
  join private.ecosystems as ecosystem on ecosystem.id = 1;
end;
$function$;

revoke all on function public.controller_begin_pairing(text, text, text)
  from public, authenticated;
grant execute on function public.controller_begin_pairing(text, text, text)
  to anon, service_role;

revoke all on function public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) from public, authenticated;
grant execute on function public.controller_sync(
  text, text, bigint, text, boolean, double precision, double precision,
  double precision, double precision, text, boolean, boolean, boolean,
  boolean, integer, integer, text
) to anon, service_role;

comment on function private.controller_gateway_access_allowed() is
  'Allows legacy direct controller RPCs only until strict protocol activation; service-role Edge gateway calls always remain available.';

commit;

