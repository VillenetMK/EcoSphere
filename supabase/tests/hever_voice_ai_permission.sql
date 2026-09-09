-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.
-- Owner-only rollback tests. Synthetic auth fixtures cannot persist or sign in;
-- no audio, provider call or physical device command is issued.
begin;
set local statement_timeout='20s';
set local lock_timeout='5s';
create temporary table ai_test_results(test text,passed boolean) on commit drop;
create temporary table ai_test_context(claims text) on commit drop;
grant insert on ai_test_results to authenticated,anon;
grant select on ai_test_context to authenticated;

create function pg_temp.ai_assert(test_name text, result boolean)
returns void language plpgsql as $$
begin
  if result is distinct from true then raise exception 'TEST FAILURE: %',test_name; end if;
  insert into ai_test_results values(test_name,true);
end;
$$;
grant execute on function pg_temp.ai_assert(text,boolean) to authenticated,anon;
create function pg_temp.ai_rejected(test_name text, action text, expected text)
returns void language plpgsql as $$
begin
  begin
    if action='snapshot' then perform public.ai_sensor_snapshot();
    elsif action='session' then perform public.reserve_ai_session();
    else raise exception 'Unknown test action'; end if;
    raise exception 'TEST FAILURE: % unexpectedly allowed',test_name;
  exception when sqlstate 'P0001' then
    if sqlerrm<>expected then raise; end if;
  end;
  insert into ai_test_results values(test_name,true);
end;
$$;

do $tests$
declare
  allowed_id uuid:=gen_random_uuid();
  other_id uuid:=gen_random_uuid();
  allowed_session uuid:=gen_random_uuid();
  other_session uuid:=gen_random_uuid();
  allowed_claims text;
  other_claims text;
  snapshot jsonb;
  fixture_id bigint;
  active_id bigint;
begin
  insert into auth.users(id,email,raw_app_meta_data,is_anonymous) values
    (allowed_id,'ai-permitted-'||allowed_id||'@example.invalid','{}',false),
    (other_id,'ai-denied-'||other_id||'@example.invalid','{}',false);
  insert into private.user_profiles(user_id,full_name,first_name,last_name,email,
    registration_method,status,role,username) values
    (allowed_id,'Prueba Permitida','Prueba','Permitida','ai-permitted-'||allowed_id||'@example.invalid',
      'google','approved','operator','test.ai.'||left(replace(allowed_id::text,'-',''),20)),
    (other_id,'Prueba Restringida','Prueba','Restringida','ai-denied-'||other_id||'@example.invalid',
      'google','approved','operator','test.ai.'||left(replace(other_id::text,'-',''),20));
  insert into auth.sessions(id,user_id,created_at,updated_at,aal,not_after) values
    (allowed_session,allowed_id,now(),now(),'aal1',now()+interval '1 hour'),
    (other_session,other_id,now(),now(),'aal1',now()+interval '1 hour');
  allowed_claims:=jsonb_build_object('sub',allowed_id,'role','authenticated','session_id',allowed_session,'aal','aal1','is_anonymous',false)::text;
  other_claims:=jsonb_build_object('sub',other_id,'role','authenticated','session_id',other_session,'aal','aal1','is_anonymous',false)::text;
  insert into ai_test_context values(allowed_claims);
  perform set_config('request.jwt.claims',allowed_claims,true);
  perform pg_temp.ai_assert('approved account has no implicit AI access',not public.my_ai_access());
  insert into private.ai_permissions(user_id,enabled) values(allowed_id,true);
  perform pg_temp.ai_assert('explicit account permission permits active approved operator',public.my_ai_access());
  perform pg_temp.ai_assert('first AI session can be reserved',public.reserve_ai_session());
  perform pg_temp.ai_rejected('immediate second session is rate limited','session','AI_SESSION_RATE_LIMIT');
  update private.ai_session_usage set recent_started_at=array(select now()-i*interval '1 minute' from generate_series(1,20) as i)
    where user_id=allowed_id;
  perform pg_temp.ai_rejected('twenty sessions within rolling hour block another','session','AI_SESSION_RATE_LIMIT');
  update private.ai_session_usage set recent_started_at=array[ now()-interval '61 minutes' ] where user_id=allowed_id;
  perform pg_temp.ai_assert('expired reservation window permits another session',public.reserve_ai_session());
  perform pg_temp.ai_assert('old session timestamps are discarded',(select cardinality(recent_started_at)=1 from private.ai_session_usage where user_id=allowed_id));

  perform set_config('request.jwt.claims',other_claims,true);
  perform pg_temp.ai_assert('another approved operator cannot use AI',not public.my_ai_access());
  perform pg_temp.ai_rejected('another operator cannot read AI snapshot','snapshot','AI_ACCESS_DENIED');
  perform pg_temp.ai_rejected('another operator cannot reserve provider session','session','AI_ACCESS_DENIED');
  perform set_config('request.jwt.claims',allowed_claims,true);
  update private.user_profiles set status='blocked' where user_id=allowed_id;
  perform pg_temp.ai_assert('blocked account permission is ineffective',not public.my_ai_access());
  perform pg_temp.ai_rejected('blocked account snapshot denied','snapshot','AI_ACCESS_DENIED');
  update private.user_profiles set status='pending' where user_id=allowed_id;
  perform pg_temp.ai_assert('pending account permission is ineffective',not public.my_ai_access());
  update private.user_profiles set status='approved',role='admin' where user_id=allowed_id;
  perform pg_temp.ai_assert('admin still needs verified MFA',not public.my_ai_access());
  perform set_config('request.jwt.claims',(allowed_claims::jsonb||'{"aal":"aal2"}')::text,true);
  perform pg_temp.ai_assert('claim alone cannot forge verified MFA session',not public.my_ai_access());
  update auth.sessions set aal='aal2' where id=allowed_session;
  perform pg_temp.ai_assert('explicitly permitted verified admin can use AI',public.my_ai_access());
  update private.user_profiles set role='operator' where user_id=allowed_id;
  update auth.sessions set aal='aal1' where id=allowed_session;
  perform set_config('request.jwt.claims',(allowed_claims::jsonb||'{"is_anonymous":true}')::text,true);
  perform pg_temp.ai_assert('anonymous identity cannot use permission',not public.my_ai_access());
  perform set_config('request.jwt.claims',allowed_claims,true);
  update auth.users set banned_until=now()+interval '1 hour' where id=allowed_id;
  perform pg_temp.ai_assert('banned identity cannot use permission',not public.my_ai_access());
  update auth.users set banned_until=null where id=allowed_id;
  update auth.sessions set not_after=now()-interval '1 second' where id=allowed_session;
  perform pg_temp.ai_assert('expired session cannot use permission',not public.my_ai_access());
  update auth.sessions set not_after=now()+interval '1 hour' where id=allowed_session;
  perform set_config('request.jwt.claims',(allowed_claims::jsonb||jsonb_build_object('session_id',gen_random_uuid()))::text,true);
  perform pg_temp.ai_assert('revoked or invented session cannot use permission',not public.my_ai_access());
  perform set_config('request.jwt.claims',allowed_claims,true);
  update private.ai_permissions set enabled=false where user_id=allowed_id;
  perform pg_temp.ai_rejected('permission revocation is immediate','snapshot','AI_ACCESS_DENIED');
  update private.ai_permissions set enabled=true where user_id=allowed_id;

  select active_controller_id into strict active_id from public.device_control where id=1 for update;
  if active_id is null then raise exception 'TEST FIXTURE: requires an active controller'; end if;
  select least(coalesce(min(id),0),0)-2 into fixture_id from public.sensor_records;
  insert into public.sensor_records(id,controller_id,created_at,temperature,air_humidity,soil_humidity,light_lux,
    water_level,fan_on,pump_on,led_on,auto_mode,fan_power,led_power) values
    (fixture_id,active_id,now(),23.5,62,70,null,'high',true,false,false,true,50,0),
    (fixture_id+1,null,now()+interval '1 second',35,90,95,1000,'low',false,true,true,false,0,100);
  update public.device_control set esp32_online=true,last_seen_at=now() where id=1;
  snapshot:=public.ai_sensor_snapshot();
  perform pg_temp.ai_assert('snapshot uses real current-controller telemetry',snapshot#>>'{lecturas,temperatura,valor}'='23.5' and snapshot->>'fuente'='api');
  perform pg_temp.ai_assert('newer orphan/replaced-controller sample excluded',snapshot#>>'{estado_sistema,pump_on,valor}'='false');
  perform pg_temp.ai_assert('fresh reading marked current',snapshot#>>'{lecturas,temperatura,vigente}'='true' and snapshot#>>'{dispositivo,conectado}'='true');
  perform pg_temp.ai_assert('missing sensor remains null and not current',snapshot#>'{lecturas,luz,valor}'='null'::jsonb and snapshot#>>'{lecturas,luz,vigente}'='false');
  update public.device_control set last_seen_at=now()-interval '1 minute' where id=1;
  snapshot:=public.ai_sensor_snapshot();
  perform pg_temp.ai_assert('disconnected controller data is explicitly historical',snapshot#>>'{lecturas,temperatura,vigente}'='false' and snapshot#>>'{lecturas,temperatura,valor}'='23.5' and snapshot#>>'{dispositivo,conectado}'='false');
  update public.device_control set active_controller_id=null,last_seen_at=now() where id=1;
  snapshot:=public.ai_sensor_snapshot();
  perform pg_temp.ai_assert('no active controller cannot expose previous-controller readings',snapshot#>'{lecturas,temperatura,valor}'='null'::jsonb and snapshot#>>'{dispositivo,conectado}'='false');
  perform set_config('request.jwt.claims','{}',true);
  perform pg_temp.ai_assert('no authenticated session has no AI capability',not public.my_ai_access());
  perform pg_temp.ai_rejected('no session cannot read AI snapshot','snapshot','AI_ACCESS_DENIED');
  perform pg_temp.ai_rejected('no session cannot reserve AI session','session','AI_ACCESS_DENIED');
  perform set_config('request.jwt.claims',allowed_claims,true);
end;
$tests$;

set local role authenticated;
do $acl$
begin
  perform pg_temp.ai_assert('authenticated allowed identity can call capability RPC',public.my_ai_access());
  begin
    perform public.ai_provider_key();
    raise exception 'TEST FAILURE: client could obtain provider key';
  exception when insufficient_privilege then
    insert into ai_test_results values('authenticated cannot execute provider-key helper',true);
  end;
  begin
    perform 1 from private.ai_permissions;
    raise exception 'TEST FAILURE: client could read private permissions';
  exception when insufficient_privilege then
    insert into ai_test_results values('authenticated cannot access private permissions',true);
  end;
  begin
    delete from private.ai_session_usage;
    raise exception 'TEST FAILURE: client could clear quota';
  exception when insufficient_privilege then
    insert into ai_test_results values('authenticated cannot reset private session quota',true);
  end;
end;
$acl$;
reset role;
set local role anon;
do $anon$
begin
  begin
    perform public.my_ai_access();
    raise exception 'TEST FAILURE: anon capability EXECUTE grant';
  exception when insufficient_privilege then
    insert into ai_test_results values('anonymous role cannot execute capability RPC',true);
  end;
  begin
    perform public.ai_provider_key();
    raise exception 'TEST FAILURE: anon provider-key EXECUTE grant';
  exception when insufficient_privilege then
    insert into ai_test_results values('anonymous role cannot execute provider-key helper',true);
  end;
end;
$anon$;
reset role;
select jsonb_agg(to_jsonb(result)) as tests from ai_test_results result;
rollback;
