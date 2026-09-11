-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.
--
-- Administrative integration probe. All control, audit, permission and sensor
-- writes are rolled back inside a subtransaction before this command returns.
-- No pump command is committed or made visible to the ESP32.
do $probe$
declare
  v_user uuid := '367e842b-fd47-4c38-a3fc-c54c47732a9e';
  v_session uuid;
  v_before jsonb;
  v_after jsonb;
  v_initial bigint;
  v_reply public.device_control%rowtype;
  v_record bigint;
  v_denied boolean;
begin
  select s.id into v_session from auth.sessions s
  where s.user_id = v_user and (s.not_after is null or s.not_after > now())
  order by s.created_at desc limit 1;
  if v_session is null then raise exception 'An active CIMA session is required for this probe'; end if;

  perform set_config('lock_timeout', '3s', true);
  perform set_config('request.jwt.claims', jsonb_build_object(
    'sub',v_user,'role','authenticated','session_id',v_session,
    'aal','aal1','is_anonymous',false)::text,true);
  if not private.current_session_is_active(false) then
    raise exception 'CIMA session validation failed';
  end if;

  select to_jsonb(c),c.pump_request into v_before,v_initial
  from public.device_control c where c.id=1 for update;

  begin
    select * into strict v_reply from public.control_command('pump',3000);
    if v_reply.pump_request <> v_initial+1 then raise exception 'First pulse was not accepted'; end if;
    select * into strict v_reply from public.control_command('pump',3000);
    if v_reply.pump_request <> v_initial+2 then raise exception 'Consecutive pulse was not accepted'; end if;

    update private.manual_watering_permissions
    set allow_wet_soil_manual_watering=false where user_id=v_user;
    v_denied := false;
    begin
      perform public.control_command('pump',3000);
    exception when sqlstate '55000' then
      if sqlerrm <> 'watering denied: system pump cooldown is active' then raise; end if;
      v_denied := true;
    end;
    if not v_denied then raise exception 'Revoked permission did not restore the cooldown'; end if;
    update private.manual_watering_permissions
    set allow_wet_soil_manual_watering=true where user_id=v_user;

    select r.id into v_record from public.sensor_records r
    where r.controller_id=(select active_controller_id from public.device_control where id=1)
    order by r.created_at desc,r.id desc limit 1;
    update public.sensor_records set water_level='low' where id=v_record;
    v_denied := false;
    begin
      perform public.control_command('pump',3000);
    exception when sqlstate '55000' then
      if sqlerrm <> 'watering denied: water level is not sufficient' then raise; end if;
      v_denied := true;
    end;
    if not v_denied then raise exception 'No-water protection failed'; end if;

    v_denied := false;
    begin
      perform public.control_command('pump',10001);
    exception when sqlstate '22023' then
      if sqlerrm <> 'pump duration must be between 500 and 10000 ms' then raise; end if;
      v_denied := true;
    end;
    if not v_denied then raise exception 'Maximum pulse duration protection failed'; end if;

    raise exception using errcode='Z0001',message='Rollback all probe writes';
  exception when sqlstate 'Z0001' then
    null;
  end;

  select to_jsonb(c) into v_after from public.device_control c where c.id=1;
  if v_after is distinct from v_before then raise exception 'Probe left a control mutation'; end if;
end;
$probe$;
