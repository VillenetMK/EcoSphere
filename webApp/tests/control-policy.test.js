import test from 'node:test';
import assert from 'node:assert/strict';
import {
  CONTROL_POLICY,
  clampPower,
  irrigationDecision,
  isDeviceOnline,
  waterLevelLabel,
} from '../control-policy.js';

test('el riego exige lecturas válidas de suelo y del flotador horizontal', () => {
  assert.equal(irrigationDecision(null, 'high').reason, 'missing-soil-reading');
  assert.equal(irrigationDecision(30, null).reason, 'missing-water-reading');
  assert.equal(irrigationDecision(30, 'medium').reason, 'missing-water-reading');
});

test('el riego se bloquea con suelo húmedo o nivel bajo', () => {
  assert.equal(irrigationDecision(60, 'high').reason, 'soil-too-wet');
  assert.equal(irrigationDecision(25, 'low').reason, 'low-water');
});

test('el riego sólo se permite con suelo seguro y agua disponible', () => {
  assert.equal(irrigationDecision(35, 'high').allowed, true);
  assert.equal(irrigationDecision(59.9, 'HIGH').allowed, true);
});

test('la potencia queda limitada entre cero y cien', () => {
  assert.equal(clampPower(-20), 0);
  assert.equal(clampPower(55), 55);
  assert.equal(clampPower(120), 100);
});

test('el estado online respeta el timeout del heartbeat', () => {
  const lastSeen = Date.parse('2026-08-23T20:00:00.000Z');
  const control = { esp32_online: true, last_seen_at: '2026-08-23T20:00:00.000Z' };
  assert.equal(isDeviceOnline(control, lastSeen + CONTROL_POLICY.onlineTimeoutMs - 1), true);
  assert.equal(isDeviceOnline(control, lastSeen + CONTROL_POLICY.onlineTimeoutMs + 1), false);
  assert.equal(isDeviceOnline({ ...control, esp32_online: false }, lastSeen), false);
});

test('el único flotador sólo reconoce high y low', () => {
  assert.equal(waterLevelLabel('high'), 'Disponible');
  assert.equal(waterLevelLabel('low'), 'Bajo');
  assert.equal(waterLevelLabel('normal'), 'Sin lectura válida');
});
