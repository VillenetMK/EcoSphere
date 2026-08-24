import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const webAppDir = path.resolve(testDir, '..');

test('el panel principal conserva la estructura de la app móvil', async () => {
  const html = await readFile(path.join(webAppDir, 'index.html'), 'utf8');
  const dashboard = html.slice(
    html.indexOf('<section id="dashboard"'),
    html.indexOf('<section id="history"'),
  );
  const sections = [
    'Estado general',
    'Lecturas ambientales',
    'Estado del sistema',
    'Control remoto',
  ];

  let previousIndex = -1;
  for (const section of sections) {
    const currentIndex = dashboard.indexOf(section);
    assert.ok(currentIndex > previousIndex, `${section} debe aparecer en el orden móvil`);
    previousIndex = currentIndex;
  }

  assert.match(dashboard, /Sistema inteligente de microclima/);
  assert.match(dashboard, /Cuando el ESP32 vuelva a enviar datos/);
  assert.doesNotMatch(dashboard, /Control y estado del sistema/);
});

test('el estado reportado y los controles remotos permanecen separados', async () => {
  const [html, app] = await Promise.all([
    readFile(path.join(webAppDir, 'index.html'), 'utf8'),
    readFile(path.join(webAppDir, 'app.js'), 'utf8'),
  ]);

  for (const id of ['fanState', 'pumpState', 'ledState', 'controlState']) {
    assert.match(html, new RegExp(`id="${id}"`));
    assert.match(app, new RegExp(`\\$\\('${id}'\\)`));
  }
  assert.match(html, /id="autoMode"/);
  assert.match(html, /id="fanPower"/);
  assert.match(html, /id="ledPower"/);
  assert.match(html, /id="pumpBtn"/);
});
