/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { environmentPresentation, estimateLedLux } from '../demo-presentation.js';
import {
  actuatorPwmLabel, actuatorSwitchLabel, isDeviceOnline, isTelemetryCurrent,
  manualIrrigationDecision, normalizeControlPermissions, waterLevelLabel,
} from '../control-policy.js';

const heverId = '367e842b-fd47-4c38-a3fc-c54c47732a9e';
const approved = { status: 'approved', role: 'operator', username: 'Hever' };

test('LED reference follows confirmed output, with no inferred value for invalid reports', () => {
  for (const [power, lux] of [[0, 0], [25, 212.5], [50, 425], [100, 850]]) {
    assert.equal(estimateLedLux({ led_on: power > 0, led_power: power }), lux);
  }
  for (const report of [null, {}, { led_on: true }, { led_power: 50 },
    { led_on: true, led_power: 0 }, { led_on: false, led_power: 50 },
    ...[null, '', '50', -1, 101, NaN, Infinity].map(led_power => ({ led_on: true, led_power }))]) {
    assert.equal(estimateLedLux(report), null);
  }
  const raw = Object.freeze({ led_on: true, led_power: 50, light_lux: 73 });
  assert.equal(environmentPresentation(raw, { userId: heverId, profile: approved }).light_lux, 425);
  assert.equal(environmentPresentation(raw, { userId: 'gabriel', profile: approved }).light_lux, 73);
  assert.equal(raw.light_lux, 73);
});

test('demo requires the exact approved operator session UUID, not a display name or permission', () => {
  const record = Object.freeze({ temperature: null, air_humidity: 35, light_lux: 12 });
  assert.equal(environmentPresentation(record, { userId: heverId, profile: approved }).simulated, true);
  for (const identity of [
    {}, { profile: approved }, { userId: 'another-user', profile: approved },
    { userId: heverId, profile: { ...approved, status: 'blocked' } },
    { userId: heverId, profile: { ...approved, role: 'admin' } },
    { userId: 'administrator', profile: { ...approved, allowWetSoilManualWatering: true } },
  ]) {
    assert.deepEqual(environmentPresentation(record, identity), { simulated: false, ...record });
  }
});

test('presentation has no soil, water, actuator or timestamp fields and never modifies raw telemetry', () => {
  const record = Object.freeze({
    temperature: null, air_humidity: null, light_lux: null,
    soil_humidity: null, water_level: 'low', fan_on: false, led_on: false, pump_on: false,
    created_at: new Date().toISOString(),
  });
  const before = JSON.stringify(record);
  const control = { esp32_online: true, last_seen_at: record.created_at, auto_mode: false };
  const permission = { allowWetSoilManualWatering: true };
  const decision = manualIrrigationDecision(record, control, approved, permission);
  const presentation = environmentPresentation(record, { userId: heverId, profile: approved });
  assert.deepEqual(Object.keys(presentation).sort(), ['air_humidity', 'light_lux', 'simulated', 'temperature']);
  assert.equal(JSON.stringify(record), before);
  assert.deepEqual(manualIrrigationDecision(record, control, approved, permission), decision);
  assert.equal(decision.allowed, false);
  assert.equal(environmentPresentation(null, { userId: heverId, profile: approved }).simulated, true);
});

const app = await readFile(new URL('../app.js', import.meta.url), 'utf8');
const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');

function dashboardHarness() {
  const elements = new Map([...html.matchAll(/\bid="([^"]+)"/g)].map(([, id]) => [id, {
    textContent: '', hidden: false, disabled: false,
    classList: { toggle() {} },
  }]));
  const context = vm.createContext({
    environmentPresentation, actuatorPwmLabel, actuatorSwitchLabel,
    isTelemetryCurrent, manualIrrigationDecision, normalizeControlPermissions, waterLevelLabel,
    onlineNow: isDeviceOnline,
    $: id => { assert.ok(elements.has(id), `Missing UI element ${id}`); return elements.get(id); },
    formatDate: value => value ?? 'Sin registro',
    formatNumber: (value, unit) => value == null ? '--' : `${Number(value).toFixed(1)} ${unit}`,
    iconPath: value => value,
    heverAssistant: { checkAccess() {}, reset() {} },
    refresh() {}, setInterval: () => 1, clearInterval() {},
  });
  vm.runInContext(`
    let latestRecord = null, deviceControl = null, currentProfile = null, currentUserId = null;
    let historyRecords = [], controllerStatus = null, currentControlPermissions = {}, busy = false;
    let applicationGeneration = 0, activeScreen = 'dashboard', refreshTimer = null;
    ${app.slice(app.indexOf('function renderDashboard()'), app.indexOf('function renderHistory()'))}
    ${app.slice(app.indexOf('function startApplication('), app.indexOf('\ninitializeAuth({'))}
  `, context);
  return { elements, run: source => vm.runInContext(source, context) };
}

test('dashboard account switch and signout clear simulated values; offline demo cannot fake controls or soil', () => {
  const { elements, run } = dashboardHarness();
  run(`startApplication({ session: { user: { id: '${heverId}' } }, profile: ${JSON.stringify(approved)} });`);
  assert.equal(elements.get('demoNotice').hidden, true);
  assert.equal(elements.get('environmentTitle').textContent, 'Panel ambiental');
  assert.equal(elements.get('temperatureDetails').hidden, true);
  assert.equal(elements.get('lightDetails').hidden, true);
  assert.equal(elements.get('airHumidityDetails').hidden, true);
  assert.equal(elements.get('soilDetails').hidden, false);
  assert.equal(elements.get('waterDetails').hidden, false);
  assert.equal(elements.get('metricsGrid').hidden, false);
  assert.equal(elements.get('temperatureValue').textContent, '25.4 °C');
  assert.equal(elements.get('temperatureSource').textContent, '');
  assert.equal(elements.get('lightSource').textContent, '');
  assert.equal(elements.get('lightValue').textContent, '--');
  assert.equal(elements.get('soilHumidityValue').textContent, '--');
  assert.equal(elements.get('waterValue').textContent, '--');
  assert.equal(elements.get('systemStatus').textContent, 'Sistema sin conexión');
  assert.equal(elements.get('pumpState').textContent, 'SIN CONFIRMAR');
  assert.equal(elements.get('pumpBtn').disabled, true);

  const now = new Date().toISOString();
  run(`latestRecord = { created_at: '${now}', temperature: null, air_humidity: null, light_lux: null,
    soil_humidity: 72, water_level: 'high', fan_on: true, fan_power: 40, led_on: false, pump_on: false };
    deviceControl = { esp32_online: true, last_seen_at: '${now}', auto_mode: false };
    currentControlPermissions = { allowWetSoilManualWatering: true }; renderDashboard();`);
  assert.equal(elements.get('soilHumidityValue').textContent, '72.0 %');
  assert.equal(elements.get('waterValue').textContent, 'Disponible');
  assert.equal(elements.get('fanState').textContent, 'SALIDA PWM 40 %');
  assert.equal(elements.get('pumpBtn').disabled, false);
  run(`latestRecord.water_level = 'low'; renderDashboard();`);
  assert.equal(elements.get('pumpBtn').disabled, true);

  run(`startApplication({ session: { user: { id: 'gabriel-admin' } }, profile: { status: 'approved', role: 'admin' } });`);
  assert.equal(elements.get('demoNotice').hidden, true);
  assert.equal(elements.get('temperatureValue').textContent, '--');
  assert.equal(elements.get('temperatureSource').textContent, 'BME280');
  assert.equal(elements.get('environmentTitle').textContent, 'Lecturas ambientales');
  assert.equal(elements.get('temperatureDetails').hidden, false);
  assert.equal(elements.get('lightDetails').hidden, false);
  assert.equal(elements.get('airHumidityDetails').hidden, false);
  run(`latestRecord = { created_at: '${now}', temperature: 19.3, air_humidity: 47, light_lux: 91 };
    deviceControl = { esp32_online: true, last_seen_at: '${now}', auto_mode: false }; renderDashboard();`);
  assert.equal(elements.get('temperatureValue').textContent, '19.3 °C');
  run(`startApplication({ session: { user: { id: '${heverId}' } }, profile: ${JSON.stringify(approved)} }); stopApplication();`);
  assert.equal(elements.get('demoNotice').hidden, true);
  assert.equal(elements.get('temperatureValue').textContent, '--');
  assert.equal(elements.get('temperatureSource').textContent, 'BME280');
});

test('dashboard waits for LED telemetry; desired power never drives the reference', () => {
  const { elements, run } = dashboardHarness();
  run(`startApplication({ session: { user: { id: '${heverId}' } }, profile: ${JSON.stringify(approved)} });`);
  const now = new Date().toISOString();
  run(`latestRecord = { created_at: '${now}', led_on: true, led_power: 100, light_lux: 99 };
    deviceControl = { esp32_online: true, last_seen_at: '${now}', auto_mode: false, led_power: 100 };
    renderDashboard();`);
  assert.equal(elements.get('lightValue').textContent, '850.0 lux');
  run('deviceControl.led_power = 25; renderDashboard();');
  assert.equal(elements.get('lightValue').textContent, '850.0 lux');
  run('latestRecord.led_power = 25; renderDashboard();');
  assert.equal(elements.get('lightValue').textContent, '212.5 lux');
  run('latestRecord.led_on = false; latestRecord.led_power = 0; renderDashboard();');
  assert.equal(elements.get('lightValue').textContent, '0.0 lux');
  run('latestRecord.led_power = null; renderDashboard();');
  assert.equal(elements.get('lightValue').textContent, '--');
  run('latestRecord.led_on = true; latestRecord.led_power = 50; deviceControl.esp32_online = false; renderDashboard();');
  assert.equal(elements.get('lightValue').textContent, '--');
  run(`deviceControl.esp32_online = true; latestRecord.created_at = '2000-01-01T00:00:00Z'; renderDashboard();`);
  assert.equal(elements.get('lightValue').textContent, '--');
});
