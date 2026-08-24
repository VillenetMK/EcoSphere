import {
  CONTROL_POLICY,
  clampPower,
  irrigationDecision,
  irrigationStatus,
  isDeviceOnline,
  waterLevelLabel,
} from './control-policy.js';
import { buildDiagnosticModel, technicalReport } from './diagnostics.js';

const BASE_URL = 'https://kslzmrddrhfyyrxyfmbw.supabase.co';
const API_KEY = 'sb_publishable_oHQqSvres8b5l0qgcpXJ2w_9A33lfg3';

let latestRecord = null;
let deviceControl = null;
let historyRecords = [];
let busy = false;
let deferredInstallPrompt = null;
let activeScreen = 'dashboard';
let refreshing = false;
let currentDiagnosticModel = null;

const $ = (id) => document.getElementById(id);

function headers(extra = {}) {
  return {
    apikey: API_KEY,
    Authorization: `Bearer ${API_KEY}`,
    ...extra,
  };
}

async function apiGet(path) {
  const response = await fetch(`${BASE_URL}/${path}`, { headers: headers() });
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  return response.json();
}

async function apiPatch(path, body) {
  const response = await fetch(`${BASE_URL}/${path}`, {
    method: 'PATCH',
    headers: headers({
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
}

async function refresh({ manual = false } = {}) {
  if (refreshing) return;
  refreshing = true;
  if (manual) setRefreshLoading(true);
  try {
    await loadLatest();
    if (activeScreen === 'history') await loadHistory();
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
  $('systemStatus').textContent = online ? 'Sistema conectado' : 'Sistema sin conexión';
  $('modeValue').textContent = deviceControl?.auto_mode ? 'Automático' : 'Manual';
  $('espValue').textContent = online ? 'Online' : 'Offline';
  $('soilStatusValue').textContent = latestRecord?.soil_humidity != null ? `${Math.round(latestRecord.soil_humidity)} %` : 'Sin registro';
  $('lastTelemetry').textContent = formatDate(latestRecord?.created_at);

  $('temperatureValue').textContent = formatNumber(latestRecord?.temperature, '°C');
  $('airHumidityValue').textContent = formatNumber(latestRecord?.air_humidity, '%');
  $('soilHumidityValue').textContent = formatNumber(latestRecord?.soil_humidity, '%');
  $('lightValue').textContent = formatNumber(latestRecord?.light_lux, 'lux');
  $('waterValue').textContent = waterLevelLabel(latestRecord?.water_level);

  const auto = !!deviceControl?.auto_mode;
  $('autoMode').checked = auto;
  $('autoMode').disabled = busy || !deviceControl;
  $('modeHint').textContent = auto ? 'El ESP32 controla los actuadores' : 'Control manual habilitado';

  const fan = Number(deviceControl?.fan_power ?? 0);
  const led = Number(deviceControl?.led_power ?? 0);
  $('fanPower').value = fan;
  $('ledPower').value = led;
  $('fanPowerLabel').textContent = `${fan} %`;
  $('ledPowerLabel').textContent = `${led} %`;
  $('fanPower').disabled = busy || auto || !deviceControl;
  $('ledPower').disabled = busy || auto || !deviceControl;
  const irrigation = irrigationDecision(
    latestRecord?.soil_humidity,
    latestRecord?.water_level,
  );
  $('pumpBtn').disabled = busy || !deviceControl || !irrigation.allowed;
  $('pumpHint').textContent = irrigationStatus(
    latestRecord?.soil_humidity,
    latestRecord?.water_level,
  );
}

function renderHistory() {
  $('historyCount').textContent = `Últimos ${historyRecords.length} registros`;
  $('historyBody').innerHTML = historyRecords.map(row => `
    <tr>
      <td>${escapeHtml(formatDate(row.created_at))}</td>
      <td>${escapeHtml(formatNumber(row.temperature, '°C'))}</td>
      <td>${escapeHtml(formatNumber(row.air_humidity, '%'))}</td>
      <td>${escapeHtml(formatNumber(row.soil_humidity, '%'))}</td>
      <td>${escapeHtml(formatNumber(row.light_lux, 'lx'))}</td>
      <td>${escapeHtml(waterLevelLabel(row.water_level))}</td>
    </tr>
  `).join('');
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
              <strong>${escapeHtml(entry.name)}</strong>
              <span class="diag-status">${escapeHtml(entry.status)}</span>
            </div>
            <p class="diag-reading">${escapeHtml(entry.reading)}</p>
            <p class="diag-detail">${escapeHtml(entry.detail)}</p>
          </article>
        `).join('')}
      </div>
    </section>
  `).join('');
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
$('diagnosticsRefreshBtn').addEventListener('click', () => refresh({ manual: true }));
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
  const decision = irrigationDecision(
    latestRecord?.soil_humidity,
    latestRecord?.water_level,
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
  });
});

window.addEventListener('beforeinstallprompt', event => {
  event.preventDefault();
  deferredInstallPrompt = event;
  $('installBtn').hidden = false;
});

$('installBtn').addEventListener('click', async () => {
  if (!deferredInstallPrompt) return;
  deferredInstallPrompt.prompt();
  await deferredInstallPrompt.userChoice;
  deferredInstallPrompt = null;
  $('installBtn').hidden = true;
});

window.addEventListener('appinstalled', () => {
  deferredInstallPrompt = null;
  $('installBtn').hidden = true;
});

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js'));
}

refresh();
setInterval(() => refresh(), 2000);
