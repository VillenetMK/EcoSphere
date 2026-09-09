/* EcoSphere · real observations only, preserving each sensor's timestamp. */
export const METRICS = {
  temperatura: { label: 'Temperatura', unit: '°C', icon: '🌡️' },
  humedad_ambiente: { label: 'Humedad ambiental', unit: '%', icon: '💧' },
  humedad_suelo: { label: 'Humedad del suelo', unit: '%', icon: '🌱' },
  luz: { label: 'Luz', unit: 'lx', icon: '☀️' },
};
export function realReading(value) {
  return value && typeof value.valor === 'number' && Number.isFinite(value.valor) && value.simulado !== true;
}
export function timestampIsCurrent(timestamp, now = Date.now()) {
  const age = now - Date.parse(timestamp);
  return Number.isFinite(age) && age >= 0 && age <= 30000;
}
export function deviceIsCurrent(device, verified, now = Date.now()) {
  return verified === true && device?.conectado === true && timestampIsCurrent(device.ultima_conexion, now);
}
export function ageLabel(timestamp) {
  const date = Date.parse(timestamp);
  if (!Number.isFinite(date)) return 'Fecha desconocida';
  const seconds = Math.max(0, Math.floor((Date.now() - date) / 1000));
  if (seconds < 60) return `Hace ${seconds} s`;
  if (seconds < 3600) return `Hace ${Math.floor(seconds / 60)} min`;
  return `Hace ${Math.floor(seconds / 3600)} h`;
}
export class Panel {
  constructor() { this.history = {}; this.selected = 'humedad_suelo'; this.data = null; this.verified = false; }
  render(data) {
    this.data = data;
    this.verified = true;
    const readings = data.fuente?.startsWith('simulador') ? {} : data.lecturas || {};
    const cards = document.getElementById('tarjetas');
    cards.replaceChildren();
    for (const [key, meta] of Object.entries(METRICS)) {
      const reading = readings[key];
      const valid = realReading(reading);
      const button = document.createElement('button');
      button.type = 'button';
      button.dataset.metric = key;
      button.className = `tarjeta${key === this.selected ? ' activa' : ''}${valid && !reading.vigente ? ' historico' : ''}`;
      button.setAttribute('aria-pressed', String(key === this.selected));
      const heading = document.createElement('span'); heading.className = 'cab'; heading.textContent = `${meta.icon} ${meta.label}`;
      const value = document.createElement('span'); value.className = 'valor'; value.textContent = valid ? `${reading.valor.toLocaleString('es-PE', { maximumFractionDigits: 1 })} ${meta.unit}` : 'Sin lectura';
      const age = document.createElement('span'); age.className = 'lectura-meta';
      age.textContent = valid ? `${ageLabel(reading.actualizado)}${reading.vigente === true ? '' : ' · último dato'}` : 'Sensor sin datos disponibles';
      button.append(heading, value, age);
      button.addEventListener('click', () => {
        this.selected = key;
        for (const card of cards.querySelectorAll('.tarjeta')) {
          const selected = card.dataset.metric === key;
          card.classList.toggle('activa', selected); card.setAttribute('aria-pressed', String(selected));
        }
        this.draw();
      });
      cards.appendChild(button);
      if (valid && Number.isFinite(Date.parse(reading.actualizado))) {
        const history = this.history[key] ||= [];
        if (!history.some(point => point.time === reading.actualizado)) {
          history.push({ time: reading.actualizado, value: reading.valor });
          if (history.length > 40) history.shift();
        }
      }
    }
    const chips = document.getElementById('sistema-chips');
    chips.replaceChildren();
    for (const [key, item] of Object.entries(data.estado_sistema || {})) {
      if (!item || item.valor === null || item.valor === undefined) continue;
      const chip = document.createElement('span'); chip.className = 'chip-sistema';
      const value = item.tipo === 'bool' ? (item.valor === true ? 'encendido' : 'apagado') : String(item.texto || item.valor);
      chip.textContent = `${item.label || key}: ${value}`;
      chips.appendChild(chip);
    }
    document.getElementById('sistema-caja').hidden = !chips.childElementCount;
    this.updateFreshness();
    this.draw();
  }
  invalidate() { this.verified = false; this.updateFreshness(); }
  updateFreshness() {
    if (!this.data) {
      document.getElementById('pie-actualizado').textContent = 'Sin datos verificables. Intentando actualizar…';
      return;
    }
    const data = this.data;
    const device = data.dispositivo || {};
    const connected = deviceIsCurrent(device, this.verified);
    const deviceNode = document.getElementById('dispositivo-estado');
    deviceNode.hidden = false;
    deviceNode.dataset.estado = connected ? 'conectado' : 'desconectado';
    deviceNode.querySelector('span').textContent = !this.verified ? 'Sin verificar' : connected ? 'Conectado' : 'Sin conexión';
    for (const card of document.getElementById('tarjetas').querySelectorAll('.tarjeta')) {
      const reading = data.lecturas?.[card.dataset.metric];
      if (!realReading(reading) || data.fuente?.startsWith('simulador')) continue;
      const current = connected && reading.vigente === true && timestampIsCurrent(reading.actualizado);
      card.classList.toggle('historico', !current);
      card.querySelector('.lectura-meta').textContent = `${ageLabel(reading.actualizado)}${current ? '' : ' · último dato'}`;
    }
    document.getElementById('pie-actualizado').textContent = !this.verified
      ? 'No se pudo actualizar. Se muestran únicamente los últimos datos conocidos; la conexión no está confirmada.'
      : connected
        ? `Última señal: ${ageLabel(device.ultima_conexion)}.`
        : `Última señal: ${ageLabel(device.ultima_conexion)}. Las lecturas pueden ser anteriores.`;
  }
  draw() {
    const canvas = document.getElementById('grafico-historial');
    const label = METRICS[this.selected].label;
    document.getElementById('grafico-titulo').textContent = label;
    const points = this.history[this.selected] || [];
    canvas.setAttribute('aria-label', `${label}: ${points.length} lecturas diferentes recibidas en esta sesión.`);
    const width = Math.max(240, canvas.parentElement.clientWidth), height = 135;
    canvas.width = width * 2; canvas.height = height * 2;
    canvas.style.width = '100%'; canvas.style.height = `${height}px`;
    const ctx = canvas.getContext('2d'); ctx.scale(2, 2);
    ctx.fillStyle = '#a8c4b9'; ctx.font = '12px system-ui';
    if (!points.length) { ctx.fillText('Esperando lecturas reales', 12, 70); return; }
    const min = Math.min(...points.map(point => point.value)), max = Math.max(...points.map(point => point.value));
    ctx.fillText(`${max.toFixed(1)} ${METRICS[this.selected].unit}`, 8, 15);
    ctx.fillText(`${min.toFixed(1)} ${METRICS[this.selected].unit}`, 8, height - 7);
    ctx.strokeStyle = '#35e0a1'; ctx.lineWidth = 2; ctx.beginPath();
    points.forEach((point, index) => {
      const x = 55 + index * (width - 65) / Math.max(1, points.length - 1);
      const y = 30 + (max === min ? .5 : (max - point.value) / (max - min)) * (height - 60);
      if (!index) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      if (points.length === 1) { ctx.arc(x, y, 3, 0, Math.PI * 2); }
    });
    ctx.stroke();
  }
}
