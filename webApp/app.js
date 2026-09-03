/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import {
  CONTROL_POLICY,
  actuatorPwmLabel,
  actuatorSwitchLabel,
  clampPower,
  irrigationDecision,
  irrigationStatus,
  isDeviceOnline,
  isTelemetryCurrent,
  waterLevelLabel,
} from './control-policy.js';
import { buildDiagnosticModel, technicalReport } from './diagnostics.js';
import {
  HISTORY_CONFIG,
  analyzeHistory,
  buildHistoryChart,
  historyCsv,
  paginateHistory,
  prepareHistoryExport,
} from './history.js';
import { initializeAuth } from './auth.js';
import { SUPABASE_PUBLISHABLE_KEY, SUPABASE_URL, supabase } from './supabase-client.js';

let latestRecord = null;
let deviceControl = null;
let historyRecords = [];
let busy = false;
let activeScreen = 'dashboard';
let refreshing = false;
let currentDiagnosticModel = null;
let lastHistoryLoadedAt = 0;
let historyPage = 1;
let historyPageSize = HISTORY_CONFIG.defaultPageSize;
let historyMetric = 'soil_humidity';
let refreshTimer = null;
let currentProfile = null;
let controllerStatus = null;

const $ = (id) => document.getElementById(id);

const ICON_BASE = './icons';
const HISTORY_METRIC_ICONS = {
  soil_humidity: 'ic_soil_humidity',
  temperature: 'ic_temperature',
  air_humidity: 'ic_air_humidity',
  light_lux: 'ic_light',
};
const DIAGNOSTIC_ICONS = {
  ESP32: 'ic_esp32',
  'Telemetría': 'ic_history',
  BME280: 'ic_temperature',
  BH1750: 'ic_light',
  'Humedad de suelo': 'ic_soil_humidity',
  'Nivel de agua': 'ic_water_level',
  Ventilador: 'ic_fan',
  'LED Grow': 'ic_grow_led',
  Bomba: 'ic_pump',
};
const SEVERITY_ICONS = {
  critical: 'ic_error',
  warning: 'ic_warning',
  unknown: 'ic_info',
  normal: 'ic_ok',
};

function iconPath(name) {
  return `${ICON_BASE}/${name}.svg`;
}

function iconMarkup(name, className = 'ui-icon') {
  return `<img class="${className}" src="${iconPath(name)}" alt="" aria-hidden="true" />`;
}

async function headers(extra = {}) {
  const { data: { session }, error } = await supabase.auth.getSession();
  if (error) throw error;
  if (!session?.access_token) throw new Error('La sesión venció. Inicia sesión nuevamente.');
  return {
    apikey: SUPABASE_PUBLISHABLE_KEY,
    Authorization: `Bearer ${session.access_token}`,
    ...extra,
  };
}

async function apiGet(path) {
  const response = await fetch(`${SUPABASE_URL}/${path}`, { headers: await headers() });
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  return response.json();
}

async function apiPatch(path, body) {
  const response = await fetch(`${SUPABASE_URL}/${path}`, {
    method: 'PATCH',
    headers: await headers({
      'Content-Type': 'application/json',
      Prefer: 'return=representation',
    }),
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  return response.json();
}

async function apiPost(path, body = {}) {
  const response = await fetch(`${SUPABASE_URL}/${path}`, {
    method: 'POST',
    headers: await headers({
      'Content-Type': 'application/json',
      Prefer: 'return=representation',
    }),
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  return response.json();
}

async function loadLatest() {
  const [records, controls] = await Promise.all([
    apiGet('rest/v1/sensor_records?select=*&order=created_at.desc&limit=1'),
    apiGet('rest/v1/device_control?id=eq.1&select=*'),
  ]);
  latestRecord = records[0] ?? null;
  deviceControl = controls[0] ?? null;
}

async function loadHistory() {
  historyRecords = await apiGet('rest/v1/sensor_records?select=*&order=created_at.desc&limit=200');
  lastHistoryLoadedAt = Date.now();
}

async function loadControllerStatus() {
  if (currentProfile?.role !== 'admin') {
    controllerStatus = null;
    return;
  }
  const result = await apiPost('rest/v1/rpc/controller_admin_status');
  controllerStatus = result[0] ?? null;
}

async function refresh({ manual = false } = {}) {
  if (refreshing) return;
  refreshing = true;
  if (manual) setRefreshLoading(true);
  try {
    await loadLatest();
    if (activeScreen === 'history' && (manual || Date.now() - lastHistoryLoadedAt >= 30000)) {
      await loadHistory();
    }
    if (activeScreen === 'diagnostics' && currentProfile?.role === 'admin') {
      await loadControllerStatus();
    }
    hideError();
    renderAll();
  } catch (error) {
    showError(error.message || 'Error sincronizando con Supabase');
  } finally {
    if (manual) setRefreshLoading(false);
    refreshing = false;
  }
}

function onlineNow(control) {
  return isDeviceOnline(control);
}

function formatNumber(value, unit) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '--';
  return `${Number(value).toFixed(1)} ${unit}`;
}

function formatDate(value) {
  if (!value) return 'Sin registro';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('es-PE', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

function renderAll() {
  renderDashboard();
  renderHistory();
  renderDiagnostics();
}

function renderDashboard() {
  const online = onlineNow(deviceControl);
  const telemetryCurrent = isTelemetryCurrent(latestRecord, deviceControl);
  const currentRecord = telemetryCurrent ? latestRecord : null;
  const auto = !!deviceControl?.auto_mode;
  $('systemStatus').textContent = online ? 'Sistema conectado' : 'Sistema sin conexión';
  $('modeValue').textContent = auto ? 'Automático' : 'Manual';
  $('modeIcon').src = iconPath(auto ? 'ic_auto_mode' : 'ic_manual_mode');
  $('lastEsp32').textContent = formatDate(deviceControl?.last_seen_at);
  $('espIcon').src = iconPath(online ? 'ic_online' : 'ic_offline');
  $('lastTelemetry').textContent = formatDate(latestRecord?.created_at);

  const hasTelemetry = latestRecord !== null;
  $('emptyTelemetry').hidden = hasTelemetry;
  $('metricsGrid').hidden = !hasTelemetry;

  $('temperatureValue').textContent = formatNumber(currentRecord?.temperature, '°C');
  $('airHumidityValue').textContent = formatNumber(currentRecord?.air_humidity, '%');
  $('soilHumidityValue').textContent = formatNumber(currentRecord?.soil_humidity, '%');
  $('lightValue').textContent = formatNumber(currentRecord?.light_lux, 'lux');
  $('waterValue').textContent = currentRecord ? waterLevelLabel(currentRecord.water_level) : '--';

  const reportedMode = telemetryCurrent ? latestRecord?.auto_mode : null;
  $('fanState').textContent = telemetryCurrent
    ? actuatorPwmLabel(latestRecord?.fan_on, latestRecord?.fan_power)
    : 'SIN CONFIRMAR';
  $('pumpState').textContent = telemetryCurrent
    ? actuatorSwitchLabel(latestRecord?.pump_on)
    : 'SIN CONFIRMAR';
  $('ledState').textContent = telemetryCurrent
    ? actuatorPwmLabel(latestRecord?.led_on, latestRecord?.led_power)
    : 'SIN CONFIRMAR';
  $('controlState').textContent = reportedMode == null
    ? telemetryCurrent ? 'SIN REGISTRO' : 'SIN CONFIRMAR'
    : reportedMode ? 'AUTOMÁTICO' : 'MANUAL';
  $('reportedModeIcon').src = iconPath(reportedMode == null ? 'ic_offline' : reportedMode ? 'ic_auto_mode' : 'ic_manual_mode');

  $('autoMode').checked = auto;
  const canOperate = ['operator', 'admin'].includes(currentProfile?.role);
  $('autoMode').disabled = busy || !deviceControl || !canOperate;
  $('modeHint').textContent = auto ? 'El ESP32 controla los actuadores' : 'Control manual habilitado';

  const fan = Number(deviceControl?.fan_power ?? 0);
  const led = Number(deviceControl?.led_power ?? 0);
  $('fanPower').value = fan;
  $('ledPower').value = led;
  $('fanPowerLabel').textContent = `${fan} %`;
  $('ledPowerLabel').textContent = `${led} %`;
  $('fanPower').disabled = busy || auto || !deviceControl || !canOperate;
  $('ledPower').disabled = busy || auto || !deviceControl || !canOperate;
  const irrigation = irrigationDecision(
    currentRecord?.soil_humidity,
    currentRecord?.water_level,
  );
  $('pumpBtn').disabled = busy || !deviceControl || !irrigation.allowed || !canOperate;
  $('pumpHint').textContent = irrigationStatus(
    currentRecord?.soil_humidity,
    currentRecord?.water_level,
  );
}

function renderHistory() {
  const analysis = analyzeHistory(historyRecords);
  const pagination = paginateHistory(analysis.records, historyPage, historyPageSize);
  historyPage = pagination.page;
  $('historyCount').textContent = historyRecords.length
    ? `${analysis.total} registros · última lectura ${analysis.newestAgeLabel}`
    : 'Esperando registros históricos';

  const notice = $('historyNotice');
  notice.className = `history-notice ${analysis.stale ? 'severity-warning' : 'severity-normal'}`;
  $('historyNoticeTitle').textContent = analysis.stale ? 'Historial sin datos recientes' : 'Historial actualizado';
  $('historyNoticeDetail').textContent = analysis.total
    ? analysis.stale
      ? `La última lectura llegó ${analysis.newestAgeLabel}. Este historial sirve para análisis, pero no representa el estado actual.`
      : 'Las lecturas más recientes están dentro del intervalo esperado.'
    : 'Todavía no se han recibido registros para analizar.';

  $('historySummary').innerHTML = [
    ['ic_history', 'Registros cargados', analysis.total, `Rango temporal: ${analysis.rangeLabel}`],
    ['ic_info', 'Datos disponibles', `${analysis.completeness} %`, `${analysis.completeRecords} registros completos`],
    ['ic_water_level', 'Agua baja', analysis.lowWaterRecords, analysis.lowWaterRecords ? 'Riego bloqueado en esos registros' : 'Sin eventos detectados'],
    ['ic_soil_humidity', 'Saltos del suelo', analysis.abruptChanges, analysis.abruptChanges ? 'Cambios ≥ 40 puntos en ≤ 15 s' : 'Sin variaciones bruscas'],
  ].map(([icon, label, value, detail]) => `
    <article class="history-summary-card">
      <div class="history-summary-label">${iconMarkup(icon, 'ui-icon summary-icon')}<span>${escapeHtml(label)}</span></div>
      <strong>${escapeHtml(value)}</strong>
      <small>${escapeHtml(detail)}</small>
    </article>
  `).join('');

  renderHistoryChart(analysis.records);
  $('historyBody').innerHTML = pagination.items.map(row => `
    <tr>
      <td class="history-date">${escapeHtml(formatDate(row.created_at))}</td>
      <td>${historyReading(row.temperature, '°C')}</td>
      <td>${historyReading(row.air_humidity, '%')}</td>
      <td>${historyReading(row.soil_humidity, '%', 0)}</td>
      <td>${historyReading(row.light_lux, 'lux')}</td>
      <td>${escapeHtml(waterLevelLabel(row.water_level))}</td>
      <td>
        <span class="history-row-status severity-${escapeHtml(row.historyStatus.severity)}">${escapeHtml(row.historyStatus.label)}</span>
        <small class="history-row-detail">${escapeHtml(row.historyStatus.detail)}</small>
      </td>
    </tr>
  `).join('');
  $('historyPageStatus').textContent = analysis.total
    ? `Mostrando ${pagination.from}–${pagination.to} de ${analysis.total}`
    : 'Sin registros';
  $('historyPageNumber').textContent = `Página ${pagination.page} de ${pagination.pageCount}`;
  $('historyPrevBtn').disabled = pagination.page <= 1;
  $('historyNextBtn').disabled = pagination.page >= pagination.pageCount;
}

function historyReading(value, unit, decimals = 1) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '<span class="missing-reading">Sin dato</span>';
  }
  return `${escapeHtml(Number(value).toFixed(decimals))} ${escapeHtml(unit)}`;
}

function renderHistoryChart(records) {
  const chart = buildHistoryChart(records, historyMetric);
  const format = value => value === null
    ? '--'
    : `${Number(value).toFixed(chart.metric.decimals)} ${chart.metric.unit}`;
  $('historyChartTitle').textContent = chart.metric.label;
  $('historyChartIcon').src = iconPath(HISTORY_METRIC_ICONS[chart.metric.field] ?? 'ic_history');
  $('historyChartStats').innerHTML = [
    ['Mínimo', format(chart.min)],
    ['Promedio', format(chart.average)],
    ['Máximo', format(chart.max)],
  ].map(([label, value]) => `<span><small>${escapeHtml(label)}</small><strong>${escapeHtml(value)}</strong></span>`).join('');

  if (!chart.points.length) {
    $('historyChart').innerHTML = '<div class="history-chart-empty"><strong>Sin datos para esta métrica</strong><span>Selecciona otra lectura para visualizar su tendencia.</span></div>';
    return;
  }
  const points = chart.points.map(point => `${point.x.toFixed(2)},${point.y.toFixed(2)}`).join(' ');
  const first = chart.points[0];
  const last = chart.points.at(-1);
  $('historyChart').innerHTML = `
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="Tendencia de ${escapeHtml(chart.metric.label)}">
      <line x1="4" y1="12" x2="96" y2="12" class="chart-grid-line" />
      <line x1="4" y1="52" x2="96" y2="52" class="chart-grid-line" />
      <line x1="4" y1="92" x2="96" y2="92" class="chart-grid-line" />
      <polyline points="${points}" class="chart-line" />
    </svg>
    <div class="history-chart-axis"><span>${escapeHtml(formatDate(first.createdAt))}</span><span>${escapeHtml(formatDate(last.createdAt))}</span></div>
  `;
}

function renderDiagnostics() {
  currentDiagnosticModel = buildDiagnosticModel(latestRecord, deviceControl);
  const model = currentDiagnosticModel;
  const summary = $('diagnosticSummary');
  summary.className = `diagnostic-summary severity-${model.severity}`;
  $('diagnosticSummaryTitle').textContent = model.headline;
  $('diagnosticSummaryDetail').textContent = model.summary;
  $('diagnosticSummaryCounts').innerHTML = [
    ['Críticos', model.counts.critical, 'critical'],
    ['Advertencias', model.counts.warning, 'warning'],
    ['Sin datos', model.counts.unknown, 'unknown'],
  ].map(([label, count, severity]) => `
    <span class="summary-count severity-${severity}"><strong>${count}</strong> ${escapeHtml(label)}</span>
  `).join('');

  $('diagnosticGroups').innerHTML = model.groups.map(group => `
    <section class="diagnostic-group diagnostic-group-${escapeHtml(group.id)}" aria-labelledby="diagnostic-${escapeHtml(group.id)}-title">
      <header class="diagnostic-group-header">
        <h3 id="diagnostic-${escapeHtml(group.id)}-title">${escapeHtml(group.title)}</h3>
        <p>${escapeHtml(group.description)}</p>
      </header>
      <div class="diagnostic-cards">
        ${group.items.map(entry => `
          <article class="diag-card severity-${escapeHtml(entry.severity)}">
            <div class="diag-card-head">
              <div class="diag-card-name">${iconMarkup(DIAGNOSTIC_ICONS[entry.name] ?? 'ic_info', 'ui-icon diag-name-icon')}<strong>${escapeHtml(entry.name)}</strong></div>
              <span class="diag-status">${iconMarkup(SEVERITY_ICONS[entry.severity] ?? 'ic_info', 'ui-icon diag-status-icon')}${escapeHtml(entry.status)}</span>
            </div>
            <p class="diag-reading">${escapeHtml(entry.reading)}</p>
            <p class="diag-detail">${escapeHtml(entry.detail)}</p>
          </article>
        `).join('')}
      </div>
    </section>
  `).join('');

  const pairingPanel = $('controllerPairingPanel');
  const isAdmin = currentProfile?.role === 'admin';
  pairingPanel.hidden = !isAdmin;
  if (isAdmin) {
    const statusLabel = controllerStatus?.controller_status === 'active'
      ? 'Controlador activo'
      : 'Aún no hay un controlador seguro vinculado';
    const identity = controllerStatus?.hardware_uid_masked
      ? ` · ${controllerStatus.hardware_uid_masked}`
      : '';
    const firmware = controllerStatus?.firmware_version
      ? ` · firmware ${controllerStatus.firmware_version}`
      : '';
    $('controllerStatusTitle').textContent = statusLabel;
    $('controllerStatusDetail').textContent = controllerStatus?.secure_mode
      ? `Modo seguro habilitado${identity}${firmware}`
      : `Modo de transición activo${identity}${firmware}`;
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function showError(message) {
  const box = $('errorBox');
  box.textContent = message;
  box.hidden = false;
}

function hideError() {
  $('errorBox').hidden = true;
}

let toastTimer;
function toast(message) {
  const el = $('toast');
  el.textContent = message;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 4500);
}

function setBusy(value) {
  busy = value;
  renderDashboard();
}

function setRefreshLoading(value) {
  [
    ['refreshBtn', 'refreshBtnLabel', 'Actualizar datos'],
    ['historyRefreshBtn', 'historyRefreshLabel', 'Actualizar historial'],
    ['diagnosticsRefreshBtn', 'diagnosticsRefreshLabel', 'Actualizar diagnóstico'],
  ].forEach(([buttonId, labelId, idleLabel]) => {
    const button = $(buttonId);
    if (!button) return;
    button.disabled = value;
    button.classList.toggle('loading', value);
    $(labelId).textContent = value ? 'Actualizando...' : idleLabel;
  });
}

async function updateControl(body) {
  if (!['operator', 'admin'].includes(currentProfile?.role)) {
    toast('Tu cuenta tiene acceso de sólo lectura.');
    return;
  }
  setBusy(true);
  try {
    const result = await apiPatch('rest/v1/device_control?id=eq.1', body);
    deviceControl = result[0] ?? deviceControl;
    await refresh();
  } catch (error) {
    toast(error.message || 'Error actualizando el control');
  } finally {
    setBusy(false);
  }
}

$('refreshBtn').addEventListener('click', () => refresh({ manual: true }));
$('historyRefreshBtn').addEventListener('click', () => refresh({ manual: true }));
$('historyMetric').addEventListener('change', event => {
  historyMetric = event.target.value;
  renderHistory();
});
$('historyPageSize').addEventListener('change', event => {
  historyPageSize = Number(event.target.value);
  historyPage = 1;
  renderHistory();
});
$('historyPrevBtn').addEventListener('click', () => {
  historyPage -= 1;
  renderHistory();
});
$('historyNextBtn').addEventListener('click', () => {
  historyPage += 1;
  renderHistory();
});
$('historyExportBtn').addEventListener('click', () => {
  if (!historyRecords.length) {
    toast('No hay registros para exportar.');
    return;
  }
  const analysis = analyzeHistory(historyRecords);
  $('historyExportFrom').value = localDateTimeInput(analysis.oldestAt);
  $('historyExportTo').value = localDateTimeInput(analysis.newestAt, true);
  $('historyExportStatus').value = 'all';
  document.querySelectorAll('input[name="historyExportColumn"]').forEach(input => { input.checked = true; });
  updateHistoryExportPreview();
  const dialog = $('historyExportDialog');
  if (typeof dialog.showModal === 'function') dialog.showModal();
  else dialog.setAttribute('open', '');
});

function localDateTimeInput(value, roundUp = false) {
  if (value === null || value === undefined) return '';
  const date = new Date(Number(value) + (roundUp ? 1000 : 0));
  if (Number.isNaN(date.getTime())) return '';
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 19);
}

function historyExportOptions() {
  const fromValue = $('historyExportFrom').value;
  const toValue = $('historyExportTo').value;
  return {
    from: fromValue ? new Date(fromValue).toISOString() : null,
    to: toValue ? new Date(toValue).toISOString() : null,
    status: $('historyExportStatus').value,
    columns: [...document.querySelectorAll('input[name="historyExportColumn"]:checked')].map(input => input.value),
  };
}

function updateHistoryExportPreview() {
  const options = historyExportOptions();
  const selection = prepareHistoryExport(historyRecords, options);
  const preview = $('historyExportPreview');
  if (!selection.columns.length) {
    preview.innerHTML = '<strong>Selecciona al menos una columna.</strong><span>No se generará ningún archivo hasta elegirla.</span>';
  } else if (!selection.records.length) {
    preview.innerHTML = '<strong>No hay registros con esos filtros.</strong><span>Cambia el rango o el tipo de registro.</span>';
  } else {
    preview.innerHTML = `<strong>${selection.records.length} registros listos</strong><span>${selection.columns.length} columnas serán incluidas en el CSV.</span>`;
  }
  $('historyExportDownloadBtn').disabled = !selection.records.length || !selection.columns.length;
}

$('historyExportForm').addEventListener('input', updateHistoryExportPreview);
$('historyExportForm').addEventListener('change', updateHistoryExportPreview);
$('historyExportAllColumns').addEventListener('click', () => {
  document.querySelectorAll('input[name="historyExportColumn"]').forEach(input => { input.checked = true; });
  updateHistoryExportPreview();
});
$('historyExportNoColumns').addEventListener('click', () => {
  document.querySelectorAll('input[name="historyExportColumn"]').forEach(input => { input.checked = false; });
  updateHistoryExportPreview();
});
$('historyExportDownloadBtn').addEventListener('click', () => {
  const options = historyExportOptions();
  const selection = prepareHistoryExport(historyRecords, options);
  if (!selection.records.length || !selection.columns.length) return;
  const blob = new Blob([`\ufeff${historyCsv(historyRecords, options)}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `EcoSphere-historial-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
  $('historyExportDialog').close();
  toast(`${selection.records.length} registros exportados en CSV.`);
});
$('diagnosticsRefreshBtn').addEventListener('click', () => refresh({ manual: true }));
$('controllerPairingForm').addEventListener('submit', async event => {
  event.preventDefault();
  if (currentProfile?.role !== 'admin') return;
  const input = $('controllerPairingCode');
  const code = input.value.trim();
  const button = $('replaceControllerBtn');
  button.disabled = true;
  try {
    const result = await apiPost('rest/v1/rpc/replace_active_controller', {
      p_pairing_code: code,
    });
    controllerStatus = result[0] ?? controllerStatus;
    input.value = '';
    await refresh({ manual: true });
    toast('Controlador reemplazado. El sistema conservará su historial y configuración.');
  } catch (error) {
    toast(error.message || 'No se pudo vincular el controlador.');
  } finally {
    button.disabled = false;
  }
});
$('copyDiagnosticsBtn').addEventListener('click', async () => {
  if (!currentDiagnosticModel) return;
  const report = technicalReport(currentDiagnosticModel);
  try {
    await navigator.clipboard.writeText(report);
    toast('Reporte técnico copiado.');
  } catch {
    const textarea = document.createElement('textarea');
    textarea.value = report;
    textarea.setAttribute('readonly', '');
    textarea.className = 'copy-helper';
    document.body.appendChild(textarea);
    textarea.select();
    const copied = document.execCommand('copy');
    textarea.remove();
    toast(copied ? 'Reporte técnico copiado.' : 'No se pudo copiar el reporte.');
  }
});

$('autoMode').addEventListener('change', event => {
  updateControl({ auto_mode: event.target.checked });
});

$('fanPower').addEventListener('input', event => {
  $('fanPowerLabel').textContent = `${event.target.value} %`;
});
$('fanPower').addEventListener('change', event => {
  const power = clampPower(event.target.value);
  updateControl({ fan_power: power, fan_target: power > 0 });
});

$('ledPower').addEventListener('input', event => {
  $('ledPowerLabel').textContent = `${event.target.value} %`;
});
$('ledPower').addEventListener('change', event => {
  const power = clampPower(event.target.value);
  updateControl({ led_power: power, led_target: power > 0 });
});

$('pumpBtn').addEventListener('click', async () => {
  const currentRecord = isTelemetryCurrent(latestRecord, deviceControl) ? latestRecord : null;
  const decision = irrigationDecision(
    currentRecord?.soil_humidity,
    currentRecord?.water_level,
  );
  if (!decision.allowed) {
    toast(decision.message);
    return;
  }
  await updateControl({
    pump_request: Number(deviceControl?.pump_request ?? 0) + 1,
    pump_duration_ms: CONTROL_POLICY.pumpDurationMs,
  });
});

document.querySelectorAll('.nav-item').forEach(button => {
  button.addEventListener('click', async () => {
    activeScreen = button.dataset.screen;
    document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b === button));
    document.querySelectorAll('.screen').forEach(s => s.classList.toggle('active', s.id === activeScreen));
    if (activeScreen === 'history') {
      try { await loadHistory(); renderHistory(); }
      catch (error) { showError(error.message || 'Error cargando historial'); }
    }
    if (activeScreen === 'diagnostics' && currentProfile?.role === 'admin') {
      try { await loadControllerStatus(); renderDiagnostics(); }
      catch (error) { showError(error.message || 'Error cargando el controlador'); }
    }
  });
});

const downloadsDialog = $('downloadsDialog');
function openDownloads() {
  if (typeof downloadsDialog.showModal === 'function') downloadsDialog.showModal();
  else downloadsDialog.setAttribute('open', '');
}
$('downloadsBtn').addEventListener('click', openDownloads);
$('authDownloadsBtn').addEventListener('click', openDownloads);
$('downloadsCloseBtn').addEventListener('click', () => downloadsDialog.close());
downloadsDialog.addEventListener('click', event => {
  if (event.target === downloadsDialog) downloadsDialog.close();
});

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js'));
}

function startApplication({ profile }) {
  currentProfile = profile;
  $('authGate').hidden = true;
  $('app').hidden = false;
  $('currentUserName').textContent = profile.username || profile.full_name;
  $('currentUserRole').textContent = profile.role === 'admin'
    ? 'Administrador'
    : profile.role === 'operator' ? 'Operador' : 'Sólo lectura';
  if (!refreshTimer) {
    refresh();
    refreshTimer = setInterval(() => refresh(), 2000);
  }
}

function stopApplication() {
  $('app').hidden = true;
  $('authGate').hidden = false;
  if (refreshTimer) clearInterval(refreshTimer);
  refreshTimer = null;
  latestRecord = null;
  deviceControl = null;
  historyRecords = [];
  controllerStatus = null;
  currentProfile = null;
}

initializeAuth({
  onAccessGranted: startApplication,
  onSignedOut: stopApplication,
}).catch(error => {
  const message = $('authMessage');
  message.textContent = error.message || 'No se pudo iniciar el acceso seguro.';
  message.dataset.kind = 'error';
  message.hidden = false;
});
