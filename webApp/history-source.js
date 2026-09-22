/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

export function historyRange({ from, to } = {}, now = Date.now()) {
  const start = from ? Date.parse(from) : null;
  const end = to ? Date.parse(to) + 1000 : now;
  if ((start !== null && !Number.isFinite(start)) || !Number.isFinite(end)) {
    throw new Error('Selecciona fechas válidas para el historial.');
  }
  if (start !== null && start >= end) {
    throw new Error('La fecha inicial debe ser anterior a la fecha final.');
  }
  // An exclusive next-second bound includes PostgreSQL's fractional microseconds.
  return { from: start === null ? null : new Date(start).toISOString(), before: new Date(Math.min(end, now)).toISOString() };
}

function cursorFor(record) {
  if (!record) return null;
  const id = String(record.id);
  if (!/^\d+$/.test(id) || !Number.isFinite(Date.parse(record.created_at))) {
    throw new Error('El historial contiene un registro sin identificador o fecha válidos.');
  }
  // Preserve PostgreSQL's microseconds; converting this timestamp to Date would
  // skip rows that share the same millisecond at a page boundary.
  return { id, createdAt: record.created_at };
}

function historyPath(range, cursor, limit) {
  const query = new URLSearchParams({ select: '*', order: 'created_at.desc,id.desc', limit: String(limit) });
  if (range.from) query.append('created_at', `gte.${range.from}`);
  query.append('created_at', `lt.${range.before}`);
  if (cursor) {
    const time = JSON.stringify(cursor.createdAt);
    query.set('or', `(created_at.lt.${time},and(created_at.eq.${time},id.lt.${cursor.id}))`);
  }
  return `rest/v1/sensor_records?${query}`;
}

export async function loadHistoryPage(get, { range, cursor = null, pageSize = 25, signal } = {}) {
  const rows = await get(historyPath(range, cursor, pageSize + 1), { signal });
  const records = rows.slice(0, pageSize);
  return { records, hasMore: rows.length > pageSize, nextCursor: cursorFor(records.at(-1)) };
}

export async function loadHistoryRange(get, { range, signal, onProgress = () => {} } = {}) {
  const records = [];
  let cursor = null;
  while (true) {
    signal?.throwIfAborted();
    const rows = await get(historyPath(range, cursor, 500), { signal });
    signal?.throwIfAborted();
    if (!rows.length) return records;
    const nextCursor = cursorFor(rows.at(-1));
    if (cursor && nextCursor.id === cursor.id && nextCursor.createdAt === cursor.createdAt) {
      throw new Error('El historial no pudo avanzar a la siguiente página.');
    }
    records.push(...rows);
    cursor = nextCursor;
    onProgress(records.length);
    // Continue until an empty page, even if the server's row limit is below 500.
  }
}
