-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

-- Firmware anterior a 2.0.5 no puede distinguir de forma segura un GPIO34
-- flotante ni un flotador abierto de un cable desconectado. Conservamos su
-- heartbeat y los estados de los actuadores, pero no publicamos esas dos
-- señales como lecturas ambientales válidas.

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
  p_reported_led_power integer default null
)
returns table(
  fan_target boolean,
  led_target boolean,
  auto_mode boolean,
  pump_request bigint,
  pump_duration_ms integer,
  fan_power integer,
  led_power integer,
  secure_mode boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_hardware_uid text := upper(regexp_replace(coalesce(p_hardware_uid, ''), '[^0-9A-Fa-f]', '', 'g'));
  v_secret text := lower(trim(coalesce(p_device_secret, '')));
  v_secret_hash bytea;
  v_controller private.device_controllers%rowtype;
  v_secure_mode_just_enabled boolean := false;
  v_firmware text := btrim(coalesce(p_firmware_version, ''));
  v_firmware_without_build text;
  v_firmware_prerelease text;
  v_firmware_core text;
  v_firmware_parts text[];
  v_semver_valid boolean := false;
  v_sensor_presence_safe boolean := false;
begin
  if v_hardware_uid !~ '^[0-9A-F]{12}$'
     or v_secret !~ '^[0-9a-f]{64}$'
     or p_heartbeat_seq is null
     or p_heartbeat_seq < 0 then
    raise exception 'invalid controller credentials or heartbeat' using errcode = '22023';
  end if;

  -- Una versión malformada nunca interrumpe heartbeat/control: simplemente
  -- no obtiene permiso para publicar sensores cuya presencia no puede probar.
  if char_length(v_firmware) between 5 and 40
     and v_firmware ~
       '^(0|[1-9][0-9]*)[.](0|[1-9][0-9]*)[.](0|[1-9][0-9]*)(-[0-9A-Za-z-]+([.][0-9A-Za-z-]+)*)?([+][0-9A-Za-z-]+([.][0-9A-Za-z-]+)*)?$'
  then
    v_firmware_without_build := split_part(v_firmware, '+', 1);

    if strpos(v_firmware_without_build, '-') > 0 then
      v_firmware_prerelease := substr(
        v_firmware_without_build,
        strpos(v_firmware_without_build, '-') + 1
      );
      v_firmware_core := substr(
        v_firmware_without_build,
        1,
        strpos(v_firmware_without_build, '-') - 1
      );
    else
      v_firmware_prerelease := null;
      v_firmware_core := v_firmware_without_build;
    end if;

    v_semver_valid := not coalesce(
      v_firmware_prerelease ~ '(^|[.])0[0-9]+($|[.])',
      false
    );
  end if;

  if v_semver_valid then
    v_firmware_parts := string_to_array(v_firmware_core, '.');
    v_sensor_presence_safe := (
      v_firmware_parts[1]::numeric,
      v_firmware_parts[2]::numeric,
      v_firmware_parts[3]::numeric
    ) >= (2::numeric, 0::numeric, 5::numeric);
  end if;

  v_secret_hash := extensions.digest(v_secret, 'sha256');

  select c.*
    into v_controller
  from private.device_controllers c
  join private.ecosystems e
    on e.id = c.ecosystem_id
   and e.active_controller_id = c.id
  where c.hardware_uid = v_hardware_uid
    and c.secret_hash = v_secret_hash
    and c.status = 'active'
  for update of c;

  if not found then
    raise exception 'controller is not active' using errcode = '42501';
  end if;

  update private.ecosystems
    set legacy_writes_allowed = false,
        updated_at = now()
  where id = 1
    and legacy_writes_allowed
  returning true into v_secure_mode_just_enabled;

  update private.device_controllers
    set last_seen_at = now(),
        firmware_version = case
          when v_semver_valid then v_firmware
          else firmware_version
        end,
        updated_at = now()
  where id = v_controller.id;

  update public.device_control
    set heartbeat_seq = p_heartbeat_seq,
        esp32_online = true,
        last_seen_at = now()
  where id = 1
    and active_controller_id = v_controller.id;

  if p_has_telemetry then
    insert into public.sensor_records (
      controller_id,
      temperature,
      air_humidity,
      soil_humidity,
      light_lux,
      water_level,
      fan_on,
      pump_on,
      led_on,
      auto_mode,
      fan_power,
      led_power
    )
    values (
      v_controller.id,
      p_temperature,
      p_air_humidity,
      case
        when v_sensor_presence_safe then p_soil_humidity
        else null::double precision
      end,
      p_light_lux,
      case
        when not v_sensor_presence_safe or p_water_level is null then null::text
        else lower(p_water_level)
      end,
      p_fan_on,
      p_pump_on,
      p_led_on,
      p_reported_auto_mode,
      p_reported_fan_power,
      p_reported_led_power
    );
  end if;

  if coalesce(v_secure_mode_just_enabled, false) then
    insert into private.controller_events (
      ecosystem_id,
      controller_id,
      event_type
    )
    values (1, v_controller.id, 'secure_mode_enabled');
  end if;

  return query
    select
      d.fan_target,
      d.led_target,
      d.auto_mode,
      d.pump_request,
      d.pump_duration_ms,
      d.fan_power,
      d.led_power,
      true
    from public.device_control d
    where d.id = 1;
end;
$$;

comment on function public.controller_sync(
  text,
  text,
  bigint,
  text,
  boolean,
  double precision,
  double precision,
  double precision,
  double precision,
  text,
  boolean,
  boolean,
  boolean,
  boolean,
  integer,
  integer
) is 'Authenticates the active ESP32, quarantines unsafe soil/water telemetry from firmware older than 2.0.5, updates heartbeat, and returns control targets.';

revoke all on function public.controller_sync(
  text,
  text,
  bigint,
  text,
  boolean,
  double precision,
  double precision,
  double precision,
  double precision,
  text,
  boolean,
  boolean,
  boolean,
  boolean,
  integer,
  integer
) from public, anon, authenticated;

grant execute on function public.controller_sync(
  text,
  text,
  bigint,
  text,
  boolean,
  double precision,
  double precision,
  double precision,
  double precision,
  text,
  boolean,
  boolean,
  boolean,
  boolean,
  integer,
  integer
) to anon;
