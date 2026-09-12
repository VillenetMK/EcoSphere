-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.
--
-- Store a controller's completed/rejected manual-pulse observation on its next
-- authenticated sync. No command, output setting, or sensor record is changed
-- by diagnostic persistence. Existing firmware may omit the optional argument.

create function private.controller_pump_diagnostic_is_valid(p_diagnostic jsonb)
returns boolean
language plpgsql immutable security invoker set search_path = ''
as $validation$
declare
  v_keys constant text[] := array[
    'request_id', 'result', 'reason', 'elapsed_ms', 'gpio26_start', 'gpio26_end',
    'led_power_before', 'led_power_after', 'led_duty_before', 'led_duty_during',
    'led_duty_after'
  ];
  v_key text;
  v_number numeric;
  v_max numeric;
begin
  if p_diagnostic is null
     or pg_catalog.jsonb_typeof(p_diagnostic) is distinct from 'object'
     or pg_catalog.octet_length(p_diagnostic::text) > 2048 then
    return false;
  end if;
  if not (p_diagnostic ?& v_keys)
     or (p_diagnostic - v_keys) <> '{}'::jsonb
     or pg_catalog.jsonb_typeof(p_diagnostic -> 'result') is distinct from 'string'
     or pg_catalog.jsonb_typeof(p_diagnostic -> 'reason') is distinct from 'string' then
    return false;
  end if;
  if not (
    ((p_diagnostic ->> 'result') = 'completed' and (p_diagnostic ->> 'reason') in (
      'duration_elapsed', 'authorization_expired', 'mode_changed',
      'water_unavailable', 'soil_invalid', 'soil_wet', 'manual_stop'
    ))
    or ((p_diagnostic ->> 'result') = 'rejected' and (p_diagnostic ->> 'reason') in (
      'remote_gate', 'local_gate'
    ))
  ) then
    return false;
  end if;

  foreach v_key in array array[
    'request_id', 'elapsed_ms', 'gpio26_start', 'gpio26_end', 'led_power_before',
    'led_power_after', 'led_duty_before', 'led_duty_during', 'led_duty_after'
  ] loop
    if v_key in ('gpio26_start', 'gpio26_end', 'led_duty_during')
       and (p_diagnostic -> v_key) = 'null'::jsonb then
      continue;
    end if;
    if pg_catalog.jsonb_typeof(p_diagnostic -> v_key) is distinct from 'number' then
      return false;
    end if;
    v_number := (p_diagnostic ->> v_key)::numeric;
    v_max := case
      when v_key = 'request_id' then 9007199254740991
      when v_key = 'elapsed_ms' then 4294967295
      when v_key in ('gpio26_start', 'gpio26_end') then 1
      when v_key in ('led_power_before', 'led_power_after') then 100
      else 256
    end;
    if v_number < (case when v_key = 'request_id' then 1 else 0 end)
       or v_number > v_max
       or v_number <> pg_catalog.trunc(v_number) then
      return false;
    end if;
  end loop;
  return true;
end;
$validation$;
revoke all on function private.controller_pump_diagnostic_is_valid(jsonb)
  from public, anon, authenticated, service_role;

create table private.controller_pump_diagnostics (
  controller_id bigint not null references private.device_controllers(id),
  request_id bigint not null check (request_id > 0),
  observed_at timestamptz not null default now(),
  diagnostic jsonb not null,
  primary key (controller_id, request_id),
  constraint controller_pump_diagnostics_valid
    check (private.controller_pump_diagnostic_is_valid(diagnostic)),
  constraint controller_pump_diagnostics_request_matches
    check ((diagnostic ->> 'request_id')::numeric::bigint = request_id)
);
alter table private.controller_pump_diagnostics enable row level security;
alter table private.controller_pump_diagnostics force row level security;
revoke all on table private.controller_pump_diagnostics
  from public, anon, authenticated, service_role;
comment on table private.controller_pump_diagnostics is
  'Authenticated firmware observations; GPIO/PWM readback is not proof of motor movement, water flow, or measured LED brightness.';

-- Adding a defaulted parameter creates a new signature in PostgreSQL. Remove
-- the old signature to avoid ambiguous PostgREST calls from existing firmware.
drop function public.controller_sync(text, text, bigint, text, boolean, double precision, double precision, double precision, double precision, text, boolean, boolean, boolean, boolean, integer, integer, text);
create function public.controller_sync(p_hardware_uid text, p_device_secret text, p_heartbeat_seq bigint, p_firmware_version text DEFAULT NULL::text, p_has_telemetry boolean DEFAULT false, p_temperature double precision DEFAULT NULL::double precision, p_air_humidity double precision DEFAULT NULL::double precision, p_soil_humidity double precision DEFAULT NULL::double precision, p_light_lux double precision DEFAULT NULL::double precision, p_water_level text DEFAULT NULL::text, p_fan_on boolean DEFAULT NULL::boolean, p_pump_on boolean DEFAULT NULL::boolean, p_led_on boolean DEFAULT NULL::boolean, p_reported_auto_mode boolean DEFAULT NULL::boolean, p_reported_fan_power integer DEFAULT NULL::integer, p_reported_led_power integer DEFAULT NULL::integer, p_boot_nonce text DEFAULT NULL::text, p_pump_diagnostic jsonb DEFAULT NULL::jsonb)
returns table(fan_target boolean, led_target boolean, auto_mode boolean, pump_request bigint, pump_duration_ms integer, fan_power integer, led_power integer, secure_mode boolean, heartbeat_seq bigint, pump_authorized boolean, pump_expires_at_epoch bigint, pump_allow_wet_soil boolean, pump_bypass_sensor_checks boolean)
language plpgsql security definer set search_path = ''
as $sync$
declare
  response record;
  v_controller_id bigint;
  v_current_request bigint;
  v_diagnostic_request bigint;
begin
  if not private.controller_gateway_access_allowed() then
    raise exception 'controller edge gateway required' using errcode = '42501';
  end if;

  -- Authenticate the controller and accept its nonce/heartbeat before examining
  -- or storing diagnostics. This also holds the existing lock order through the
  -- entire transaction: ecosystem -> controller -> control.
  select * into response from private.controller_sync_impl(
    $1, $2, $3, $4, $5, $6, $7, $8, $9,
    $10, $11, $12, $13, $14, $15, $16, $17
  );
  if not found then return; end if;

  if p_pump_diagnostic is not null then
    if not private.controller_pump_diagnostic_is_valid(p_pump_diagnostic) then
      raise exception 'invalid pump diagnostic' using errcode = '22023';
    end if;
    v_diagnostic_request := (p_pump_diagnostic ->> 'request_id')::numeric::bigint;
    select controller.id, control.pump_request
      into v_controller_id, v_current_request
    from private.ecosystems as ecosystem
    join private.device_controllers as controller
      on controller.id = ecosystem.active_controller_id
     and controller.ecosystem_id = ecosystem.id
    join public.device_control as control
      on control.id = 1 and control.active_controller_id = controller.id
    where ecosystem.id = 1
      and controller.status = 'active'
      and controller.hardware_uid = upper(btrim(p_hardware_uid));
    if not found or v_diagnostic_request > v_current_request then
      raise exception 'pump diagnostic request was not issued' using errcode = '22023';
    end if;

    insert into private.controller_pump_diagnostics(controller_id, request_id, diagnostic)
    values (v_controller_id, v_diagnostic_request, p_pump_diagnostic)
    on conflict (controller_id, request_id) do nothing;
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
         response.pump_expires_at_epoch,
         coalesce(response.pump_authorized
           and not control.auto_mode
           and control.pump_request = response.pump_request
           and control.pump_allow_wet_soil, false),
         coalesce(response.pump_authorized
           and not control.auto_mode
           and control.pump_request = response.pump_request
           and control.pump_duration_ms = 3000
           and control.pump_allow_wet_soil
           and control.pump_bypass_sensor_checks, false)
  from private.ecosystems as ecosystem
  join public.device_control as control on control.id = 1
  where ecosystem.id = 1;
end;
$sync$;
revoke all on function public.controller_sync(text, text, bigint, text, boolean, double precision, double precision, double precision, double precision, text, boolean, boolean, boolean, boolean, integer, integer, text, jsonb)
  from public, anon, authenticated, service_role;
grant execute on function public.controller_sync(text, text, bigint, text, boolean, double precision, double precision, double precision, double precision, text, boolean, boolean, boolean, boolean, integer, integer, text, jsonb)
  to anon, service_role;

notify pgrst, 'reload schema';

