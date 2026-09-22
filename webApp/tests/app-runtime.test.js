/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import * as policy from '../control-policy.js';
import * as history from '../history.js';
import * as source from '../history-source.js';
import * as diagnostics from '../diagnostics.js';
import * as response from '../api-response.js';

const script = (await readFile(new URL('../app.js', import.meta.url), 'utf8')).replace(/import[\s\S]*?from '[^']+';/g, '');

function harness() {
  let now = Date.parse('2026-09-22T12:00:00Z');
  const elements = new Map();
  const intervals = new Map();
  function element(id) {
    if (!elements.has(id)) elements.set(id, {
      value: '', hidden: false, disabled: false, dataset: {}, handlers: {},
      classList: { toggle() {} },
      addEventListener(type, handler) { this.handlers[type] = handler; },
      close() { this.handlers.close?.(); },
    });
    return elements.get(id);
  }
  const context = vm.createContext({
    ...policy, ...history, ...source, ...diagnostics, ...response,
    isDeviceOnline: control => policy.isDeviceOnline(control, now),
    isTelemetryCurrent: (record, control) => policy.isTelemetryCurrent(record, control, now),
    Date: class extends Date { static now() { return now; } },
    document: { getElementById: element, querySelectorAll: () => [], activeElement: null },
    navigator: {}, URL, URLSearchParams, AbortController, DOMException,
    window: { addEventListener() {} },
    setInterval: (callback, period) => { const id = Symbol(); intervals.set(id, { callback, period }); return id; },
    clearInterval: id => intervals.delete(id), setTimeout, clearTimeout,
    initializeAuth: async () => {}, authErrorMessage: () => '',
    SUPABASE_URL: 'https://example.invalid', SUPABASE_PUBLISHABLE_KEY: 'fixture', supabase: {},
  });
  vm.runInContext(script, context);
  vm.runInContext(`
    currentProfile = { role: 'operator' };
    latestRecord = { created_at: new Date(Date.now()).toISOString(), soil_humidity: 30, water_level: 'high' };
    deviceControl = { esp32_online: true, last_seen_at: new Date(Date.now()).toISOString(), auto_mode: false };
  `, context);
  return { context, element, intervals, advance: millis => { now += millis; } };
}

test('un fallo de red invalida el estado visible y bloquea controles hasta recuperar datos', async () => {
  const { context, element } = harness();
  context.renderDashboard();
  assert.equal(element('systemStatus').textContent, 'Sistema conectado');
  context.apiGet = async () => { throw new Error('Network unavailable'); };
  await context.refresh();
  assert.equal(element('systemStatus').textContent, 'Conexión sin confirmar');
  assert.equal(element('pumpBtn').disabled, true);
  assert.equal(element('fanPower').disabled, true);
  context.apiGet = async path => path.includes('device_control')
    ? [{ esp32_online: true, last_seen_at: new Date(context.Date.now()).toISOString(), auto_mode: false }]
    : [{ created_at: new Date(context.Date.now()).toISOString(), soil_humidity: 30, water_level: 'high' }];
  await context.refresh();
  assert.equal(element('systemStatus').textContent, 'Sistema conectado');
  assert.equal(element('pumpBtn').disabled, false);
});

test('la telemetría vence aunque una solicitud siga pendiente y el temporizador se limpia al salir', () => {
  const { context, element, advance, intervals } = harness();
  context.apiGet = () => new Promise(() => {});
  context.startApplication({ profile: { role: 'operator' } });
  context.renderDashboard();
  assert.equal(element('systemStatus').textContent, 'Sistema conectado');
  advance(31000);
  [...intervals.values()].find(timer => timer.period === 1000).callback();
  assert.equal(element('systemStatus').textContent, 'Sistema sin conexión');
  assert.equal(element('pumpBtn').disabled, true);
  context.stopApplication();
  assert.equal(intervals.size, 0);
});

test('una respuesta de la sesión anterior no repuebla los datos tras salir', async () => {
  const { context } = harness();
  let resolve;
  const pending = new Promise(done => { resolve = done; });
  context.apiGet = () => pending;
  const refreshing = context.refresh();
  context.stopApplication();
  resolve([{ esp32_online: true, created_at: new Date().toISOString() }]);
  await refreshing;
  assert.equal(vm.runInContext('latestRecord', context), null);
  assert.equal(vm.runInContext('deviceControl', context), null);
});

test('cambiar las fechas descarta una respuesta histórica anterior que llega tarde', async () => {
  const { context, element } = harness();
  let resolve;
  context.apiGet = () => new Promise(done => { resolve = done; });
  const first = context.loadHistory();
  const row = { id: 1, created_at: '2026-09-20T12:00:00Z', soil_humidity: 30, water_level: 'high' };
  context.apiGet = async () => [row];
  element('historyFrom').value = '2026-09-20T00:00:00Z';
  await context.loadHistory();
  resolve([{ ...row, id: 2 }]);
  await first;
  assert.equal(vm.runInContext('historyRecords[0].id', context), 1);
});
