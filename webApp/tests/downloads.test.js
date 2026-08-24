import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const webAppDir = path.resolve(testDir, '..');

test('la web ofrece instaladores oficiales para las tres plataformas', async () => {
  const html = await readFile(path.join(webAppDir, 'index.html'), 'utf8');
  const expected = [
    ['Windows', 'EcoSphere-1.4.1-Windows-x64.msi', 'ic_windows.svg'],
    ['Linux', 'EcoSphere-1.4.1-Linux-amd64.deb', 'ic_linux.svg'],
    ['Android', 'EcoSphere-1.4.1-Android.apk', 'ic_android.svg'],
  ];

  for (const [platform, file, icon] of expected) {
    assert.match(html, new RegExp(`>${platform}<`));
    assert.match(html, new RegExp(`releases/latest/download/${file.replaceAll('.', '\\.')}`));
    assert.match(html, new RegExp(`icons/${icon.replaceAll('.', '\\.')}`));
  }
});

test('la web ya no promociona su propia instalación como PWA', async () => {
  const [html, app] = await Promise.all([
    readFile(path.join(webAppDir, 'index.html'), 'utf8'),
    readFile(path.join(webAppDir, 'app.js'), 'utf8'),
  ]);

  assert.doesNotMatch(html, /id="installBtn"/);
  assert.doesNotMatch(app, /beforeinstallprompt|deferredInstallPrompt|appinstalled/);
  assert.match(html, /id="downloadsBtn"/);
});

test('los iconos de plataforma conservan el formato profesional de 48 px', async () => {
  await Promise.all(['ic_windows', 'ic_linux', 'ic_android'].map(async name => {
    const svg = await readFile(path.join(webAppDir, 'icons', `${name}.svg`), 'utf8');
    assert.match(svg, /width="48" height="48"/);
    assert.match(svg, /viewBox="0 0 48 48"/);
    assert.doesNotMatch(svg, /<script\b|\bonload\s*=/i);
  }));
});
