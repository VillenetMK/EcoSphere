export const HISTORY_CONFIG = Object.freeze({
  staleAfterMs: 30000,
  abruptSoilDelta: 40,
  abruptWindowMs: 15000,
  defaultPageSize: 25,
});

export const HISTORY_METRICS = Object.freeze([
  { field: 'temperature', label: 'Temperatura', unit: '°C', decimals: 1 },
  { field: 'air_humidity', label: 'Humedad del aire', unit: '%', decimals: 1 },
  { field: 'soil_humidity', label: 'Humedad del suelo', unit: '%', decimals: 0 },
  { field: 'light_lux', label: 'Iluminación', unit: 'lux', decimals: 1 },
]);

export const HISTORY_EXPORT_COLUMNS = Object.freeze([
  { field: 'created_at', label: 'Fecha ISO' },
  { field: 'temperature', label: 'Temperatura °C' },
  { field: 'air_humidity', label: 'Humedad aire %' },
  { field: 'soil_humidity', label: 'Humedad suelo %' },
  { field: 'light_lux', label: 'Iluminación lux' },
  { field: 'water_level', label: 'Nivel de agua' },
  { field: 'fan_power', label: 'Ventilador %' },
  { field: 'led_power', label: 'LED Grow %' },
  { field: 'pump_on', label: 'Bomba encendida' },
  { field: 'history_status', label: 'Evaluación' },
]);

const SENSOR_FIELDS = ['temperature', 'air_humidity', 'soil_humidity', 'light_lux'];
const VALID_WATER_LEVELS = new Set(['high', 'low']);

export function numericValue(value) {
  if (value === null || value === undefined || value === '') return null;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function timestamp(value) {
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? null : parsed;
}

function waterIsValid(value) {
  return VALID_WATER_LEVELS.has(String(value ?? '').toLowerCase());
}

export function formatDuration(durationMs) {
  if (!Number.isFinite(durationMs) || durationMs < 0) return 'Sin rango válido';
  const seconds = Math.round(durationMs / 1000);
  if (seconds < 60) return `${seconds} s`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h`;
  const days = Math.round(hours / 24);
  return `${days} ${days === 1 ? 'día' : 'días'}`;
}

export function historyAgeLabel(value, nowMillis = Date.now()) {
  const recordedAt = timestamp(value);
  if (recordedAt === null) return 'sin fecha válida';
  const age = Math.max(0, nowMillis - recordedAt);
  if (age < 5000) return 'hace unos segundos';
  return `hace ${formatDuration(age)}`;
}

export function isAbruptSoilChange(newer, older) {
  const newerValue = numericValue(newer?.soil_humidity);
  const olderValue = numericValue(older?.soil_humidity);
  const newerAt = timestamp(newer?.created_at);
  const olderAt = timestamp(older?.created_at);
  if (newerValue === null || olderValue === null || newerAt === null || olderAt === null) return false;
  const elapsed = newerAt - olderAt;
  return elapsed >= 0 &&
    elapsed <= HISTORY_CONFIG.abruptWindowMs &&
    Math.abs(newerValue - olderValue) >= HISTORY_CONFIG.abruptSoilDelta;
}

function readingCount(record) {
  const sensorValues = SENSOR_FIELDS.filter(field => numericValue(record?.[field]) !== null).length;
  return sensorValues + (waterIsValid(record?.water_level) ? 1 : 0);
}

function rowStatus(record, olderRecord) {
  if (isAbruptSoilChange(record, olderRecord)) {
    const delta = Math.abs(Number(record.soil_humidity) - Number(olderRecord.soil_humidity));
    return {
      code: 'abrupt-soil-change',
      label: 'VARIACIÓN BRUSCA',
      severity: 'warning',
      detail: `Cambio de ${Math.round(delta)} puntos en menos de 15 s.`,
    };
  }
  if (String(record?.water_level ?? '').toLowerCase() === 'low') {
    return {
      code: 'low-water',
      label: 'AGUA BAJA',
      severity: 'warning',
      detail: 'El riego estaba bloqueado en este registro.',
    };
  }
  const available = readingCount(record);
  if (available < 5) {
    return {
      code: 'partial-data',
      label: 'DATOS PARCIALES',
      severity: 'unknown',
      detail: `${available} de 5 lecturas ambientales disponibles.`,
    };
  }
  return {
    code: 'complete',
    label: 'REGISTRO COMPLETO',
    severity: 'normal',
    detail: 'Todas las lecturas ambientales están disponibles.',
  };
}

export function analyzeHistory(records, nowMillis = Date.now()) {
  const sorted = [...records].sort((a, b) => (timestamp(b.created_at) ?? 0) - (timestamp(a.created_at) ?? 0));
  const enriched = sorted.map((record, index) => ({
    ...record,
    historyStatus: rowStatus(record, sorted[index + 1]),
    availableReadings: readingCount(record),
  }));
  const dated = sorted.map(record => timestamp(record.created_at)).filter(value => value !== null);
  const newestAt = dated.length ? Math.max(...dated) : null;
  const oldestAt = dated.length ? Math.min(...dated) : null;
  const possibleReadings = sorted.length * 5;
  const availableReadings = sorted.reduce((sum, record) => sum + readingCount(record), 0);
  const lowWaterRecords = sorted.filter(record => String(record.water_level ?? '').toLowerCase() === 'low').length;
  const abruptChanges = enriched.filter(record => record.historyStatus.code === 'abrupt-soil-change').length;
  const completeRecords = enriched.filter(record => record.availableReadings === 5).length;

  return {
    records: enriched,
    total: sorted.length,
    newestAt,
    oldestAt,
    newestAgeLabel: newestAt === null ? 'sin registros' : historyAgeLabel(newestAt, nowMillis),
    stale: newestAt === null || nowMillis - newestAt > HISTORY_CONFIG.staleAfterMs,
    rangeLabel: newestAt === null || oldestAt === null ? 'Sin rango' : formatDuration(newestAt - oldestAt),
    completeness: possibleReadings ? Math.round((availableReadings / possibleReadings) * 100) : 0,
    completeRecords,
    lowWaterRecords,
    abruptChanges,
  };
}

export function paginateHistory(records, requestedPage, requestedPageSize) {
  const pageSize = Math.max(1, Number(requestedPageSize) || HISTORY_CONFIG.defaultPageSize);
  const pageCount = Math.max(1, Math.ceil(records.length / pageSize));
  const page = Math.min(pageCount, Math.max(1, Number(requestedPage) || 1));
  const start = (page - 1) * pageSize;
  const items = records.slice(start, start + pageSize);
  return {
    items,
    page,
    pageCount,
    from: records.length ? start + 1 : 0,
    to: start + items.length,
  };
}

export function buildHistoryChart(records, metricField, maxPoints = 80) {
  const metric = HISTORY_METRICS.find(candidate => candidate.field === metricField) ?? HISTORY_METRICS[2];
  const values = [...records]
    .map(record => ({ createdAt: record.created_at, time: timestamp(record.created_at), value: numericValue(record[metric.field]) }))
    .filter(point => point.time !== null && point.value !== null)
    .sort((a, b) => a.time - b.time);
  const step = Math.max(1, Math.ceil(values.length / Math.max(2, maxPoints)));
  let sampled = values.filter((point, index) => index % step === 0);
  if (values.length && sampled.at(-1) !== values.at(-1)) sampled = [...sampled, values.at(-1)];
  if (!sampled.length) return { metric, points: [], min: null, average: null, max: null };

  const allNumbers = values.map(point => point.value);
  const min = Math.min(...allNumbers);
  const max = Math.max(...allNumbers);
  const average = allNumbers.reduce((sum, value) => sum + value, 0) / allNumbers.length;
  const firstTime = sampled[0].time;
  const lastTime = sampled.at(-1).time;
  const timeSpan = Math.max(1, lastTime - firstTime);
  const valueSpan = Math.max(1, max - min);
  const points = sampled.map(point => ({
    ...point,
    x: 4 + ((point.time - firstTime) / timeSpan) * 92,
    y: 92 - ((point.value - min) / valueSpan) * 80,
  }));
  return { metric, points, min, average, max };
}

function csvCell(value) {
  const text = value === null || value === undefined ? '' : String(value);
  return `"${text.replaceAll('"', '""')}"`;
}

export function prepareHistoryExport(records, options = {}) {
  const analyzed = analyzeHistory(records);
  const requestedColumns = Array.isArray(options.columns)
    ? new Set(options.columns)
    : new Set(HISTORY_EXPORT_COLUMNS.map(column => column.field));
  const columns = HISTORY_EXPORT_COLUMNS.filter(column => requestedColumns.has(column.field));
  const from = options.from ? timestamp(options.from) : null;
  const to = options.to ? timestamp(options.to) : null;
  const inclusiveTo = to === null ? null : to + 999;
  const status = options.status ?? 'all';
  const filteredRecords = analyzed.records.filter(record => {
    const recordedAt = timestamp(record.created_at);
    if (from !== null && (recordedAt === null || recordedAt < from)) return false;
    if (inclusiveTo !== null && (recordedAt === null || recordedAt > inclusiveTo)) return false;
    if (status === 'alerts' && record.historyStatus.severity !== 'warning') return false;
    if (status === 'abrupt' && record.historyStatus.code !== 'abrupt-soil-change') return false;
    if (status === 'low-water' && String(record.water_level ?? '').toLowerCase() !== 'low') return false;
    if (status === 'partial' && record.availableReadings === 5) return false;
    if (status === 'complete' && record.availableReadings !== 5) return false;
    return true;
  });
  return { records: filteredRecords, columns };
}

function exportValue(record, field) {
  if (field === 'history_status') return record.historyStatus.label;
  return record[field];
}

export function historyCsv(records, options = {}) {
  const selection = prepareHistoryExport(records, options);
  const headers = selection.columns.map(column => column.label);
  const rows = selection.records.map(record => selection.columns.map(column => exportValue(record, column.field)));
  return [headers, ...rows].map(row => row.map(csvCell).join(',')).join('\r\n');
}
