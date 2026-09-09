-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.
-- Voice AI access is explicit and independent of device-control permissions.
-- Account grants and provider secrets are provisioned separately, never here.

create table private.ai_permissions (
  user_id uuid primary key references auth.users(id) on delete cascade,
  enabled boolean not null default false,
  updated_at timestamptz not null default now()
);
alter table private.ai_permissions enable row level security;
revoke all on private.ai_permissions from public, anon, authenticated, service_role;

-- At most twenty timestamps per account; no audio or conversation is stored.
create table private.ai_session_usage (
  user_id uuid primary key references auth.users(id) on delete cascade,
  recent_started_at timestamptz[] not null default '{}',
  constraint ai_session_usage_bounded check (cardinality(recent_started_at) <= 20)
);
alter table private.ai_session_usage enable row level security;
revoke all on private.ai_session_usage from public, anon, authenticated, service_role;

create function public.my_ai_access()
returns boolean language sql stable security definer set search_path = ''
as $$
  select private.current_session_is_active(false)
    and exists (
      select 1 from private.user_profiles as profile
      join private.ai_permissions as permission on permission.user_id=profile.user_id
      where profile.user_id=(select auth.uid()) and permission.enabled
        and profile.status='approved' and profile.role in ('operator','admin')
        and (profile.role<>'admin' or private.current_session_is_active(true))
    );
$$;
revoke all on function public.my_ai_access() from public, anon, service_role;
grant execute on function public.my_ai_access() to authenticated;

create function public.reserve_ai_session()
returns boolean language plpgsql volatile security definer set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_now timestamptz := clock_timestamp();
  v_starts timestamptz[];
begin
  if not public.my_ai_access() then
    raise exception using errcode='P0001',message='AI_ACCESS_DENIED';
  end if;
  insert into private.ai_session_usage(user_id) values(v_user_id)
  on conflict(user_id) do nothing;
  select recent_started_at into v_starts from private.ai_session_usage
    where user_id=v_user_id for update;
  -- Read the time after waiting for any concurrent reservation on this account.
  v_now:=clock_timestamp();
  select coalesce(array_agg(started order by started),'{}'::timestamptz[])
    into v_starts from unnest(v_starts) as started
    where started>v_now-interval '1 hour';
  if cardinality(v_starts)>=20 or exists (
    select 1 from unnest(v_starts) as started where started>v_now-interval '30 seconds'
  ) then
    raise exception using errcode='P0001',message='AI_SESSION_RATE_LIMIT';
  end if;
  update private.ai_session_usage set recent_started_at=array_append(v_starts,v_now)
    where user_id=v_user_id;
  return true;
end;
$$;
revoke all on function public.reserve_ai_session() from public, anon, service_role;
grant execute on function public.reserve_ai_session() to authenticated;

create function public.ai_sensor_snapshot()
returns jsonb language plpgsql stable security definer set search_path = ''
as $$
declare
  v_control public.device_control%rowtype;
  v_sample public.sensor_records%rowtype;
  v_age numeric;
  v_connected boolean := false;
  v_fresh boolean := false;
  v_readings jsonb;
  v_states jsonb;
  v_alerts jsonb := '[]'::jsonb;
begin
  if not public.my_ai_access() then
    raise exception using errcode='P0001',message='AI_ACCESS_DENIED';
  end if;
  select * into v_control from public.device_control where id=1;
  -- Never label readings from a replaced controller as current-controller data.
  select * into v_sample from public.sensor_records
    where controller_id=v_control.active_controller_id
    order by created_at desc,id desc limit 1;
  v_age:=extract(epoch from now()-v_control.last_seen_at);
  v_connected:=coalesce(v_control.esp32_online and
    v_control.active_controller_id is not null and v_age between 0 and 30,false);
  v_fresh:=v_connected and coalesce(v_sample.created_at between now()-interval '30 seconds' and now(),false);
  select jsonb_object_agg(metric.key,jsonb_build_object(
    'clave',metric.key,'label',metric.label,'unidad',metric.unit,'valor',metric.value,
    'simulado',false,'actualizado',v_sample.created_at,
    'vigente',v_fresh and metric.value is not null
  )) into v_readings from (values
    ('temperatura','Temperatura','°C',case when v_sample.temperature between -40 and 85 then v_sample.temperature end),
    ('humedad_ambiente','Humedad ambiental','%',case when v_sample.air_humidity between 0 and 100 then v_sample.air_humidity end),
    ('humedad_suelo','Humedad del suelo','%',case when v_sample.soil_humidity between 0 and 100 then v_sample.soil_humidity end),
    ('luz','Iluminación','lx',case when v_sample.light_lux>=0 and v_sample.light_lux<'Infinity'::float8 then v_sample.light_lux end)
  ) as metric(key,label,unit,value);
  select jsonb_object_agg(state.key,jsonb_build_object(
    'clave',state.key,'label',state.label,'tipo',state.kind,'valor',state.value,
    'actualizado',v_sample.created_at,'vigente',v_fresh and state.value<>'null'::jsonb
  )) into v_states from (values
    ('water_level','Nivel de agua','nivel',coalesce(to_jsonb(v_sample.water_level),'null'::jsonb)),
    ('fan_on','Ventilador','bool',coalesce(to_jsonb(v_sample.fan_on),'null'::jsonb)),
    ('pump_on','Bomba de riego','bool',coalesce(to_jsonb(v_sample.pump_on),'null'::jsonb)),
    ('led_on','Luz LED','bool',coalesce(to_jsonb(v_sample.led_on),'null'::jsonb)),
    ('auto_mode','Modo automático','bool',coalesce(to_jsonb(v_sample.auto_mode),'null'::jsonb)),
    ('fan_power','Potencia del ventilador','porcentaje',coalesce(to_jsonb(v_sample.fan_power),'null'::jsonb)),
    ('led_power','Potencia de la luz LED','porcentaje',coalesce(to_jsonb(v_sample.led_power),'null'::jsonb)),
    ('controller_id','Controlador de la lectura','id',coalesce(to_jsonb(v_sample.controller_id),'null'::jsonb))
  ) as state(key,label,kind,value);
  if not v_connected then
    v_alerts:=v_alerts||jsonb_build_array('Controlador sin conexión reciente; las lecturas disponibles son históricas.');
  end if;
  if v_sample.id is null then
    v_alerts:=v_alerts||jsonb_build_array('No hay lecturas del controlador activo.');
  elsif not v_fresh then
    v_alerts:=v_alerts||jsonb_build_array('La última lectura no está vigente; no describe necesariamente el estado actual.');
  end if;
  if exists(select 1 from jsonb_each(v_readings) as reading where reading.value->'valor'='null'::jsonb) then
    v_alerts:=v_alerts||jsonb_build_array('Uno o más sensores no tienen una lectura válida.');
  end if;
  if v_fresh and v_sample.water_level='low' then
    v_alerts:=v_alerts||jsonb_build_array('El sensor indica nivel de agua bajo.');
  end if;
  return jsonb_build_object(
    'fuente','api','actualizado',v_sample.created_at,'lecturas',v_readings,
    'estado_sistema',v_states,'dispositivo',jsonb_build_object(
      'conectado',v_connected,'ultima_conexion',v_control.last_seen_at,
      'hace_segundos',case when v_age is not null then greatest(0,round(v_age)) end,
      'controller_id',v_control.active_controller_id
    ),'alertas',v_alerts
  );
end;
$$;
revoke all on function public.ai_sensor_snapshot() from public, anon, service_role;
grant execute on function public.ai_sensor_snapshot() to authenticated;

-- Edge Functions only: this helper has no user-facing EXECUTE grant. The
-- named credential is encrypted by Vault and is never included in migrations.
create function public.ai_provider_key()
returns text language sql stable security definer set search_path = ''
as $$
  select secret.decrypted_secret from vault.decrypted_secrets as secret
    where secret.name='ecosphere_gemini_api_key' limit 1;
$$;
revoke all on function public.ai_provider_key() from public, anon, authenticated;
grant execute on function public.ai_provider_key() to service_role;

