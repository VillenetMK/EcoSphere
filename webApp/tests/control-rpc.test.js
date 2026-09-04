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
