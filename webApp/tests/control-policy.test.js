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
  irrigationStatus,
  isDeviceOnline,
  isTelemetryCurrent,
  isTelemetryFresh,
  manualIrrigationDecision,
  normalizeControlPermissions,
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

test('el permiso de suelo húmedo exige un booleano autorizado en la respuesta del servidor', () => {
  for (const result of [
    { allow_wet_soil_manual_watering: true },
    [{ allow_wet_soil_manual_watering: true }],
  ]) {
    assert.equal(normalizeControlPermissions(result).allowWetSoilManualWatering, true);
  }
  for (const result of [
    null, undefined, {}, [], true,
    { allow_wet_soil_manual_watering: false },
    { allow_wet_soil_manual_watering: 'true' },
    { allow_wet_soil_manual_watering: 1 },
    { role: 'admin', username: 'Hever' },
    [{ allow_wet_soil_manual_watering: true }, { allow_wet_soil_manual_watering: false }],
  ]) {
    assert.equal(normalizeControlPermissions(result).allowWetSoilManualWatering, false);
  }
});

test('el permiso permite suelo húmedo sin cambiar la regla del resto de cuentas', () => {
  const authorized = normalizeControlPermissions({ allow_wet_soil_manual_watering: true });
  assert.equal(irrigationDecision(60, 'high', authorized).allowed, true);
  assert.equal(irrigationDecision(100, 'high', authorized).allowed, true);
  assert.match(irrigationStatus(70, 'high', authorized), /suelo húmedo autorizado para tu cuenta/);
  assert.equal(irrigationDecision(70, 'high').reason, 'soil-too-wet');
  assert.equal(irrigationDecision(70, 'high', normalizeControlPermissions(null)).reason, 'soil-too-wet');
  assert.equal(irrigationDecision(70, 'high', { allowWetSoilManualWatering: 'true' }).allowed, false);
});

test('el permiso no omite el sensor de suelo ni la disponibilidad de agua', () => {
  const authorized = normalizeControlPermissions({ allow_wet_soil_manual_watering: true });
  for (const soil of [null, undefined, '', NaN, Infinity, -1, 101]) {
    assert.equal(irrigationDecision(soil, 'high', authorized).reason, 'missing-soil-reading');
  }
  assert.equal(irrigationDecision(70, 'low', authorized).reason, 'low-water');
  assert.equal(irrigationDecision(70, null, authorized).reason, 'missing-water-reading');
  assert.equal(irrigationDecision(70, 'unknown', authorized).reason, 'missing-water-reading');
});

test('el permiso conserva sesión aprobada, modo manual, conexión y telemetría vigente', () => {
  const now = Date.parse('2026-09-09T22:00:00.000Z');
  const timestamp = new Date(now - 1000).toISOString();
  const record = { created_at: timestamp, soil_humidity: 70, water_level: 'high' };
  const control = { auto_mode: false, esp32_online: true, last_seen_at: timestamp };
  const profile = { role: 'operator', status: 'approved' };
  const permissions = normalizeControlPermissions({ allow_wet_soil_manual_watering: true });
  const decide = (overrides = {}) => manualIrrigationDecision(
    Object.hasOwn(overrides, 'record') ? overrides.record : record,
    Object.hasOwn(overrides, 'control') ? overrides.control : control,
    Object.hasOwn(overrides, 'profile') ? overrides.profile : profile,
    Object.hasOwn(overrides, 'permissions') ? overrides.permissions : permissions,
    now,
  );
  assert.equal(decide().allowed, true);
  assert.equal(decide({ permissions: normalizeControlPermissions(null) }).reason, 'soil-too-wet');
  assert.equal(decide({ profile: null }).reason, 'operator-required');
  assert.equal(decide({ profile: { ...profile, status: 'pending' } }).reason, 'operator-required');
  assert.equal(decide({ profile: { ...profile, role: 'viewer' } }).reason, 'operator-required');
  assert.equal(decide({ control: { ...control, auto_mode: true } }).reason, 'automatic-mode');
  assert.equal(decide({ control: { ...control, auto_mode: null } }).reason, 'automatic-mode');
  assert.equal(decide({ control: { ...control, esp32_online: false } }).reason, 'telemetry-unavailable');
  assert.equal(decide({ control: null }).reason, 'telemetry-unavailable');
  assert.equal(decide({ record: null }).reason, 'telemetry-unavailable');
  assert.equal(decide({ record: { ...record, created_at: new Date(now - 31000).toISOString() } }).reason, 'telemetry-unavailable');
  assert.equal(decide({ control: { ...control, last_seen_at: new Date(now - 31000).toISOString() } }).reason, 'telemetry-unavailable');
});

test('el permiso para el pulso sin sensores exige el booleano específico del servidor', () => {
  for (const result of [
    { allow_sensorless_manual_watering: true },
    [{ allow_sensorless_manual_watering: true }],
  ]) {
    assert.equal(normalizeControlPermissions(result).allowSensorlessManualWatering, true);
  }
  for (const result of [
    null, undefined, {}, [], true,
    { allow_sensorless_manual_watering: false },
    { allow_sensorless_manual_watering: 'true' },
    { allow_sensorless_manual_watering: 1 },
    { allow_wet_soil_manual_watering: true },
    { role: 'admin', username: 'CIMA' },
    [{ allow_sensorless_manual_watering: true }, { allow_sensorless_manual_watering: false }],
  ]) {
    assert.equal(normalizeControlPermissions(result).allowSensorlessManualWatering, false);
  }
});

test('el pulso autorizado sin sensores conserva modo manual, cuenta aprobada y conexión actual', () => {
  const now = Date.parse('2026-09-11T00:00:00.000Z');
  const timestamp = new Date(now - 1000).toISOString();
  const record = Object.freeze({ created_at: timestamp, soil_humidity: null, water_level: 'low' });
  const control = { auto_mode: false, esp32_online: true, last_seen_at: timestamp };
  const profile = { role: 'operator', status: 'approved' };
  const permissions = normalizeControlPermissions({ allow_sensorless_manual_watering: true });
  const decide = (overrides = {}) => manualIrrigationDecision(
    Object.hasOwn(overrides, 'record') ? overrides.record : record,
    Object.hasOwn(overrides, 'control') ? overrides.control : control,
    Object.hasOwn(overrides, 'profile') ? overrides.profile : profile,
    Object.hasOwn(overrides, 'permissions') ? overrides.permissions : permissions,
    now,
  );
  assert.equal(decide().allowed, true);
  assert.equal(decide().message, 'Pulso manual de 3 segundos.');
  for (const soil of [null, 0, 35, 70, 100]) {
    for (const water of [null, 'low', 'high']) {
      assert.equal(decide({ record: { ...record, soil_humidity: soil, water_level: water } }).allowed, true);
    }
  }
  assert.equal(record.soil_humidity, null);
  assert.equal(record.water_level, 'low');
  assert.equal(decide({ permissions: {} }).reason, 'missing-soil-reading');
  assert.equal(decide({ permissions: { allowWetSoilManualWatering: true } }).reason, 'missing-soil-reading');
  assert.equal(decide({ permissions: { allowSensorlessManualWatering: 'true' } }).reason, 'missing-soil-reading');
  assert.equal(decide({ profile: null }).reason, 'operator-required');
  assert.equal(decide({ profile: { ...profile, status: 'pending' } }).reason, 'operator-required');
  assert.equal(decide({ profile: { ...profile, role: 'viewer' } }).reason, 'operator-required');
  assert.equal(decide({ control: { ...control, auto_mode: true } }).reason, 'automatic-mode');
  assert.equal(decide({ control: { ...control, auto_mode: null } }).reason, 'automatic-mode');
  assert.equal(decide({ control: { ...control, esp32_online: false } }).reason, 'telemetry-unavailable');
  assert.equal(decide({ control: null }).reason, 'telemetry-unavailable');
  assert.equal(decide({ record: null }).reason, 'telemetry-unavailable');
  assert.equal(decide({ record: { ...record, created_at: new Date(now - 31000).toISOString() } }).reason, 'telemetry-unavailable');
  assert.equal(decide({ control: { ...control, last_seen_at: new Date(now - 31000).toISOString() } }).reason, 'telemetry-unavailable');
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
