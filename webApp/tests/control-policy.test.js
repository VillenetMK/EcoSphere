/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import {
  CONTROL_POLICY,
  actuatorPwmLabel,
  actuatorSwitchLabel,
  clampPower,
  irrigationDecision,
  isDeviceOnline,
  isTelemetryCurrent,
  isTelemetryFresh,
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

test('la telemetría sólo se considera actual dentro del timeout', () => {
  const createdAt = '2026-08-23T20:00:00.000Z';
  const timestamp = Date.parse(createdAt);
  assert.equal(isTelemetryFresh({ created_at: createdAt }, timestamp + 29_999), true);
  assert.equal(isTelemetryFresh({ created_at: createdAt }, timestamp + 30_001), false);
  assert.equal(isTelemetryFresh(null, timestamp), false);
});

test('una lectura antigua nunca se presenta como estado físico actual', () => {
  const now = Date.parse('2026-08-23T20:00:00.000Z');
  const freshRecord = { created_at: '2026-08-23T19:59:55.000Z', soil_humidity: 42 };
  const staleRecord = { created_at: '2026-08-23T19:00:00.000Z', soil_humidity: 100 };
  const onlineControl = { esp32_online: true, last_seen_at: '2026-08-23T19:59:55.000Z' };

  assert.equal(isTelemetryCurrent(freshRecord, onlineControl, now), true);
  assert.equal(isTelemetryCurrent(staleRecord, onlineControl, now), false);
  assert.equal(isTelemetryCurrent(freshRecord, { ...onlineControl, esp32_online: false }, now), false);
});

test('los actuadores describen salidas del ESP32 y no presencia física', () => {
  assert.equal(actuatorPwmLabel(true, 100), 'SALIDA PWM 100 %');
  assert.equal(actuatorPwmLabel(false, 0), 'SALIDA PWM 0 %');
  assert.equal(actuatorPwmLabel(null, null), 'SIN REGISTRO');
  assert.equal(actuatorSwitchLabel(true), 'SALIDA ACTIVA');
  assert.equal(actuatorSwitchLabel(false), 'SALIDA INACTIVA');
});

test('el único flotador sólo reconoce high y low', () => {
  assert.equal(waterLevelLabel('high'), 'Disponible');
  assert.equal(waterLevelLabel('low'), 'Bajo');
  assert.equal(waterLevelLabel('normal'), 'Sin lectura válida');
});
