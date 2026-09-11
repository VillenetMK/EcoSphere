-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.
--
-- Explicit sensor-independent manual pulses for the authorized CIMA account.
-- This never requests a pulse or changes telemetry. Automatic watering and
-- all other accounts keep their sensor checks. Session, manual mode, fresh
-- telemetry and expiry remain required; CIMA pulses are exactly 3000 ms.

alter table public.device_control
  add column pump_bypass_sensor_checks boolean not null default false;
alter table private.control_audit_log
  add column pump_bypass_sensor_checks boolean not null default false;

drop function public.my_control_permissions();
create function public.my_control_permissions()
returns table(allow_wet_soil_manual_watering boolean, allow_sensorless_manual_watering boolean)
language sql stable security definer set search_path = ''
as $permission$
  select capability.allowed,
         capability.allowed and (select auth.uid()) = '367e842b-fd47-4c38-a3fc-c54c47732a9e'::uuid
  from (select coalesce(
    private.current_session_is_active(false)
    and exists (
      select 1 from private.user_profiles as profile
      join private.manual_watering_permissions as permission
        on permission.user_id = profile.user_id
      where profile.user_id = (select auth.uid())
        and profile.status = 'approved'
        and profile.role in ('operator','admin')
        and (profile.role <> 'admin' or private.current_session_is_active(true))
        and permission.allow_wet_soil_manual_watering
    ), false) as allowed) as capability;
$permission$;
revoke all on function public.my_control_permissions() from public, anon;
grant execute on function public.my_control_permissions() to authenticated;

CREATE OR REPLACE FUNCTION private.control_command_impl(p_action text, p_value integer DEFAULT NULL::integer)
 RETURNS SETOF public.device_control
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_user_id uuid := (select auth.uid());
  v_role text;
  v_allow_wet_soil boolean := false;
  v_skip_pump_cooldown boolean := false;
  v_bypass_sensor_checks boolean := false;
  v_action text;
  v_control public.device_control%rowtype;
  v_soil_humidity double precision;
  v_water_level text;
  v_telemetry_at timestamptz;
  v_duration integer;
  v_limit_started_at timestamptz;
  v_retry_after integer;
begin
  if octet_length(coalesce(p_action, '')) > 32 then
    raise exception 'control action is too long' using errcode = '22023';
  end if;
  v_action := lower(btrim(coalesce(p_action, '')));

  if v_user_id is null
     or not private.current_session_is_active(false) then
    raise exception 'active authenticated session required'
      using errcode = '42501';
  end if;

  select profile.role into v_role
  from private.user_profiles as profile
  where profile.user_id = v_user_id
    and profile.status = 'approved'
    and profile.role in ('operator', 'admin');

  if not found then
    raise exception 'operator access required' using errcode = '42501';
  end if;

  if v_role = 'admin' then
    perform private.require_admin_aal2();
  end if;

  if v_action not in ('auto_mode', 'fan_power', 'led_power', 'pump') then
    raise exception 'unsupported control action'
      using errcode = '22023';
  end if;

  if v_action = 'auto_mode'
     and (p_value is null or p_value not in (0, 1)) then
    raise exception 'auto_mode value must be 0 or 1'
      using errcode = '22023';
  elsif v_action in ('fan_power', 'led_power')
        and (p_value is null or p_value not between 0 and 100) then
    raise exception '% power must be between 0 and 100',
      case when v_action = 'fan_power' then 'fan' else 'LED' end
      using errcode = '22023';
  elsif v_action = 'pump' then
    v_duration := coalesce(p_value, 3000);
    if v_duration not between 500 and 10000 then
      raise exception 'pump duration must be between 500 and 10000 ms'
        using errcode = '22023';
    end if;
  end if;

  select * into v_control
  from public.device_control as control
  where control.id = 1
  for update;

  if not found then
    raise exception 'device control row not found' using errcode = 'P0002';
  end if;

  -- Replaying the current set-point is a successful idempotent request. It
  -- produces neither a write nor an audit entry and is safe for client retries.
  if (v_action = 'auto_mode'
      and v_control.auto_mode is not distinct from (p_value = 1))
     or (v_action = 'fan_power'
         and v_control.fan_power is not distinct from p_value)
     or (v_action = 'led_power'
         and v_control.led_power is not distinct from p_value) then
    return query
      select control.*
      from public.device_control as control
      where control.id = 1;
    return;
  end if;

  if v_action in ('auto_mode', 'fan_power', 'led_power') then
    -- At most 30 accepted state changes per ten seconds across the system.
    -- Normal operators never reach this, while automated flooding is bounded.
    select audit.created_at into v_limit_started_at
    from private.control_audit_log as audit
    where audit.created_at > now() - interval '10 seconds'
      and (
        audit.auto_mode_after is not null
        or audit.fan_power_after is not null
        or audit.led_power_after is not null
      )
    order by audit.created_at desc, audit.id desc
    offset 29
    limit 1;

    if found then
      v_retry_after := greatest(
        1,
        ceil(extract(epoch from (
          v_limit_started_at + interval '10 seconds' - now()
        )))::integer
      );
      raise sqlstate 'PGRST' using
        message = jsonb_build_object(
          'code', 'ECOSPHERE_CONTROL_BUSY',
          'message', format(
            'Hay demasiadas órdenes simultáneas. Intenta nuevamente en %s s.',
            v_retry_after
          ),
          'details', null,
          'hint', 'Los controles se habilitarán automáticamente.'
        )::text,
        detail = jsonb_build_object(
          'status', 429,
          'headers', jsonb_build_object(
            'Retry-After', v_retry_after::text
          )
        )::text;
    end if;

    -- Each account may make ten accepted state changes per ten seconds. This
    -- permits fast manual tuning without allowing unbounded audit/WAL growth.
    select audit.created_at into v_limit_started_at
    from private.control_audit_log as audit
    where audit.actor_user_id = v_user_id
      and audit.created_at > now() - interval '10 seconds'
      and (
        audit.auto_mode_after is not null
        or audit.fan_power_after is not null
        or audit.led_power_after is not null
      )
    order by audit.created_at desc, audit.id desc
    offset 9
    limit 1;

    if found then
      v_retry_after := greatest(
        1,
        ceil(extract(epoch from (
          v_limit_started_at + interval '10 seconds' - now()
        )))::integer
      );
      raise sqlstate 'PGRST' using
        message = jsonb_build_object(
          'code', 'ECOSPHERE_CONTROL_RATE_LIMIT',
          'message', format(
            'Estás enviando órdenes demasiado rápido. Intenta nuevamente en %s s.',
            v_retry_after
          ),
          'details', null,
          'hint', 'Espera un instante antes de volver a ajustar el control.'
        )::text,
        detail = jsonb_build_object(
          'status', 429,
          'headers', jsonb_build_object(
            'Retry-After', v_retry_after::text
          )
        )::text;
    end if;
  end if;

  case v_action
    when 'auto_mode' then
      update public.device_control
      set auto_mode = (p_value = 1),
          pump_expires_at = null,
          pump_allow_wet_soil = false,
          pump_bypass_sensor_checks = false
      where id = 1;

    when 'fan_power' then
      if v_control.auto_mode then
        raise exception 'manual fan control is disabled in automatic mode'
          using errcode = '55000';
      end if;

      update public.device_control
      set fan_power = p_value,
          fan_target = (p_value > 0)
      where id = 1;

    when 'led_power' then
      if v_control.auto_mode then
        raise exception 'manual LED control is disabled in automatic mode'
          using errcode = '55000';
      end if;

      update public.device_control
      set led_power = p_value,
          led_target = (p_value > 0)
      where id = 1;

    when 'pump' then
      select exists (
        select 1 from private.manual_watering_permissions as permission
        where permission.user_id = v_user_id
          and permission.allow_wet_soil_manual_watering
      ) into v_allow_wet_soil;
      -- Only the authorized CIMA session may request consecutive manual pulses.
      -- The capability is tied to the immutable account ID and private grant.
      v_skip_pump_cooldown := v_allow_wet_soil
        and v_user_id = '367e842b-fd47-4c38-a3fc-c54c47732a9e'::uuid;
      v_bypass_sensor_checks := v_skip_pump_cooldown;
      if v_bypass_sensor_checks and v_duration <> 3000 then
        raise exception 'CIMA manual watering requires a 3000 ms pulse'
          using errcode = '22023';
      end if;
      if v_control.auto_mode then
        raise exception 'manual watering is disabled in automatic mode'
          using errcode = '55000';
      end if;

      if not v_skip_pump_cooldown and exists (
        select 1
        from private.control_audit_log as audit
        where audit.pump_requested
          and audit.created_at > now() - interval '10 seconds'
      ) then
        raise exception 'watering denied: system pump cooldown is active'
          using errcode = '55000';
      end if;

      if not v_skip_pump_cooldown and exists (
        select 1
        from private.control_audit_log as audit
        where audit.pump_requested
          and audit.actor_user_id = v_user_id
          and audit.created_at > now() - interval '60 seconds'
      ) then
        raise exception 'watering denied: operator pump cooldown is active'
          using errcode = '55000';
      end if;

      select record.soil_humidity, lower(record.water_level), record.created_at
        into v_soil_humidity, v_water_level, v_telemetry_at
      from public.sensor_records as record
      where record.controller_id = v_control.active_controller_id
      order by record.created_at desc, record.id desc
      limit 1;

      if not found or v_telemetry_at < now() - interval '30 seconds' then
        raise exception 'watering denied: current telemetry is unavailable'
          using errcode = '55000';
      end if;
      if not v_bypass_sensor_checks and v_soil_humidity is null then
        raise exception 'watering denied: soil sensor is unavailable'
          using errcode = '55000';
      end if;
      if v_soil_humidity >= 60 and not v_allow_wet_soil then
        raise exception 'watering denied: soil humidity is already 60 percent or higher'
          using errcode = '55000';
      end if;
      if not v_bypass_sensor_checks and v_water_level is distinct from 'high' then
        raise exception 'watering denied: water level is not sufficient'
          using errcode = '55000';
      end if;

      update public.device_control
      set pump_request = pump_request + 1,
          pump_duration_ms = v_duration,
          pump_allow_wet_soil = v_allow_wet_soil,
          pump_bypass_sensor_checks = v_bypass_sensor_checks,
          pump_expires_at = now() + interval '15 seconds'
      where id = 1;

  end case;

  return query
    select control.*
    from public.device_control as control
    where control.id = 1;
end;
$function$;

CREATE OR REPLACE FUNCTION private.log_device_control_changes()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_user_id uuid := (select auth.uid());
  v_username text;
  v_role text;
begin
  if v_user_id is null then
    return new;
  end if;

  select profile.username, profile.role
  into v_username, v_role
  from private.user_profiles as profile
  where profile.user_id = v_user_id
    and profile.status = 'approved'
    and profile.role in ('operator', 'admin');

  if not found then
    return new;
  end if;

  if new.auto_mode is not distinct from old.auto_mode
     and new.fan_power is not distinct from old.fan_power
     and new.led_power is not distinct from old.led_power
     and new.pump_request is not distinct from old.pump_request then
    return new;
  end if;

  insert into private.control_audit_log (
    actor_user_id, actor_username, actor_role,
    auto_mode_before, auto_mode_after,
    fan_power_before, fan_power_after,
    led_power_before, led_power_after,
    pump_requested, pump_duration_ms, pump_allow_wet_soil, pump_bypass_sensor_checks
  )
  values (
    v_user_id, v_username, v_role,
    case when new.auto_mode is distinct from old.auto_mode then old.auto_mode end,
    case when new.auto_mode is distinct from old.auto_mode then new.auto_mode end,
    case when new.fan_power is distinct from old.fan_power then old.fan_power end,
    case when new.fan_power is distinct from old.fan_power then new.fan_power end,
    case when new.led_power is distinct from old.led_power then old.led_power end,
    case when new.led_power is distinct from old.led_power then new.led_power end,
    new.pump_request > old.pump_request,
    case when new.pump_request > old.pump_request then new.pump_duration_ms end,
    new.pump_request > old.pump_request and new.pump_allow_wet_soil,
    new.pump_request > old.pump_request and new.pump_bypass_sensor_checks
  );

  return new;
end;
$function$;

-- Return-type extension; atomically recreate the public wrapper and its ACL.
drop function public.controller_sync(text, text, bigint, text, boolean, double precision, double precision, double precision, double precision, text, boolean, boolean, boolean, boolean, integer, integer, text);
CREATE OR REPLACE FUNCTION public.controller_sync(p_hardware_uid text, p_device_secret text, p_heartbeat_seq bigint, p_firmware_version text DEFAULT NULL::text, p_has_telemetry boolean DEFAULT false, p_temperature double precision DEFAULT NULL::double precision, p_air_humidity double precision DEFAULT NULL::double precision, p_soil_humidity double precision DEFAULT NULL::double precision, p_light_lux double precision DEFAULT NULL::double precision, p_water_level text DEFAULT NULL::text, p_fan_on boolean DEFAULT NULL::boolean, p_pump_on boolean DEFAULT NULL::boolean, p_led_on boolean DEFAULT NULL::boolean, p_reported_auto_mode boolean DEFAULT NULL::boolean, p_reported_fan_power integer DEFAULT NULL::integer, p_reported_led_power integer DEFAULT NULL::integer, p_boot_nonce text DEFAULT NULL::text)
 RETURNS TABLE(fan_target boolean, led_target boolean, auto_mode boolean, pump_request bigint, pump_duration_ms integer, fan_power integer, led_power integer, secure_mode boolean, heartbeat_seq bigint, pump_authorized boolean, pump_expires_at_epoch bigint, pump_allow_wet_soil boolean, pump_bypass_sensor_checks boolean)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  response record;
begin
  if not private.controller_gateway_access_allowed() then
    raise exception 'controller edge gateway required' using errcode = '42501';
  end if;

  -- A separate statement observes the state locked/updated by sync_impl.
  select * into response from private.controller_sync_impl(
    $1, $2, $3, $4, $5, $6, $7, $8, $9,
    $10, $11, $12, $13, $14, $15, $16, $17
  );
  if not found then return; end if;

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
$function$;

revoke all on function public.controller_sync(text, text, bigint, text, boolean, double precision, double precision, double precision, double precision, text, boolean, boolean, boolean, boolean, integer, integer, text) from public, authenticated;
grant execute on function public.controller_sync(text, text, bigint, text, boolean, double precision, double precision, double precision, double precision, text, boolean, boolean, boolean, boolean, integer, integer, text) to anon, service_role;
notify pgrst, 'reload schema';

