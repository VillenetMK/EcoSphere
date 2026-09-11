-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.
-- Administrative integration probe. Every fixture, command, credential hash,
-- telemetry row and audit mutation rolls back before returning. No pump command
-- is committed or becomes visible to the physical controller.
do $probe$
declare
  v_cima constant uuid := '367e842b-fd47-4c38-a3fc-c54c47732a9e';
  v_cima_session uuid;
  v_other uuid;
  v_other_session uuid;
  v_cima_claims text;
  v_other_claims text;
  v_claims_before text := current_setting('request.jwt.claims',true);
  v_before jsonb;
  v_after jsonb;
  v_initial bigint;
  v_reply public.device_control%rowtype;
  v_sync record;
  v_controller bigint;
  v_record bigint;
  v_hardware text;
  v_nonce text;
  v_seq bigint;
  v_denied boolean;
begin
  select s.id into v_cima_session from auth.sessions s
  where s.user_id=v_cima and (s.not_after is null or s.not_after>now())
  order by s.created_at desc limit 1;
  select p.user_id,s.id into v_other,v_other_session
  from private.user_profiles p join auth.sessions s on s.user_id=p.user_id
  where p.user_id<>v_cima and p.status='approved' and p.role='operator'
    and (s.not_after is null or s.not_after>now())
  order by s.created_at desc limit 1;
  if v_cima_session is null or v_other_session is null then
    raise exception 'Probe requires active CIMA and ordinary operator sessions';
  end if;
  v_cima_claims:=jsonb_build_object('sub',v_cima,'role','authenticated',
    'session_id',v_cima_session,'aal','aal1','is_anonymous',false)::text;
  v_other_claims:=jsonb_build_object('sub',v_other,'role','authenticated',
    'session_id',v_other_session,'aal','aal1','is_anonymous',false)::text;
  perform set_config('lock_timeout','3s',true);
  -- Follow the production ecosystem -> controller -> control lock order.
  select active_controller_id into strict v_controller
  from private.ecosystems where id=1 for update;
  select hardware_uid into strict v_hardware
  from private.device_controllers where id=v_controller for update;
  select to_jsonb(c),c.pump_request into v_before,v_initial
  from public.device_control c where c.id=1 for update;
  select r.id into strict v_record from public.sensor_records r
  where r.controller_id=v_controller order by r.created_at desc,r.id desc limit 1;

  begin
    perform set_config('request.jwt.claims',v_cima_claims,true);
    update public.device_control set auto_mode=false,pump_expires_at=null,
      pump_allow_wet_soil=false,pump_bypass_sensor_checks=false where id=1;
    insert into private.manual_watering_permissions(user_id,allow_wet_soil_manual_watering)
      values(v_cima,true),(v_other,true)
      on conflict(user_id) do update set allow_wet_soil_manual_watering=true;
    update public.sensor_records set soil_humidity=null,water_level='low',created_at=now()
      where id=v_record;
    delete from private.control_audit_log where created_at>now()-interval '60 seconds';

    if not (select allow_sensorless_manual_watering from public.my_control_permissions()) then
      raise exception 'CIMA capability was not granted';
    end if;
    select * into strict v_reply from public.control_command('pump',3000);
    if v_reply.pump_request<>v_initial+1 or not v_reply.pump_bypass_sensor_checks
       or not v_reply.pump_allow_wet_soil or v_reply.pump_duration_ms<>3000
       or v_reply.pump_expires_at<>now()+interval '15 seconds' then
      raise exception 'CIMA sensor-independent command was not bounded correctly';
    end if;
    select * into strict v_reply from public.control_command('pump',3000);
    if v_reply.pump_request<>v_initial+2 then raise exception 'Consecutive CIMA pulse rejected'; end if;
    if not exists(select 1 from private.control_audit_log where actor_user_id=v_cima
      and pump_requested and pump_bypass_sensor_checks and pump_allow_wet_soil
      and created_at=now()) then raise exception 'Sensor override was not audited'; end if;

    -- Read the response through the real controller wrapper with a rollback-only
    -- credential fixture. Existing live credentials/nonces never leave SQL.
    update private.device_controllers set secret_hash=extensions.digest(repeat('1',64),'sha256')
      where id=v_controller;
    select boot_nonce,max_seq+1 into v_nonce,v_seq from private.controller_boot_sessions
      where controller_id=v_controller and retired_at is null;
    if v_nonce is null then raise exception 'Active nonce required for transport probe'; end if;
    perform set_config('request.jwt.claims','{"role":"service_role"}',true);
    select * into strict v_sync from public.controller_sync(
      p_hardware_uid=>v_hardware,p_device_secret=>repeat('1',64),p_heartbeat_seq=>v_seq,
      p_firmware_version=>'2.1.6+replaceable',p_boot_nonce=>v_nonce);
    if not v_sync.pump_authorized or not v_sync.pump_bypass_sensor_checks
       or not v_sync.pump_allow_wet_soil or v_sync.pump_request<>v_initial+2 then
      raise exception 'Authorized sensor-independent flag was lost in controller transport';
    end if;
    update public.device_control set pump_expires_at=now()-interval '1 second' where id=1;
    select * into strict v_sync from public.controller_sync(
      p_hardware_uid=>v_hardware,p_device_secret=>repeat('1',64),p_heartbeat_seq=>v_seq+1,
      p_firmware_version=>'2.1.6+replaceable',p_boot_nonce=>v_nonce);
    if v_sync.pump_authorized or v_sync.pump_bypass_sensor_checks then
      raise exception 'Expired command retained sensor bypass authorization';
    end if;
    perform set_config('request.jwt.claims',v_cima_claims,true);

    v_denied:=false;
    begin perform public.control_command('pump',4000);
    exception when sqlstate '22023' then
      if sqlerrm<>'CIMA manual watering requires a 3000 ms pulse' then raise; end if;
      v_denied:=true;
    end;
    if not v_denied then raise exception 'CIMA accepted a pulse other than three seconds'; end if;
    v_denied:=false;
    begin perform public.control_command('pump',10001);
    exception when sqlstate '22023' then v_denied:=true; end;
    if not v_denied then raise exception 'Maximum pulse duration failed'; end if;

    perform public.control_command('auto_mode',1);
    if exists(select 1 from public.device_control where id=1 and
      (pump_bypass_sensor_checks or pump_allow_wet_soil or pump_expires_at is not null)) then
      raise exception 'Switching mode retained a pending manual capability';
    end if;
    v_denied:=false;
    begin perform public.control_command('pump',3000);
    exception when sqlstate '55000' then
      if sqlerrm<>'manual watering is disabled in automatic mode' then raise; end if;
      v_denied:=true;
    end;
    if not v_denied then raise exception 'Manual command entered automatic mode'; end if;
    perform public.control_command('auto_mode',0);

    update public.sensor_records set created_at=now()-interval '2 minutes'
      where controller_id=v_controller and created_at>now()-interval '2 minutes';
    v_denied:=false;
    begin perform public.control_command('pump',3000);
    exception when sqlstate '55000' then
      if sqlerrm<>'watering denied: current telemetry is unavailable' then raise; end if;
      v_denied:=true;
    end;
    if not v_denied then raise exception 'Stale telemetry accepted'; end if;
    update public.sensor_records set created_at=now() where id=v_record;

    update private.manual_watering_permissions set allow_wet_soil_manual_watering=false
      where user_id=v_cima;
    if (select allow_sensorless_manual_watering from public.my_control_permissions()) then
      raise exception 'Revoked capability remained visible';
    end if;
    v_denied:=false;
    begin perform public.control_command('pump',3000);
    exception when sqlstate '55000' then
      if sqlerrm<>'watering denied: system pump cooldown is active' then raise; end if;
      v_denied:=true;
    end;
    if not v_denied then raise exception 'Revoked permission did not restore cooldown'; end if;

    -- Ordinary users with wet-soil permission still need valid soil and water.
    perform set_config('request.jwt.claims',v_other_claims,true);
    if (select allow_sensorless_manual_watering from public.my_control_permissions()) then
      raise exception 'Sensor-independent permission leaked to another account';
    end if;
    delete from private.control_audit_log where pump_requested and created_at>now()-interval '60 seconds';
    v_denied:=false;
    begin perform public.control_command('pump',3000);
    exception when sqlstate '55000' then
      if sqlerrm<>'watering denied: soil sensor is unavailable' then raise; end if;
      v_denied:=true;
    end;
    if not v_denied then raise exception 'Ordinary operator bypassed missing soil'; end if;
    update public.sensor_records set soil_humidity=75 where id=v_record;
    v_denied:=false;
    begin perform public.control_command('pump',3000);
    exception when sqlstate '55000' then
      if sqlerrm<>'watering denied: water level is not sufficient' then raise; end if;
      v_denied:=true;
    end;
    if not v_denied then raise exception 'Ordinary operator bypassed low water'; end if;
    update public.sensor_records set water_level='high' where id=v_record;
    select * into strict v_reply from public.control_command('pump',3000);
    if v_reply.pump_bypass_sensor_checks then raise exception 'Ordinary command inherited bypass flag'; end if;
    v_denied:=false;
    begin perform public.control_command('pump',3000);
    exception when sqlstate '55000' then
      if sqlerrm<>'watering denied: system pump cooldown is active' then raise; end if;
      v_denied:=true;
    end;
    if not v_denied then raise exception 'Ordinary system cooldown removed'; end if;
    update private.control_audit_log set created_at=now()-interval '20 seconds'
      where actor_user_id=v_other and pump_requested and created_at=now();
    v_denied:=false;
    begin perform public.control_command('pump',3000);
    exception when sqlstate '55000' then
      if sqlerrm<>'watering denied: operator pump cooldown is active' then raise; end if;
      v_denied:=true;
    end;
    if not v_denied then raise exception 'Ordinary operator cooldown removed'; end if;

    perform set_config('request.jwt.claims','{}',true);
    v_denied:=false;
    begin perform public.control_command('pump',3000);
    exception when sqlstate '42501' then v_denied:=true; end;
    if not v_denied then raise exception 'Unauthenticated command accepted'; end if;
    -- Preserve the existing explicit gateway cutover setting. Before cutover,
    -- a valid controller secret/nonce is allowed over the direct transport.
    if (select controller_edge_gateway_required from private.ecosystems where id=1) then
      v_denied:=false;
      begin
        perform public.controller_sync(p_hardware_uid=>v_hardware,p_device_secret=>repeat('1',64),
          p_heartbeat_seq=>v_seq+2,p_firmware_version=>'2.1.6+replaceable',p_boot_nonce=>v_nonce);
      exception when sqlstate '42501' then
        if sqlerrm<>'controller edge gateway required' then raise; end if;
        v_denied:=true;
      end;
      if not v_denied then raise exception 'Direct controller call bypassed service-role gateway'; end if;
    end if;
    if (select allow_sensorless_manual_watering from public.my_control_permissions()) then
      raise exception 'Unauthenticated sensor-independent capability';
    end if;
    if has_table_privilege('authenticated','public.device_control','UPDATE')
      or has_table_privilege('authenticated','private.manual_watering_permissions','UPDATE')
      or has_function_privilege('anon','public.my_control_permissions()','EXECUTE')
      or has_function_privilege('authenticated','private.control_command_impl(text,integer)','EXECUTE') then
      raise exception 'Control capability ACL regression';
    end if;
    raise exception using errcode='Z0001',message='Rollback all probe writes';
  exception when sqlstate 'Z0001' then null;
  end;
  perform set_config('request.jwt.claims',coalesce(v_claims_before,''),true);
  select to_jsonb(c) into v_after from public.device_control c where c.id=1;
  if v_after is distinct from v_before then raise exception 'Probe left a control mutation'; end if;
end;
$probe$;
