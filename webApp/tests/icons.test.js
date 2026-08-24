import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const webAppDir = path.resolve(testDir, '..');
const iconNames = [
  'ic_air_humidity',
  'ic_auto_mode',
  'ic_dashboard',
  'ic_diagnostics',
  'ic_error',
  'ic_esp32',
  'ic_fan',
  'ic_grow_led',
  'ic_history',
  'ic_info',
  'ic_light',
  'ic_manual_mode',
  'ic_offline',
  'ic_ok',
  'ic_online',
  'ic_pump',
  'ic_refresh_animated',
  'ic_soil_humidity',
  'ic_temperature',
  'ic_warning',
  'ic_water_level',
];

test('el paquete visual incluye SVG seguros y uniformes de 24 px', async () => {
  await Promise.all(iconNames.map(async name => {
    const svg = await readFile(path.join(webAppDir, 'icons', `${name}.svg`), 'utf8');
    assert.match(svg, /<svg\b/);
    assert.match(svg, /viewBox="0 0 24 24"/);
    assert.doesNotMatch(svg, /<script\b/i);
    assert.doesNotMatch(svg, /\bonload\s*=/i);
    assert.doesNotMatch(svg, /\b(?:xlink:)?href\s*=/i);
  }));
});

test('la PWA publica y almacena todos los iconos usados', async () => {
  const [html, app, worker] = await Promise.all([
    readFile(path.join(webAppDir, 'index.html'), 'utf8'),
    readFile(path.join(webAppDir, 'app.js'), 'utf8'),
    readFile(path.join(webAppDir, 'sw.js'), 'utf8'),
  ]);

  for (const name of iconNames) {
    assert.match(worker, new RegExp(`icons/${name}\\.svg`));
  }
  assert.match(html, /icons\/ic_dashboard\.svg/);
  assert.match(html, /icons\/ic_refresh_animated\.svg/);
  assert.match(app, /HISTORY_METRIC_ICONS/);
  assert.match(app, /DIAGNOSTIC_ICONS/);
});
