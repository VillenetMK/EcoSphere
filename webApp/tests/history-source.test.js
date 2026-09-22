/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { historyRange, loadHistoryPage, loadHistoryRange } from '../history-source.js';
import { historyCsv } from '../history.js';

const now = Date.parse('2026-09-22T12:00:00Z');
const records = Array.from({ length: 725 }, (_, i) => ({
  id: 725 - i, created_at: new Date(now - 1000 - Math.floor(i / 3) * 1000).toISOString(),
  soil_humidity: 30, water_level: 'high',
}));

// A fake HTTP boundary interprets the actual PostgREST query sent by the client.
// New arrivals, tied timestamps and server row caps exercise pagination behavior.
function backend(rows = records, cap = 1000) {
  const paths = [];
  const get = async path => {
    paths.push(path);
    const query = new URL(path, 'https://example.invalid').searchParams;
    assert.equal(query.get('order'), 'created_at.desc,id.desc');
    let result = [...rows];
    for (const filter of query.getAll('created_at')) {
      const separator = filter.indexOf('.');
      const operator = filter.slice(0, separator);
      const time = Date.parse(filter.slice(separator + 1));
      result = result.filter(row => operator === 'gte' ? Date.parse(row.created_at) >= time : Date.parse(row.created_at) < time);
    }
    if (query.has('or')) {
      const match = query.get('or').match(/^\(created_at\.lt\.("[^"]+"),and\(created_at\.eq\.("[^"]+"),id\.lt\.(\d+)\)\)$/);
      assert.ok(match, 'cursor is a valid compound PostgREST filter');
      assert.equal(match[1], match[2]);
      const time = JSON.parse(match[1]);
      const id = Number(match[3]);
      result = result.filter(row => row.created_at < time || (row.created_at === time && row.id < id));
    }
    return result.sort((a, b) => b.created_at.localeCompare(a.created_at) || b.id - a.id).slice(0, Math.min(cap, Number(query.get('limit'))));
  };
  return { get, paths };
}

test('navega más allá de 200 lecturas sin duplicar ni omitir fechas iguales', async () => {
  const { get } = backend();
  const range = historyRange({}, now);
  const ids = [];
  let cursor = null;
  let page;
  do {
    page = await loadHistoryPage(get, { range, cursor, pageSize: 25 });
    ids.push(...page.records.map(row => row.id));
    cursor = page.nextCursor;
  } while (page.hasMore);
  assert.deepEqual(ids, records.map(row => row.id));
});

test('consulta fechas antiguas en el servidor y exporta filas ajenas a la página visible', async () => {
  const { get, paths } = backend();
  const options = { from: records[600].created_at, to: records[400].created_at };
  const result = await loadHistoryRange(get, { range: historyRange(options, now) });
  assert.ok(result.length > 200);
  assert.ok(result.every(row => row.id < 400));
  assert.equal(new URL(paths[0], 'https://example.invalid').searchParams.getAll('created_at').length, 2);
  const csv = historyCsv(result, { ...options, columns: ['created_at', 'soil_humidity'] });
  assert.equal(csv.split('\r\n').length, result.length + 1);
});

test('la exportación continúa cuando el servidor limita cada respuesta a menos de 500 filas', async () => {
  const { get, paths } = backend(records, 37);
  const counts = [];
  const result = await loadHistoryRange(get, { range: historyRange({}, now), onProgress: count => counts.push(count) });
  assert.deepEqual(result, records);
  assert.ok(paths.length > 10);
  assert.equal(counts.at(-1), 725);
});

test('las lecturas nuevas no desplazan las páginas del intervalo consultado', async () => {
  const rows = [...records];
  const { get } = backend(rows);
  const range = historyRange({}, now);
  const first = await loadHistoryPage(get, { range });
  rows.unshift({ ...records[0], id: 726, created_at: new Date(now + 1000).toISOString() });
  const second = await loadHistoryPage(get, { range, cursor: first.nextCursor });
  assert.deepEqual([...first.records, ...second.records], records.slice(0, 50));
});

test('conserva los microsegundos del cursor de PostgreSQL', async () => {
  const rows = [
    { id: 3, created_at: '2026-09-22T11:59:59.123999Z' },
    { id: 2, created_at: '2026-09-22T11:59:59.123500Z' },
    { id: 1, created_at: '2026-09-22T11:59:59.123100Z' },
  ];
  const { get } = backend(rows);
  const range = historyRange({}, now);
  const first = await loadHistoryPage(get, { range, pageSize: 1 });
  const second = await loadHistoryPage(get, { range, pageSize: 1, cursor: first.nextCursor });
  assert.equal(second.records[0].id, 2);
});

test('cancelar la exportación impide consultar o descargar más páginas', async () => {
  const controller = new AbortController();
  const { get, paths } = backend();
  await assert.rejects(loadHistoryRange(get, {
    range: historyRange({}, now), signal: controller.signal,
    onProgress: () => controller.abort(),
  }), { name: 'AbortError' });
  assert.equal(paths.length, 1);
});

test('un error en una página intermedia no devuelve un CSV incompleto', async () => {
  const { get } = backend();
  let calls = 0;
  await assert.rejects(loadHistoryRange(async (...args) => {
    if (++calls > 1) throw new Error('Network unavailable');
    return get(...args);
  }, { range: historyRange({}, now) }), /Network unavailable/);
});

test('rechaza intervalos inválidos e incluye el último segundo seleccionado', () => {
  assert.throws(() => historyRange({ from: 'invalid' }, now), /fechas válidas/);
  assert.throws(() => historyRange({ from: '2026-09-22T11:00:00Z', to: '2026-09-21T11:00:00Z' }, now), /anterior/);
  assert.equal(historyRange({ to: '2026-09-22T11:00:00Z' }, now).before, '2026-09-22T11:00:01.000Z');
});

test('incluye lecturas con microsegundos al final del segundo elegido', async () => {
  const { get } = backend([{ id: 1, created_at: '2026-09-22T11:00:00.999999Z' }]);
  const result = await loadHistoryRange(get, {
    range: historyRange({ from: '2026-09-22T11:00:00Z', to: '2026-09-22T11:00:00Z' }, now),
  });
  assert.equal(result.length, 1);
});
