import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

import {
  evaluateManualWatering,
  isDeviceOnline,
  validatePolicy,
} from '../control-policy.js';

const rawPolicy = JSON.parse(
  readFileSync(new URL('../config/control-policy.json', import.meta.url), 'utf8')
);
const policy = validatePolicy(rawPolicy);
const NOW = Date.parse('2026-08-23T12:00:00.000Z');

test('el contrato compartido conserva las decisiones confirmadas', () => {
  assert.equal(policy.pollIntervalMs, 2000);
  assert.equal(policy.onlineTimeoutMs, 30000);
  assert.equal(policy.manualWatering.soilDenyAtOrAbovePercent, 60);
  assert.equal(policy.manualWatering.pumpDurationMs, 3000);
  assert.deepEqual([...policy.manualWatering.blockedWaterLevels], ['low']);
  assert.equal(policy.automaticWatering.soilStartAtOrBelowPercent, 35);
  assert.equal(policy.automaticWatering.implementation, 'firmware');
});

test('el estado online respeta heartbeat, ventana y tolerancia de reloj', () => {
  assert.equal(isDeviceOnline({ esp32_online: true, last_seen_at: '2026-08-23T11:59:30.000Z' }, policy, NOW), true);
  assert.equal(isDeviceOnline({ esp32_online: true, last_seen_at: '2026-08-23T11:59:29.999Z' }, policy, NOW), false);
  assert.equal(isDeviceOnline({ esp32_online: false, last_seen_at: '2026-08-23T12:00:00.000Z' }, policy, NOW), false);
  assert.equal(isDeviceOnline({ esp32_online: true, last_seen_at: 'fecha-inválida' }, policy, NOW), false);
});

test('el riego manual exige humedad válida y aplica el límite inclusivo de 60 %', () => {
  assert.equal(evaluateManualWatering(null, 'high', policy).allowed, false);
  assert.equal(evaluateManualWatering(59.9, 'high', policy).allowed, true);
  assert.equal(evaluateManualWatering(60, 'high', policy).allowed, false);
  assert.equal(evaluateManualWatering(80, 'high', policy).allowed, false);
});

test('el nivel bajo bloquea y los valores no bloqueados conservan el comportamiento vigente', () => {
  assert.equal(evaluateManualWatering(30, 'LOW', policy).allowed, false);
  assert.equal(evaluateManualWatering(30, 'high', policy).allowed, true);
  assert.equal(evaluateManualWatering(30, null, policy).allowed, true);
});
