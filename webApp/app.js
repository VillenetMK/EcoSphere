const BASE_URL = 'https://kslzmrddrhfyyrxyfmbw.supabase.co';
const API_KEY = 'sb_publishable_oHQqSvres8b5l0qgcpXJ2w_9A33lfg3';
const SOIL_DENY = 60;

let latestRecord = null;
let deviceControl = null;
let historyRecords = [];
let busy = false;
let deferredInstallPrompt = null;
let activeScreen = 'dashboard';

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
  }
}

function onlineNow(control) {
  if (!control?.esp32_online || !control?.last_seen_at) return false;
  const ms = Date.parse(control.last_seen_at);
  if (Number.isNaN(ms)) return false;
  const age = Date.now() - ms;
  return age >= -60000 && age <= 30000;
}

function formatNumber(value, unit) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '--';
  return `${Number(value).toFixed(1)} ${unit}`;
}

function waterLabel(value) {
  const v = String(value ?? '').toLowerCase();
  if (['high', 'normal', 'ok'].includes(v)) return 'Disponible';
  if (v === 'low') return 'Bajo';
  return 'Sin registro';
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
  $('waterValue').textContent = waterLabel(latestRecord?.water_level);

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
  $('pumpBtn').disabled = busy || auto || !deviceControl;

  const soil = latestRecord?.soil_humidity;
  $('pumpHint').textContent = soil == null
    ? 'Sin lectura de humedad'
    : soil >= SOIL_DENY
      ? `Suelo húmedo: ${Math.round(soil)} %`
      : `Humedad actual: ${Math.round(soil)} %`;
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
      <td>${escapeHtml(waterLabel(row.water_level))}</td>
    </tr>
  `).join('');
}

function renderDiagnostics() {
  const rows = [
    ['ESP32', onlineNow(deviceControl) ? 'OK' : 'SIN CONEXIÓN', deviceControl?.last_seen_at ? formatDate(deviceControl.last_seen_at) : 'Sin heartbeat'],
    ['BME280', latestRecord?.temperature != null && latestRecord?.air_humidity != null ? 'OK' : 'SIN CONFIRMAR', 'Temperatura y humedad del aire'],
    ['BH1750', latestRecord?.light_lux != null ? 'OK' : 'SIN CONFIRMAR', 'Sensor de iluminación'],
    ['Humedad de suelo', latestRecord?.soil_humidity != null ? 'OK' : 'SIN CONFIRMAR', latestRecord?.soil_humidity != null ? `${Math.round(latestRecord.soil_humidity)} %` : 'Sin lectura'],
    ['Nivel de agua', latestRecord?.water_level != null ? 'OK' : 'SIN CONFIRMAR', waterLabel(latestRecord?.water_level)],
    ['Ventilador', 'ESTADO', `${latestRecord?.fan_power ?? deviceControl?.fan_power ?? 0} %`],
    ['LED Grow', 'ESTADO', `${latestRecord?.led_power ?? deviceControl?.led_power ?? 0} %`],
    ['Bomba', 'ESTADO', latestRecord?.pump_on ? 'Encendida' : 'Apagada'],
  ];
  $('diagnosticsList').innerHTML = rows.map(([name, status, detail]) => `
    <article class="diag-row">
      <div><strong>${escapeHtml(name)}</strong><small>${escapeHtml(detail)}</small></div>
      <span class="diag-status">${escapeHtml(status)}</span>
    </article>
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
  const btn = $('refreshBtn');
  btn.disabled = value;
  btn.classList.toggle('loading', value);
  btn.lastChild.textContent = value ? ' Actualizando...' : ' Actualizar datos';
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

$('autoMode').addEventListener('change', event => {
  updateControl({ auto_mode: event.target.checked });
});

$('fanPower').addEventListener('input', event => {
  $('fanPowerLabel').textContent = `${event.target.value} %`;
});
$('fanPower').addEventListener('change', event => {
  const power = Number(event.target.value);
  updateControl({ fan_power: power, fan_target: power > 0 });
});

$('ledPower').addEventListener('input', event => {
  $('ledPowerLabel').textContent = `${event.target.value} %`;
});
$('ledPower').addEventListener('change', event => {
  const power = Number(event.target.value);
  updateControl({ led_power: power, led_target: power > 0 });
});

$('pumpBtn').addEventListener('click', async () => {
  const soil = latestRecord?.soil_humidity;
  const water = String(latestRecord?.water_level ?? '').toLowerCase();
  if (soil == null) {
    toast('Riego manual denegado. No hay lectura válida de humedad del suelo.');
    return;
  }
  if (soil >= SOIL_DENY) {
    toast(`Suelo húmedo. Riego manual denegado. Humedad actual: ${Math.round(soil)} %.`);
    return;
  }
  if (water === 'low') {
    toast('Riego manual denegado. Nivel de agua bajo.');
    return;
  }
  await updateControl({
    pump_request: Number(deviceControl?.pump_request ?? 0) + 1,
    pump_duration_ms: 3000,
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
