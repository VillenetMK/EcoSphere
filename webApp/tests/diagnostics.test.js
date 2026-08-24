import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildDiagnosticModel,
  isTelemetryFresh,
  technicalReport,
} from '../diagnostics.js';

const NOW = Date.parse('2026-08-23T20:00:00.000Z');

function freshRecord(overrides = {}) {
  return {
    created_at: '2026-08-23T19:59:55.000Z',
    temperature: 24.5,
    air_humidity: 55,
    soil_humidity: 40,
    light_lux: 350,
    water_level: 'high',
    fan_power: 0,
    led_power: 25,
    pump_on: false,
    ...overrides,
  };
}

const onlineControl = {
  esp32_online: true,
  last_seen_at: '2026-08-23T19:59:55.000Z',
};

function findItem(model, name) {
  return model.groups.flatMap(group => group.items).find(entry => entry.name === name);
}

test('la telemetría sólo es actual dentro del intervalo de heartbeat', () => {
  assert.equal(isTelemetryFresh(freshRecord(), NOW), true);
  assert.equal(isTelemetryFresh({ created_at: '2026-08-23T19:00:00.000Z' }, NOW), false);
  assert.equal(isTelemetryFresh(null, NOW), false);
});

test('un sistema offline nunca presenta los datos históricos como actuales', () => {
  const model = buildDiagnosticModel(
    freshRecord({ created_at: '2026-08-22T16:33:14.000Z', soil_humidity: 100, water_level: 'low' }),
    { esp32_online: true, last_seen_at: '2026-08-22T16:33:10.000Z' },
    NOW,
  );

  assert.equal(model.headline, 'Telemetría desactualizada');
  assert.match(model.summary, /no representan el estado actual/i);
  assert.equal(findItem(model, 'ESP32').severity, 'critical');
  assert.equal(findItem(model, 'Humedad de suelo').status, 'DATO ANTIGUO');
  assert.match(findItem(model, 'Humedad de suelo').detail, /riego permanece bloqueado/i);
  assert.equal(findItem(model, 'Nivel de agua').status, 'DATO ANTIGUO');
  assert.match(findItem(model, 'Nivel de agua').detail, /nivel recibido era bajo/i);
  assert.equal(findItem(model, 'Ventilador').status, 'ÚLTIMO ESTADO');
});

test('nivel bajo y suelo húmedo generan advertencias cuando el dato es reciente', () => {
  const model = buildDiagnosticModel(
    freshRecord({ soil_humidity: 100, water_level: 'low' }),
    onlineControl,
    NOW,
  );

  assert.equal(findItem(model, 'Humedad de suelo').status, 'ADVERTENCIA · RIEGO BLOQUEADO');
  assert.equal(findItem(model, 'Nivel de agua').status, 'ALERTA · RIEGO BLOQUEADO');
  assert.equal(model.severity, 'warning');
});

test('sensores sin valores se identifican como sin datos, no como OK', () => {
  const model = buildDiagnosticModel(
    freshRecord({ temperature: null, air_humidity: null, light_lux: null }),
    onlineControl,
    NOW,
  );

  assert.equal(findItem(model, 'BME280').status, 'SIN DATOS');
  assert.equal(findItem(model, 'BH1750').status, 'SIN DATOS');
  assert.equal(model.counts.unknown, 2);
});

test('los actuadores muestran un estado concreto cuando la telemetría es reciente', () => {
  const model = buildDiagnosticModel(freshRecord(), onlineControl, NOW);
  assert.equal(findItem(model, 'Ventilador').status, 'APAGADO');
  assert.equal(findItem(model, 'LED Grow').status, 'ENCENDIDO');
  assert.equal(findItem(model, 'Bomba').status, 'APAGADO');
});

test('el reporte técnico incluye resumen y grupos sin credenciales', () => {
  const report = technicalReport(buildDiagnosticModel(freshRecord(), onlineControl, NOW));
  assert.match(report, /Reporte técnico de diagnóstico/);
  assert.match(report, /\[Conectividad\]/);
  assert.match(report, /\[Sensores\]/);
  assert.match(report, /\[Actuadores\]/);
  assert.doesNotMatch(report, /apikey|Bearer|sb_publishable/);
});
