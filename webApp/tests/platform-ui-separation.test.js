import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(testDir, '..', '..');

async function source(relativePath) {
  return readFile(path.join(repoRoot, relativePath), 'utf8');
}

test('Android usa un shell y una autenticación exclusivamente móviles', async () => {
  const mainActivity = await source('app/src/main/java/com/example/ecosphere/MainActivity.kt');
  const mobileShell = await source('app/src/main/java/com/example/ecosphere/ui/mobile/MobileEcoSphereApp.kt');
  const mobileAuth = await source('app/src/main/java/com/example/ecosphere/ui/mobile/MobileAuthScreen.kt');

  assert.match(mainActivity, /ui\.mobile\.MobileEcoSphereApp/);
  assert.match(mainActivity, /ui\.mobile\.MobileAuthScreen/);
  assert.match(mobileShell, /NavigationBar/);
  assert.match(mobileShell, /MobileDestination\.ACCOUNT/);
  assert.doesNotMatch(mobileAuth, /ECOSPHERE CONTROL/);
  assert.doesNotMatch(mobileAuth, /La vida puede prosperar/);
});

test('cada plataforma conserva una entrada visual independiente', async () => {
  const mobile = await source('app/src/main/java/com/example/ecosphere/ui/mobile/MobileEcoSphereApp.kt');
  const desktop = await source('desktopApp/src/main/kotlin/com/example/ecosphere/desktop/Main.kt');
  const web = await source('webApp/index.html');
  const shared = await source('sharedCore/src/main/kotlin/com/example/ecosphere/shared/EcoSphereContract.kt');

  assert.doesNotMatch(mobile, /desktopApp|webApp|org\.w3c\.dom/);
  assert.doesNotMatch(desktop, /MobileEcoSphereApp|androidx\.activity/);
  assert.doesNotMatch(web, /MobileEcoSphereApp|androidx\.compose/);
  assert.doesNotMatch(shared, /androidx\.compose|androidx\.activity|org\.w3c\.dom/);
});
