/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { controllerOperation, rpcArguments } from './validation.ts';

const sync = {
  operation: 'sync',
  p_hardware_uid: '012345ABCDEF',
  p_device_secret: 'a'.repeat(64),
  p_heartbeat_seq: 7,
  p_firmware_version: 'diagnostic-test',
  p_has_telemetry: false,
  p_boot_nonce: 'b'.repeat(32),
};

const diagnostic = {
  request_id: 12,
  result: 'completed',
  reason: 'duration_elapsed',
  elapsed_ms: 1500,
  gpio26_start: 1,
  gpio26_end: 0,
  led_power_before: 35,
  led_power_after: 35,
  led_duty_before: 90,
  led_duty_during: 90,
  led_duty_after: 90,
};

function operationWith(value) {
  return controllerOperation({ ...sync, p_pump_diagnostic: value });
}

test('legacy sync accepts omitted or null diagnostic and retains bigint heartbeat support', () => {
  assert.equal(controllerOperation(sync), 'sync');
  assert.equal(operationWith(null), 'sync');
  assert.equal(controllerOperation({ ...sync, p_heartbeat_seq: '9223372036854775807' }), 'sync');
  assert.equal(controllerOperation({ ...sync, p_heartbeat_seq: '9223372036854775808' }), undefined);
});

test('completed diagnostics accept every pulse termination reason', () => {
  for (const reason of [
    'duration_elapsed', 'authorization_expired', 'mode_changed', 'water_unavailable',
    'soil_invalid', 'soil_wet', 'manual_stop',
  ]) {
    assert.equal(operationWith({ ...diagnostic, reason }), 'sync', reason);
    assert.equal(operationWith({ ...diagnostic, result: 'rejected', reason }), undefined, reason);
  }
});

test('rejected diagnostics accept only gate reasons', () => {
  for (const reason of ['remote_gate', 'local_gate']) {
    assert.equal(operationWith({ ...diagnostic, result: 'rejected', reason }), 'sync', reason);
    assert.equal(operationWith({ ...diagnostic, reason }), undefined, reason);
  }
});

test('diagnostic must be an object with all eleven known fields', () => {
  for (const value of [[], [diagnostic], 'diagnostic', 0, true]) {
    assert.equal(operationWith(value), undefined);
  }
  for (const field of Object.keys(diagnostic)) {
    const missing = { ...diagnostic };
    delete missing[field];
    assert.equal(operationWith(missing), undefined, `missing ${field}`);
    assert.equal(operationWith({ ...diagnostic, [field]: undefined }), undefined, `undefined ${field}`);
    assert.equal(operationWith({ ...missing, unexpected: 0 }), undefined, `replaced ${field}`);
  }
  assert.equal(operationWith({ ...diagnostic, unexpected: 0 }), undefined);
  assert.equal(operationWith({ ...diagnostic, ...JSON.parse('{"__proto__": {}}') }), undefined);
});

test('result and reason use the exact ASCII vocabulary', () => {
  for (const result of ['complete', 'COMPLETED', '', null, true, 1, {}, []]) {
    assert.equal(operationWith({ ...diagnostic, result }), undefined);
  }
  for (const reason of [
    'unknown', 'DURATION_ELAPSED', 'duration_elapsed ', 'duration_elapsed\n',
    'duration_elapsed\0', 'duration_еlapsed', '', null, true, 1, {}, [],
  ]) {
    assert.equal(operationWith({ ...diagnostic, reason }), undefined);
  }
});

test('integer diagnostic fields enforce inclusive ranges without coercion', () => {
  const ranges = [
    ['request_id', 1, Number.MAX_SAFE_INTEGER],
    ['elapsed_ms', 0, 4294967295],
    ['gpio26_start', 0, 1],
    ['gpio26_end', 0, 1],
    ['led_power_before', 0, 100],
    ['led_power_after', 0, 100],
    ['led_duty_before', 0, 256],
    ['led_duty_during', 0, 256],
    ['led_duty_after', 0, 256],
  ];
  for (const [field, minimum, maximum] of ranges) {
    for (const value of [minimum, maximum]) {
      assert.equal(operationWith({ ...diagnostic, [field]: value }), 'sync', `${field} accepts ${value}`);
    }
    for (const value of [minimum - 1, maximum + 1, 0.5, NaN, Infinity, -Infinity, '1', true, {}, []]) {
      assert.equal(operationWith({ ...diagnostic, [field]: value }), undefined, `${field} rejects ${String(value)}`);
    }
  }
});

test('only GPIO readbacks and duty during the pulse can be null', () => {
  const nullable = new Set(['gpio26_start', 'gpio26_end', 'led_duty_during']);
  for (const field of Object.keys(diagnostic)) {
    assert.equal(operationWith({ ...diagnostic, [field]: null }), nullable.has(field) ? 'sync' : undefined, field);
  }
  assert.equal(operationWith({
    ...diagnostic, gpio26_start: null, gpio26_end: null, led_duty_during: null,
  }), 'sync');
});

test('sync retains its top-level allowlist and required authentication fields', () => {
  assert.equal(controllerOperation({ ...sync, p_pump_diagnostic: diagnostic, unexpected: 1 }), undefined);
  for (const field of [
    'operation', 'p_hardware_uid', 'p_device_secret', 'p_heartbeat_seq',
    'p_firmware_version', 'p_has_telemetry', 'p_boot_nonce',
  ]) {
    const missing = { ...sync, p_pump_diagnostic: diagnostic };
    delete missing[field];
    assert.equal(controllerOperation(missing), undefined, field);
  }
  assert.equal(controllerOperation({ ...sync, p_device_secret: 'invalid', p_pump_diagnostic: diagnostic }), undefined);
});

test('legacy telemetry type and range checks still apply with diagnostics', () => {
  const telemetry = {
    ...sync, p_has_telemetry: true, p_temperature: 20, p_air_humidity: 50,
    p_soil_humidity: 25, p_light_lux: 200, p_water_level: 'high',
    p_fan_on: false, p_pump_on: false, p_led_on: true, p_reported_auto_mode: false,
    p_reported_fan_power: 0, p_reported_led_power: 35, p_pump_diagnostic: diagnostic,
  };
  assert.equal(controllerOperation(telemetry), 'sync');
  for (const [field, value] of [
    ['p_temperature', 86], ['p_air_humidity', -1], ['p_soil_humidity', 101],
    ['p_light_lux', 200001], ['p_water_level', 'unknown'], ['p_fan_on', 1],
    ['p_pump_on', 'false'], ['p_led_on', 0], ['p_reported_auto_mode', 'true'],
    ['p_reported_fan_power', 0.5], ['p_reported_led_power', 101],
  ]) {
    assert.equal(controllerOperation({ ...telemetry, [field]: value }), undefined, field);
  }
});

test('begin_pairing keeps its existing schema and rejects the sync-only diagnostic', () => {
  const pairing = {
    operation: 'begin_pairing', p_hardware_uid: sync.p_hardware_uid,
    p_device_secret: sync.p_device_secret, p_firmware_version: sync.p_firmware_version,
  };
  assert.equal(controllerOperation(pairing), 'begin_pairing');
  assert.equal(controllerOperation({ ...pairing, p_pump_diagnostic: diagnostic }), undefined);
  assert.equal(controllerOperation({ ...pairing, p_pump_diagnostic: null }), undefined);
});

test('RPC arguments preserve the validated diagnostic and old firmware omission', () => {
  const body = { ...sync, p_pump_diagnostic: Object.freeze({ ...diagnostic }) };
  const expected = { ...body };
  delete expected.operation;
  assert.deepEqual(rpcArguments(Object.freeze(body)), expected);
  assert.equal(Object.hasOwn(rpcArguments(sync), 'p_pump_diagnostic'), false);
  assert.equal(rpcArguments({ ...sync, p_pump_diagnostic: null }).p_pump_diagnostic, null);
});
