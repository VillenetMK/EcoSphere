import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('todos los clientes envían controles mediante el RPC seguro', async () => {
  const web = await readFile(new URL('../app.js', import.meta.url), 'utf8');
  const androidApi = await readFile(
    new URL('../../app/src/main/java/com/example/ecosphere/data/network/SupabaseApi.kt', import.meta.url),
    'utf8',
  );
  const androidRepository = await readFile(
    new URL('../../app/src/main/java/com/example/ecosphere/data/repository/SensorRepository.kt', import.meta.url),
    'utf8',
  );
  const desktop = await readFile(
    new URL('../../desktopApp/src/main/kotlin/com/example/ecosphere/desktop/Main.kt', import.meta.url),
    'utf8',
  );
  const combined = `${web}\n${androidApi}\n${androidRepository}\n${desktop}`;

  assert.match(web, /apiPost\('rest\/v1\/rpc\/control_command'/);
  assert.match(androidApi, /@POST\("rest\/v1\/rpc\/control_command"\)/);
  assert.match(desktop, /requestBuilder\("rest\/v1\/rpc\/control_command"\)/);
  assert.doesNotMatch(combined, /apiPatch\(|@PATCH\(|\.method\("PATCH"|updateDeviceControl\(|patchControl\(/);

  for (const action of ['auto_mode', 'fan_power', 'led_power', 'pump']) {
    assert.match(combined, new RegExp(`["']${action}["']`));
  }

  assert.match(combined, /["']p_action["']/);
  assert.match(combined, /["']p_value["']/);
  assert.doesNotMatch(web, /acceso de sólo lectura/);
});

test('el RPC tolera ajustes humanos rápidos sin retirar las protecciones de riego', async () => {
  const migration = await readFile(
    new URL(
      '../../supabase/migrations/20260904035138_relax_interactive_control_rate_limit.sql',
      import.meta.url,
    ),
    'utf8',
  );

  assert.match(migration, /Replaying the current set-point is a successful idempotent request/);
  assert.match(migration, /offset 29[\s\S]*offset 9/);
  assert.match(migration, /'status', 429/);
  assert.match(migration, /ECOSPHERE_CONTROL_RATE_LIMIT/);
  assert.doesNotMatch(migration, /system command cooldown is active/);
  assert.doesNotMatch(migration, /operator command cooldown is active/);
  assert.match(migration, /system pump cooldown is active/);
  assert.match(migration, /operator pump cooldown is active/);
  assert.match(migration, /v_telemetry_at < now\(\) - interval '30 seconds'/);
});
