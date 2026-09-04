-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

begin;

-- Interactive set-points are last-write-wins and serialized by the singleton
-- device_control row. A small fixed cooldown rejected ordinary human testing,
-- so use bounded rolling quotas instead. Pump protections remain stricter.
create or replace function private.control_command_impl(
  p_action text,
  p_value integer default null
)
returns setof public.device_control
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_user_id uuid := (select auth.uid());
  v_role text;
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
          pump_expires_at = null
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
      if v_control.auto_mode then
        raise exception 'manual watering is disabled in automatic mode'
          using errcode = '55000';
      end if;

      if exists (
        select 1
        from private.control_audit_log as audit
        where audit.pump_requested
          and audit.created_at > now() - interval '10 seconds'
      ) then
        raise exception 'watering denied: system pump cooldown is active'
          using errcode = '55000';
      end if;

      if exists (
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
      if v_soil_humidity is null then
        raise exception 'watering denied: soil sensor is unavailable'
          using errcode = '55000';
      end if;
      if v_soil_humidity >= 60 then
        raise exception 'watering denied: soil humidity is already 60 percent or higher'
          using errcode = '55000';
      end if;
      if v_water_level is distinct from 'high' then
        raise exception 'watering denied: water level is not sufficient'
          using errcode = '55000';
      end if;

      update public.device_control
      set pump_request = pump_request + 1,
          pump_duration_ms = v_duration,
          pump_expires_at = now() + interval '15 seconds'
      where id = 1;

  end case;

  return query
    select control.*
    from public.device_control as control
    where control.id = 1;
end;
$function$;

revoke all on function private.control_command_impl(text, integer)
  from public, anon, authenticated, service_role;

comment on function private.control_command_impl(text, integer) is
  'Validated operator control with idempotent set-points, rolling quotas, strict pump safety and immutable audit.';

commit;
