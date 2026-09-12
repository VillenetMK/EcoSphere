/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { PGlite } from '@electric-sql/pglite';

const read = name => readFile(new URL(`../../supabase/migrations/${name}`, import.meta.url), 'utf8');
const cima = '367e842b-fd47-4c38-a3fc-c54c47732a9e';
const operator = '00000000-0000-0000-0000-000000000002';
const admin = '00000000-0000-0000-0000-000000000003';
const session = '00000000-0000-0000-0000-000000000004';

// Execute the real PL/pgSQL functions in an isolated PostgreSQL database.
// Fixtures contain no production sessions, telemetry, credentials or network calls.
const fixture = `
  create role anon;
  create role authenticated;
  create role service_role;
  create schema auth;
  create schema private;
  create function auth.jwt() returns jsonb language sql stable as
    $$ select coalesce(nullif(current_setting('request.jwt.claims',true),''),'{}')::jsonb $$;
  create function auth.uid() returns uuid language sql stable as
    $$ select nullif(auth.jwt()->>'sub','')::uuid $$;
  create table auth.users(id uuid primary key,deleted_at timestamptz,is_anonymous boolean,banned_until timestamptz);
  create table auth.sessions(id uuid primary key,user_id uuid,not_after timestamptz,aal text);
  create table private.user_profiles(user_id uuid primary key,username text,status text,role text);
  create table public.device_control(
    id integer primary key,auto_mode boolean,fan_power integer,led_power integer,
    fan_target boolean,led_target boolean,pump_request bigint,pump_duration_ms integer,
    pump_expires_at timestamptz,pump_allow_wet_soil boolean,pump_bypass_sensor_checks boolean,
    active_controller_id bigint,esp32_online boolean,last_seen_at timestamptz
  );
  create table public.sensor_records(
    id bigint primary key,controller_id bigint,soil_humidity double precision,
    water_level text,created_at timestamptz
  );
  create table private.control_audit_log(
    id bigint generated always as identity primary key,
    actor_user_id uuid,actor_username text,actor_role text,
    auto_mode_before boolean,auto_mode_after boolean,
    fan_power_before integer,fan_power_after integer,led_power_before integer,led_power_after integer,
    pump_requested boolean default false,pump_duration_ms integer,
    pump_allow_wet_soil boolean default false,pump_bypass_sensor_checks boolean default false,
    created_at timestamptz default now()
  );
  create table private.manual_watering_permissions(user_id uuid primary key,allow_wet_soil_manual_watering boolean,updated_at timestamptz);
  create table private.ai_permissions(user_id uuid primary key,enabled boolean,updated_at timestamptz);
  create table private.ai_session_usage(user_id uuid primary key,recent_started_at timestamptz[]);
  create function public.my_control_permissions()
    returns table(allow_wet_soil_manual_watering boolean,allow_sensorless_manual_watering boolean)
    language sql as $$ select true,true $$;
  create function public.my_ai_access() returns boolean language sql as $$ select true $$;
  create function public.reserve_ai_session() returns boolean language sql as $$ select true $$;
  create function public.ai_sensor_snapshot() returns jsonb language sql as $$ select '{}'::jsonb $$;
  create function public.ai_provider_key() returns text language sql as $$ select 'fixture-only'::text $$;
  grant usage on schema auth,public to authenticated,anon,service_role;
  grant execute on function public.ai_provider_key() to service_role;
  insert into auth.users(id) values ('${cima}'),('${operator}'),('${admin}');
  insert into auth.sessions values ('${session}','${cima}',null,'aal1');
  insert into private.user_profiles values
    ('${cima}','fixture-cima','approved','operator'),
    ('${operator}','fixture-operator','approved','operator'),
    ('${admin}','fixture-admin','approved','admin');
  insert into public.device_control values (1,false,24,67,true,true,42,3000,now()+interval '15 seconds',true,true,7,true,now());
  insert into public.sensor_records values (1,7,30,'high',now()),(2,7,45,'high',now()-interval '1 day');
  insert into private.control_audit_log(actor_user_id,actor_username,actor_role,pump_requested,created_at)
    values ('${cima}','fixture-cima','operator',true,now()-interval '1 day');
  insert into private.manual_watering_permissions values ('${cima}',true,now());
  insert into private.ai_permissions values ('${cima}',true,now());
  insert into private.ai_session_usage values ('${cima}',array[now()]);
`;

function extractFunction(sql, name) {
  const start = sql.indexOf(`create or replace function ${name}(`);
  assert.notEqual(start, -1, `Missing deployed function ${name}`);
  const end = sql.indexOf('$function$;', sql.indexOf('as $function$', start)) + '$function$;'.length;
  return sql.slice(start, end);
}

test('restauración posterior a Eureka: migración y controles SQL reales', async t => {
  const db = new PGlite();
  try {
    await db.exec(fixture);
    const security = await read('20260903230825_production_security_hardening.sql');
    await db.exec(extractFunction(security, 'private.current_session_is_active'));
    await db.exec(extractFunction(security, 'private.require_admin_aal2'));
    const sensorless = await read('20260911003012_cima_sensorless_manual_pulse.sql');
    const audit = sensorless.slice(sensorless.indexOf('CREATE OR REPLACE FUNCTION private.log_device_control_changes()'));
    await db.exec(audit.slice(0, audit.indexOf('$function$;') + '$function$;'.length));
    await db.exec(`create trigger log_human_device_control_changes after update of auto_mode,fan_power,led_power,pump_request
      on public.device_control for each row execute function private.log_device_control_changes();`);
    const telemetryBefore = (await db.query('select * from public.sensor_records order by id')).rows;
    const auditBefore = (await db.query('select * from private.control_audit_log order by id')).rows;
    await db.exec(await read('20260912180644_restore_normal_operation_after_eureka.sql'));
    await db.exec(`create function public.control_command(p_action text,p_value integer default null)
      returns setof public.device_control language sql security definer set search_path='' as
      $$ select * from private.control_command_impl(p_action,p_value) $$;
      revoke all on function public.control_command(text,integer) from public,anon,service_role;
      grant execute on function public.control_command(text,integer) to authenticated;`);

    const state = async () => (await db.query('select * from public.device_control where id=1')).rows[0];
    const command = (action, value) => db.query('select * from public.control_command($1,$2)', [action, value]);
    const asUser = async (id = cima, aal = 'aal1') => {
      await db.query('update auth.sessions set user_id=$1,aal=$2,not_after=null where id=$3', [id,aal,session]);
      await db.query("select set_config('request.jwt.claims',$1,true)", [JSON.stringify({ sub:id,session_id:session,aal,is_anonymous:false,role:'authenticated' })]);
    };
    const scenario = async fn => {
      await db.exec('begin; update public.sensor_records set created_at=now() where id=1;');
      try { await asUser(); await fn(); }
      finally { await db.exec('rollback;'); }
    };

    await t.test('conserva usuarios, historial, auditoría, potencias e identidad; cancela sólo el pulso excepcional', async () => {
      assert.deepEqual((await db.query('select * from public.sensor_records order by id')).rows, telemetryBefore);
      assert.deepEqual((await db.query('select * from private.control_audit_log order by id')).rows, auditBefore);
      assert.equal((await db.query('select count(*)::int as n from auth.users')).rows[0].n, 3);
      const c = await state();
      assert.equal(c.active_controller_id, 7);
      assert.equal(c.pump_request, 42);
      assert.equal(c.fan_power, 24);
      assert.equal(c.led_power, 67);
      assert.equal(c.pump_expires_at, null);
      assert.equal(c.pump_allow_wet_soil, false);
      assert.equal(c.pump_bypass_sensor_checks, false);
    });

    await t.test('clientes antiguos y permisos reactivados no recuperan los accesos de feria', () => scenario(async () => {
      await db.exec('update private.manual_watering_permissions set allow_wet_soil_manual_watering=true; update private.ai_permissions set enabled=true; set local role authenticated;');
      assert.deepEqual((await db.query('select * from public.my_control_permissions()')).rows,
        [{ allow_wet_soil_manual_watering:false,allow_sensorless_manual_watering:false }]);
      assert.equal((await db.query('select public.my_ai_access() as allowed')).rows[0].allowed, false);
    }));

    await t.test('los RPC de voz y la clave del proveedor quedan inaccesibles', async () => {
      for (const role of ['anon','authenticated','service_role']) {
        for (const fn of ['reserve_ai_session','ai_sensor_snapshot','ai_provider_key']) {
          assert.equal((await db.query('select has_function_privilege($1,$2,\'EXECUTE\') as allowed', [role,`public.${fn}()`])).rows[0].allowed, false);
        }
      }
      assert.equal((await db.query('select count(*)::int as n from private.ai_session_usage')).rows[0].n, 1);
    });

    const denied = [
      ['suelo desconectado','soil_humidity=null',/soil sensor is unavailable/],
      ['suelo húmedo','soil_humidity=75',/60 percent or higher/],
      ['depósito vacío',"water_level='low'",/water level is not sufficient/],
      ['flotador sin lectura','water_level=null',/water level is not sufficient/],
      ['telemetría antigua',"created_at=now()-interval '1 minute'",/current telemetry is unavailable/],
    ];
    for (const [label,assignment,error] of denied) {
      await t.test(`CIMA vuelve a bloquear el riego con ${label}`, () => scenario(async () => {
        await db.exec(`update private.manual_watering_permissions set allow_wet_soil_manual_watering=true;
          update public.sensor_records set ${assignment} where id=1; set local role authenticated;`);
        await assert.rejects(command('pump',3000), error);
      }));
    }

    await t.test('el riego válido se audita, respeta las pausas y no cambia el LED ni el ventilador', () => scenario(async () => {
      await db.exec('set local role authenticated;');
      const c = (await command('pump',3000)).rows[0];
      assert.equal(c.pump_request, 43);
      assert.equal(c.pump_allow_wet_soil, false);
      assert.equal(c.pump_bypass_sensor_checks, false);
      assert.equal(c.led_power, 67);
      assert.equal(c.fan_power, 24);
      await db.exec('reset role;');
      const audit = (await db.query('select * from private.control_audit_log order by id desc limit 1')).rows[0];
      assert.equal(audit.actor_user_id, cima);
      assert.equal(audit.pump_requested, true);
      assert.equal(audit.pump_bypass_sensor_checks, false);
      await assert.rejects(command('pump',3000), /system pump cooldown is active/);
    }));

    await t.test('CIMA también conserva la pausa de sesenta segundos por operador', () => scenario(async () => {
      await db.exec(`insert into private.control_audit_log(actor_user_id,actor_username,actor_role,pump_requested,created_at)
        values ('${cima}','fixture-cima','operator',true,now()-interval '20 seconds');`);
      await assert.rejects(command('pump',3000), /operator pump cooldown is active/);
    }));

    await t.test('los controles normales siguen disponibles para otro operador', () => scenario(async () => {
      await asUser(operator);
      await db.exec('set local role authenticated;');
      const led = (await command('led_power',100)).rows[0];
      assert.equal(led.led_power, 100);
      assert.equal(led.fan_power, 24);
      assert.equal(led.pump_request, 42);
      assert.equal((await command('led_power',100)).rows[0].pump_request, 42);
      assert.equal((await command('fan_power',50)).rows[0].led_power, 100);
    }));

    await t.test('el modo automático bloquea las órdenes manuales', () => scenario(async () => {
      await command('auto_mode',1);
      await assert.rejects(command('pump',3000), /manual watering is disabled in automatic mode/);
    }));

    await t.test('una sesión revocada no puede controlar el sistema', () => scenario(async () => {
      await db.exec('delete from auth.sessions;');
      await assert.rejects(command('led_power',100), /active authenticated session required/);
    }));

    await t.test('el administrador sigue necesitando MFA', () => scenario(async () => {
      await asUser(admin);
      await assert.rejects(command('led_power',100), /active two-factor session required/);
    }));

    await t.test('el administrador con MFA conserva el control', () => scenario(async () => {
      await asUser(admin,'aal2');
      assert.equal((await command('led_power',100)).rows[0].led_power, 100);
    }));
  } finally {
    await db.close();
  }
});
