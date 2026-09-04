/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const read = relative => readFile(new URL(relative, root), 'utf8');

test('Android no respalda sesiones ni datos pendientes de registro', async () => {
  const [manifest, backup, extraction, authUi] = await Promise.all([
    read('app/src/main/AndroidManifest.xml'),
    read('app/src/main/res/xml/backup_rules.xml'),
    read('app/src/main/res/xml/data_extraction_rules.xml'),
    read('app/src/main/java/com/example/ecosphere/ui/mobile/MobileAuthScreen.kt'),
  ]);

  assert.match(manifest, /android:allowBackup="false"/);
  assert.match(manifest, /android:usesCleartextTraffic="false"/);
  assert.match(manifest, /android:fullBackupContent="@xml\/backup_rules"/);
  assert.match(manifest, /android:dataExtractionRules="@xml\/data_extraction_rules"/);
  assert.match(backup, /exclude domain="sharedpref" path="\."/);
  assert.equal((extraction.match(/exclude domain="sharedpref" path="\."/g) || []).length, 2);
  assert.doesNotMatch(authUi, /password by rememberSaveable|code by rememberSaveable/);
  assert.match(await read('app/src/main/java/com/example/ecosphere/MainActivity.kt'), /FLAG_SECURE/);
});

test('los borradores con DNI y teléfono vencen en todas las plataformas', async () => {
  const [shared, web, android, desktop] = await Promise.all([
    read('sharedCore/src/main/kotlin/com/example/ecosphere/shared/AuthContract.kt'),
    read('webApp/auth.js'),
    read('app/src/main/java/com/example/ecosphere/auth/NativeAuthViewModel.kt'),
    read('desktopApp/src/main/kotlin/com/example/ecosphere/desktop/DesktopAuthController.kt'),
  ]);
  assert.match(shared, /REGISTRATION_DRAFT_TTL_MS = 24L \* 60L \* 60L \* 1_000L/);
  assert.match(web, /savedAtEpochMs: Date\.now\(\)/);
  assert.match(web, /age > 24 \* 60 \* 60 \* 1000/);
  assert.match(android, /AuthValidation\.isRegistrationDraftCurrent\(draft\)/);
  assert.match(desktop, /AuthValidation\.isRegistrationDraftCurrent\(draft\)/);
  assert.match(desktop, /val activeSession = supabase\.auth\.currentSessionOrNull\(\)/);
  assert.match(desktop, /completeProfile\(identity\.draft\)/);
});

test('un operador no invoca el RPC administrativo al abrir diagnóstico', async () => {
  const mobile = await read('app/src/main/java/com/example/ecosphere/ui/mobile/MobileEcoSphereApp.kt');
  assert.match(
    mobile,
    /MobileDestination\.DIAGNOSTICS -> if \(profileRole == "admin"\) onRefreshController\(\)/,
  );
});

test('las interfaces no restauran el rol retirado de sólo lectura', async () => {
  const sources = await Promise.all([
    read('webApp/app.js'),
    read('app/src/main/java/com/example/ecosphere/ui/mobile/MobileEcoSphereApp.kt'),
    read('desktopApp/src/main/kotlin/com/example/ecosphere/desktop/Main.kt'),
  ]);
  for (const source of sources) assert.doesNotMatch(source, /Sólo lectura|Visualizador/);
});

test('el firmware usa el gateway estricto y un nonce nuevo por arranque', async () => {
  const firmware = await read('firmware/replaceable-controller/EcoSphereControllerClient.h');
  assert.match(firmware, /\/functions\/v1\/controller-gateway/);
  assert.match(firmware, /payload\["operation"\] = "begin_pairing"/);
  assert.match(firmware, /payload\["operation"\] = "sync"/);
  assert.match(firmware, /payload\["p_boot_nonce"\] = bootNonceHex_/);
  assert.match(firmware, /esp_fill_random\(bootNonce_, sizeof\(bootNonce_\)\)/);
  assert.doesNotMatch(firmware, /\/rest\/v1\/rpc\/controller_/);
  assert.doesNotMatch(firmware, /addHeader\("Authorization"|publishableKey_/);
});

test('todas las aplicaciones implementan la autorización física previa al reemplazo', async () => {
  const [web, androidApi, androidRepository, androidUi, desktop] = await Promise.all([
    read('webApp/app.js'),
    read('app/src/main/java/com/example/ecosphere/data/network/SupabaseApi.kt'),
    read('app/src/main/java/com/example/ecosphere/data/repository/SensorRepository.kt'),
    read('app/src/main/java/com/example/ecosphere/ui/screens/DiagnosticsScreen.kt'),
    read('desktopApp/src/main/kotlin/com/example/ecosphere/desktop/Main.kt'),
  ]);
  for (const source of [web, `${androidApi}\n${androidRepository}`, desktop]) {
    assert.match(source, /controller_open_pairing_window/);
    assert.match(source, /p_expected_hardware_uid/);
    assert.match(source, /p_expected_claim_proof/);
  }
  assert.match(androidUi, /onAuthorizeController/);
  assert.match(androidUi, /Prueba EcoSphere/);
});

test('las funciones Edge declaran explícitamente su autenticación personalizada', async () => {
  const config = await read('supabase/config.toml');
  for (const slug of ['username-login', 'controller-gateway', 'controller-credentials']) {
    assert.match(config, new RegExp(`\\[functions\\.${slug}\\]\\nverify_jwt = false`));
  }
});

test('el acceso por usuario no confía en cabeceras IP falsificables', async () => {
  const edge = await read('supabase/functions/username-login/index.ts');
  assert.match(edge, /split\(","\)[\s\S]*\.at\(-1\)/);
  assert.doesNotMatch(edge, /request\.headers\.get\("(?:cf-connecting-ip|x-real-ip)"\)/);
  assert.match(edge, /candidate\.length <= 64/);
});

test('el historial de escritorio no descarga 200 filas cada dos segundos', async () => {
  const desktop = await read('desktopApp/src/main/kotlin/com/example/ecosphere/desktop/Main.kt');
  assert.match(desktop, /HISTORY_REFRESH_MS = 30_000L/);
  assert.match(desktop, /delay\(DASHBOARD_REFRESH_MS\)\s+refresh\(\)/);
  assert.match(desktop, /delay\(HISTORY_REFRESH_MS\)\s+refreshHistory\(\)/);
  assert.doesNotMatch(desktop, /refresh\(destination == Destination\.HISTORY\)/);
  assert.doesNotMatch(desktop, /HTTP \$\{res\.statusCode\(\)\}: \$\{res\.body\(\)\}/);
});

test('Android cancela la carga histórica anterior al cambiar de mes', async () => {
  const viewModel = await read('app/src/main/java/com/example/ecosphere/ui/viewmodel/EcoSphereViewModel.kt');
  assert.match(viewModel, /private var historyJob: Job\? = null/);
  assert.ok((viewModel.match(/historyJob\?\.cancel\(\)/g) || []).length >= 3);
});

test('el service worker sólo almacena GET válidos y no responde HTML a recursos', async () => {
  const worker = await read('webApp/sw.js');
  assert.match(worker, /event\.request\.method !== 'GET'/);
  assert.match(worker, /response\.ok && response\.type === 'basic'/);
  assert.match(worker, /!url\.search && CACHEABLE_URLS\.has\(url\.href\)/);
  assert.match(worker, /event\.request\.mode === 'navigate'/);
  assert.match(worker, /return Response\.error\(\)/);
});

test('el repositorio conserva las versiones exactas de las migraciones desplegadas', async () => {
  const expected = [
    '20260821000000_initial_public_schema_baseline.sql',
    '20260824143525_replaceable_esp32_controllers.sql',
    '20260824143627_fix_controller_pairing_encoding.sql',
    '20260824144429_controller_security_followup.sql',
    '20260903230825_production_security_hardening.sql',
    '20260904035343_relax_interactive_control_rate_limit.sql',
    '20260904041856_index_sensor_records_created_at.sql',
    '20260904042923_enforce_controller_gateway_after_upgrade.sql',
  ];
  await Promise.all(expected.map(file => read(`supabase/migrations/${file}`)));
});

test('el acceso directo del controlador se cierra al activar el protocolo estricto', async () => {
  const migration = await read(
    'supabase/migrations/20260904042923_enforce_controller_gateway_after_upgrade.sql',
  );
  assert.match(migration, /auth\.role\(\)\) = 'service_role'/);
  assert.match(migration, /not ecosystem\.strict_controller_protocol/);
  assert.match(migration, /controller edge gateway required/);
});
