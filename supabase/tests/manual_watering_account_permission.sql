-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.
-- All fixtures, permissions and pump requests are rolled back. No command is
-- visible to the physical controller. Run only with the database owner role.
begin;
set local statement_timeout = '15s';
set local lock_timeout = '5s';
create temporary table watering_test_results (test text, passed boolean) on commit drop;
create function pg_temp.expect_rejection(test_name text, expected_message text, duration integer default 3000)
returns void language plpgsql as $$
begin
  begin
    perform public.control_command('pump', duration);
    raise exception 'TEST FAILURE: % unexpectedly accepted', test_name;
  exception when others then
    if sqlerrm <> expected_message then raise; end if;
  end;
  insert into watering_test_results values(test_name,true);
end;
$$;
do $tests$
declare
  operator_id uuid;
  session_id uuid;
  record_id bigint;
  command public.device_control%rowtype;
  claims text;
begin
  select p.user_id,s.id into strict operator_id,session_id
  from private.user_profiles p join auth.sessions s on s.user_id=p.user_id
  where p.status='approved' and p.role='operator'
    and p.user_id<>'367e842b-fd47-4c38-a3fc-c54c47732a9e'::uuid
    and (s.not_after is null or s.not_after>now())
  order by s.created_at desc limit 1;
  perform 1 from public.device_control where id=1 for update;
  select id into strict record_id from public.sensor_records
  where controller_id=(select active_controller_id from public.device_control where id=1)
  order by created_at desc,id desc limit 1;
  claims:=jsonb_build_object('sub',operator_id,'role','authenticated','session_id',session_id,'aal','aal1','is_anonymous',false)::text;
  perform set_config('request.jwt.claims',claims,true);
  update public.device_control set auto_mode=false,pump_expires_at=null,pump_allow_wet_soil=false where id=1;
  delete from private.control_audit_log where pump_requested and created_at>now()-interval '60 seconds';
  update public.sensor_records set soil_humidity=75,water_level='high',created_at=now() where id=record_id;
  delete from private.manual_watering_permissions where user_id=operator_id;
  if (select allow_wet_soil_manual_watering from public.my_control_permissions()) then
    raise exception 'TEST FAILURE: default permission enabled';
  end if;
  insert into watering_test_results values('default permission denied',true);
  perform pg_temp.expect_rejection('ordinary operator blocked on wet soil','watering denied: soil humidity is already 60 percent or higher');
  insert into private.manual_watering_permissions(user_id,allow_wet_soil_manual_watering) values(operator_id,true);
  if not (select allow_wet_soil_manual_watering from public.my_control_permissions()) then
    raise exception 'TEST FAILURE: explicit permission unavailable';
  end if;
  select * into command from public.control_command('pump',3000);
  if not command.pump_allow_wet_soil or command.pump_duration_ms<>3000 or command.pump_expires_at is null then
    raise exception 'TEST FAILURE: authorized wet command malformed';
  end if;
  if not exists(select 1 from private.control_audit_log where actor_user_id=operator_id and pump_requested and pump_allow_wet_soil and created_at=now()) then
    raise exception 'TEST FAILURE: wet command not audited';
  end if;
  insert into watering_test_results values('wet soil allowed with bounded audited command',true);
  perform pg_temp.expect_rejection('system cooldown retained','watering denied: system pump cooldown is active');
  update private.control_audit_log set created_at=now()-interval '20 seconds' where pump_requested and created_at=now();
  perform pg_temp.expect_rejection('operator cooldown retained','watering denied: operator pump cooldown is active');
  delete from private.control_audit_log where pump_requested and created_at>now()-interval '60 seconds';
  update public.sensor_records set water_level='low' where id=record_id;
  perform pg_temp.expect_rejection('wet permission still requires water','watering denied: water level is not sufficient');
  update public.sensor_records set water_level='high',soil_humidity=null where id=record_id;
  perform pg_temp.expect_rejection('wet permission still requires soil sensor','watering denied: soil sensor is unavailable');
  update public.sensor_records set soil_humidity=75,created_at=now()-interval '1 minute' where id=record_id;
  perform pg_temp.expect_rejection('wet permission still requires fresh telemetry','watering denied: current telemetry is unavailable');
  update public.sensor_records set created_at=now() where id=record_id;
  update public.device_control set auto_mode=true where id=1;
  perform pg_temp.expect_rejection('wet permission still requires manual mode','manual watering is disabled in automatic mode');
  update public.device_control set auto_mode=false where id=1;
  perform pg_temp.expect_rejection('maximum pulse retained','pump duration must be between 500 and 10000 ms',10001);
  update private.manual_watering_permissions set allow_wet_soil_manual_watering=false where user_id=operator_id;
  perform pg_temp.expect_rejection('revoked permission effective immediately','watering denied: soil humidity is already 60 percent or higher');
  update public.sensor_records set soil_humidity=40 where id=record_id;
  select * into command from public.control_command('pump',3000);
  if command.pump_allow_wet_soil then raise exception 'TEST FAILURE: ordinary command inherited override'; end if;
  insert into watering_test_results values('ordinary dry command resets override',true);
  perform set_config('request.jwt.claims','{}',true);
  perform pg_temp.expect_rejection('unauthenticated request denied','active authenticated session required');
  if (select allow_wet_soil_manual_watering from public.my_control_permissions()) then raise exception 'TEST FAILURE: unauthenticated capability'; end if;
  insert into watering_test_results values('unauthenticated capability false',true);
  if has_function_privilege('anon','public.my_control_permissions()','EXECUTE')
     or has_table_privilege('authenticated','private.manual_watering_permissions','UPDATE')
     or has_table_privilege('anon','private.manual_watering_permissions','SELECT') then
    raise exception 'TEST FAILURE: permission ACL too broad';
  end if;
  insert into watering_test_results values('permission ACL is restricted',true);
end;
$tests$;
select jsonb_agg(to_jsonb(result)) as tests from watering_test_results result;
rollback;
