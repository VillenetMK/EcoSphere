/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const owner = 'Copyright (c) 2026 Gabriel Enrique Villenet Montero.';
const required = [
  '.github/workflows/build-android-apk.yml',
  '.github/workflows/build-desktop-installers.yml',
  '.github/workflows/build-web-pwa.yml',
  '.github/workflows/publish-installers-release.yml',
  'LICENSE',
  'app/build.gradle.kts',
  'desktopApp/build.gradle.kts',
  'desktopApp/src/main/kotlin/com/example/ecosphere/desktop/Main.kt',
  'firmware/replaceable-controller/EcoSphereControllerClient.h',
  'firmware/replaceable-controller/README.md',
  'scripts/check-copyright.mjs',
  'supabase/functions/controller-credentials/index.ts',
  'supabase/functions/controller-gateway/index.ts',
  'supabase/functions/username-login/index.ts',
  'webApp/api-response.js',
  'webApp/app.js',
  'webApp/control-policy.js',
  'webApp/index.html',
  'webApp/sw.js',
];

const failures = [];
for (const relativePath of required) {
  try {
    const contents = await readFile(path.join(root, relativePath), 'utf8');
    if (!contents.includes(owner)) failures.push(`${relativePath}: falta el titular exacto`);
  } catch (error) {
    failures.push(`${relativePath}: ${error.code === 'ENOENT' ? 'archivo ausente' : error.message}`);
  }
}

if (failures.length) {
  console.error(`Verificación de copyright fallida:\n- ${failures.join('\n- ')}`);
  process.exitCode = 1;
} else {
  console.log(`Copyright verificado en ${required.length} archivos críticos.`);
}
