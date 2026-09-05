import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../../', import.meta.url);

test('el reemplazo de ESP32 conserva un único sistema y sólo un controlador activo', async () => {
  const migration = await readFile(
    new URL('supabase/migrations/20260824143525_replaceable_esp32_controllers.sql', root),
    'utf8',
  );
  assert.match(migration, /device_controllers_one_active_per_ecosystem/);
  assert.match(migration, /where status = 'active'/);
  assert.match(migration, /set status = 'standby'/);
  assert.match(migration, /update public\.device_control/);
  assert.doesNotMatch(migration, /delete from public\.sensor_records/);
});

test('la identidad del controlador no depende del firmware copiado', async () => {
  const firmware = await readFile(
    new URL('firmware/replaceable-controller/EcoSphereControllerClient.h', root),
    'utf8',
  );
  assert.match(firmware, /ESP\.getEfuseMac\(\)/);
  assert.match(firmware, /esp_fill_random/);
  assert.match(firmware, /Preferences/);
  assert.match(firmware, /pairingClaimProof\(\)/);
  assert.match(firmware, /ecosphere-pairing-v1:/);
  assert.match(firmware, /mbedtls_md\(/);
  assert.match(firmware, /encodeHexUpper\(digest, 12, pairingClaimProof_\)/);
  assert.doesNotMatch(firmware, /\b(?:static\s+)?(?:const|constexpr)\s+char\s+HEX\s*\[/);
  assert.doesNotMatch(firmware, /service_role/i);
  assert.doesNotMatch(firmware, /setInsecure/);
});

test('la vinculación aparece en diagnóstico y exige administrador', async () => {
  const [html, app] = await Promise.all([
    readFile(new URL('webApp/index.html', root), 'utf8'),
    readFile(new URL('webApp/app.js', root), 'utf8'),
  ]);
  assert.match(html, /id="controllerPairingPanel"[^>]*hidden/);
  assert.match(html, /id="controllerHardwareUid"/);
  assert.match(html, /id="controllerClaimProof"/);
  assert.match(app, /currentProfile\?\.role === 'admin'/);
  assert.match(app, /rpc\/controller_open_pairing_window/);
  assert.match(app, /p_expected_hardware_uid: hardwareUid/);
  assert.match(app, /p_expected_claim_proof: claimProof/);
  assert.match(app, /p_minutes: 2/);
  assert.match(app, /rpc\/replace_active_controller/);
});
