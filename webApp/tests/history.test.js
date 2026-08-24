import test from 'node:test';
import assert from 'node:assert/strict';
import {
  analyzeHistory,
  buildHistoryChart,
  historyCsv,
  isAbruptSoilChange,
  paginateHistory,
  prepareHistoryExport,
} from '../history.js';

const NOW = Date.parse('2026-08-23T20:00:00.000Z');

function record(seconds, overrides = {}) {
  return {
    created_at: new Date(Date.parse('2026-08-22T16:30:00.000Z') + seconds * 1000).toISOString(),
    temperature: null,
    air_humidity: null,
    soil_humidity: 100,
    light_lux: null,
    water_level: 'low',
    fan_power: 0,
    led_power: 0,
    pump_on: false,
    ...overrides,
  };
}

test('detecta saltos de humedad de 40 puntos o más en quince segundos', () => {
  assert.equal(isAbruptSoilChange(record(10, { soil_humidity: 100 }), record(5, { soil_humidity: 0 })), true);
  assert.equal(isAbruptSoilChange(record(30, { soil_humidity: 100 }), record(5, { soil_humidity: 0 })), false);
  assert.equal(isAbruptSoilChange(record(10, { soil_humidity: 35 }), record(5, { soil_humidity: 20 })), false);
});

test('resume calidad, agua baja y antigüedad sin presentar datos parciales como completos', () => {
  const records = [record(10), record(5, { soil_humidity: 0 }), record(0)];
  const analysis = analyzeHistory(records, NOW);
  assert.equal(analysis.total, 3);
  assert.equal(analysis.completeness, 40);
  assert.equal(analysis.completeRecords, 0);
  assert.equal(analysis.lowWaterRecords, 3);
  assert.equal(analysis.abruptChanges, 2);
  assert.equal(analysis.stale, true);
});

test('prioriza una variación brusca sobre la advertencia repetida de agua baja', () => {
  const analysis = analyzeHistory([record(10, { soil_humidity: 100 }), record(5, { soil_humidity: 0 })], NOW);
  assert.equal(analysis.records[0].historyStatus.code, 'abrupt-soil-change');
  assert.equal(analysis.records[1].historyStatus.code, 'low-water');
});

test('pagina los registros y corrige páginas fuera de rango', () => {
  const rows = Array.from({ length: 53 }, (_, index) => record(index));
  const page = paginateHistory(rows, 3, 25);
  assert.equal(page.items.length, 3);
  assert.equal(page.from, 51);
  assert.equal(page.to, 53);
  assert.equal(page.pageCount, 3);
  assert.equal(paginateHistory(rows, 99, 25).page, 3);
});

test('genera una serie gráfica con estadísticas de la métrica seleccionada', () => {
  const chart = buildHistoryChart([
    record(0, { soil_humidity: 0 }),
    record(5, { soil_humidity: 50 }),
    record(10, { soil_humidity: 100 }),
  ], 'soil_humidity');
  assert.equal(chart.points.length, 3);
  assert.equal(chart.min, 0);
  assert.equal(chart.average, 50);
  assert.equal(chart.max, 100);
  assert.equal(buildHistoryChart([record(0)], 'temperature').points.length, 0);
});

test('exporta CSV con evaluación y sin claves de acceso', () => {
  const csv = historyCsv([record(0)]);
  assert.match(csv, /Fecha ISO/);
  assert.match(csv, /AGUA BAJA/);
  assert.doesNotMatch(csv, /apikey|Bearer|sb_publishable/);
});

test('permite elegir rango, tipo de registro y columnas para exportar', () => {
  const records = [
    record(10, { soil_humidity: 100 }),
    record(5, { soil_humidity: 0 }),
    record(0, { water_level: 'high', temperature: 23, air_humidity: 50, light_lux: 300 }),
  ];
  const selection = prepareHistoryExport(records, {
    from: record(5).created_at,
    to: record(10).created_at,
    status: 'abrupt',
    columns: ['created_at', 'soil_humidity', 'history_status'],
  });
  assert.equal(selection.records.length, 2);
  assert.deepEqual(selection.columns.map(column => column.field), ['created_at', 'soil_humidity', 'history_status']);
});

test('filtra por nivel bajo aunque la evaluación priorice una variación brusca', () => {
  const records = [record(10, { soil_humidity: 100 }), record(5, { soil_humidity: 0 })];
  assert.equal(prepareHistoryExport(records, { status: 'low-water' }).records.length, 2);
});

test('el CSV contiene únicamente las columnas seleccionadas', () => {
  const csv = historyCsv([record(0)], { columns: ['created_at', 'soil_humidity'] });
  assert.match(csv, /Fecha ISO/);
  assert.match(csv, /Humedad suelo %/);
  assert.doesNotMatch(csv, /Temperatura|Evaluación|Nivel de agua/);
});
