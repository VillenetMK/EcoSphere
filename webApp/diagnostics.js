import {
  CONTROL_POLICY,
  actuatorPwmLabel,
  actuatorSwitchLabel,
  isDeviceOnline,
  isTelemetryFresh,
  waterLevelLabel,
} from './control-policy.js';

const VALID_WATER_LEVELS = new Set(['high', 'low']);

function validNumber(value) {
  if (value === null || value === undefined || value === '') return null;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function timestampAge(value, nowMillis) {
  if (!value) return null;
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return null;
  return nowMillis - timestamp;
}

export function relativeAge(value, nowMillis = Date.now()) {
  const age = timestampAge(value, nowMillis);
  if (age === null) return 'sin fecha válida';
  if (age < -CONTROL_POLICY.clockSkewToleranceMs) return 'con fecha futura';
  if (age < 5000) return 'hace unos segundos';

  const seconds = Math.floor(Math.max(0, age) / 1000);
  if (seconds < 60) return `hace ${seconds} s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `hace ${hours} h`;
  const days = Math.floor(hours / 24);
  return `hace ${days} ${days === 1 ? 'día' : 'días'}`;
}

export { isTelemetryFresh } from './control-policy.js';

function item(name, status, severity, reading, detail) {
  return { name, status, severity, reading, detail };
}

function sensorStatus({ name, hasReading, reading, fresh, missingDetail, staleDetail }) {
  if (!hasReading) {
    return item(name, 'SIN DATOS', 'unknown', reading, missingDetail);
  }
  if (!fresh) {
    return item(name, 'DATO ANTIGUO', 'warning', reading, staleDetail);
  }
  return item(name, 'LECTURA DISPONIBLE', 'normal', reading, 'Lectura recibida dentro del intervalo esperado.');
}

function actuatorStatus(name, reading, hasReading, fresh, outputActive) {
  if (!hasReading) {
    return item(name, 'SIN DATOS', 'unknown', 'Sin estado registrado', 'No existe una lectura válida del actuador.');
  }
  if (!fresh) {
    return item(
      name,
      'ÚLTIMO ESTADO',
      'warning',
      reading,
      'Estado histórico: no se puede confirmar mientras la telemetría esté desactualizada.',
    );
  }
  return item(
    name,
    outputActive ? 'SALIDA ACTIVA' : 'SALIDA INACTIVA',
    'normal',
    reading,
    'El ESP32 confirma su señal de salida. Sin sensor de corriente, tensión o RPM no puede confirmar que el equipo esté conectado ni funcionando.',
  );
}

export function buildDiagnosticModel(record, control, nowMillis = Date.now()) {
  const online = isDeviceOnline(control, nowMillis);
  const fresh = isTelemetryFresh(record, nowMillis);
  const current = online && fresh;
  const telemetryAge = relativeAge(record?.created_at, nowMillis);
  const heartbeatAge = relativeAge(control?.last_seen_at, nowMillis);

  const connectivity = [
    online
      ? item('ESP32', 'CONECTADO', 'normal', `Heartbeat ${heartbeatAge}`, 'El dispositivo responde dentro del intervalo esperado.')
      : item('ESP32', 'CRÍTICO · SIN CONEXIÓN', 'critical', `Último heartbeat ${heartbeatAge}`, 'El dispositivo no responde; los estados actuales no pueden confirmarse.'),
    fresh
      ? item('Telemetría', 'ACTUALIZADA', 'normal', `Último registro ${telemetryAge}`, 'Las lecturas corresponden al intervalo esperado.')
      : item(
        'Telemetría',
        record?.created_at ? 'DATO ANTIGUO' : 'SIN REGISTROS',
        record?.created_at ? 'warning' : 'unknown',
        record?.created_at ? `Último registro ${telemetryAge}` : 'Sin telemetría registrada',
        record?.created_at
          ? 'Los valores mostrados son históricos y no representan el estado actual.'
          : 'Todavía no se ha recibido telemetría del dispositivo.',
      ),
  ];

  const temperature = validNumber(record?.temperature);
  const airHumidity = validNumber(record?.air_humidity);
  const bmeReading = `Temperatura: ${temperature === null ? '--' : `${temperature.toFixed(1)} °C`} · Humedad: ${airHumidity === null ? '--' : `${airHumidity.toFixed(1)} %`}`;
  const light = validNumber(record?.light_lux);
  const soil = validNumber(record?.soil_humidity);
  const water = String(record?.water_level ?? '').toLowerCase();

  const sensors = [
    sensorStatus({
      name: 'BME280',
      hasReading: temperature !== null && airHumidity !== null,
      reading: bmeReading,
      fresh: current,
      missingDetail: 'El último registro no incluye temperatura y humedad del aire válidas.',
      staleDetail: `Última lectura recibida ${telemetryAge}; no debe interpretarse como actual.`,
    }),
    sensorStatus({
      name: 'BH1750',
      hasReading: light !== null,
      reading: light === null ? 'Iluminación: --' : `Iluminación: ${light.toFixed(1)} lux`,
      fresh: current,
      missingDetail: 'El último registro no incluye una lectura válida de iluminación.',
      staleDetail: `Última lectura recibida ${telemetryAge}; no debe interpretarse como actual.`,
    }),
  ];

  if (soil === null) {
    sensors.push(item('Humedad de suelo', 'SIN DATOS', 'unknown', 'Humedad: --', 'No existe una lectura válida; el riego permanece bloqueado por seguridad.'));
  } else if (!current) {
    sensors.push(item(
      'Humedad de suelo',
      'DATO ANTIGUO',
      'warning',
      `Último valor: ${Math.round(soil)} %`,
      soil >= CONTROL_POLICY.soilManualDenyThreshold
        ? 'El último valor indica suelo húmedo. El riego permanece bloqueado por seguridad.'
        : 'No se puede evaluar el suelo actual; el riego permanece bloqueado por seguridad.',
    ));
  } else if (soil >= CONTROL_POLICY.soilManualDenyThreshold) {
    sensors.push(item('Humedad de suelo', 'ADVERTENCIA · RIEGO BLOQUEADO', 'warning', `${Math.round(soil)} %`, 'El suelo supera el límite seguro configurado para riego manual.'));
  } else {
    sensors.push(item('Humedad de suelo', 'RANGO OPERATIVO', 'normal', `${Math.round(soil)} %`, 'La lectura es válida y permite evaluar el riego junto con el nivel de agua.'));
  }

  if (!VALID_WATER_LEVELS.has(water)) {
    sensors.push(item('Nivel de agua', 'SIN DATOS', 'unknown', waterLevelLabel(water), 'No existe una lectura válida; el riego permanece bloqueado por seguridad.'));
  } else if (!current) {
    sensors.push(item(
      'Nivel de agua',
      'DATO ANTIGUO',
      'warning',
      `Último nivel: ${waterLevelLabel(water)}`,
      water === 'low'
        ? 'El último nivel recibido era bajo. El riego permanece bloqueado por seguridad.'
        : 'No se puede confirmar el nivel actual; el riego permanece bloqueado por seguridad.',
    ));
  } else if (water === 'low') {
    sensors.push(item('Nivel de agua', 'ALERTA · RIEGO BLOQUEADO', 'warning', 'Nivel bajo', 'No hay agua suficiente para habilitar el riego.'));
  } else {
    sensors.push(item('Nivel de agua', 'NIVEL DISPONIBLE', 'normal', 'Agua disponible', 'El sensor confirma nivel suficiente.'));
  }

  const fan = validNumber(record?.fan_power);
  const led = validNumber(record?.led_power);
  const pump = typeof record?.pump_on === 'boolean' ? record.pump_on : null;
  const actuators = [
    actuatorStatus('Ventilador', actuatorPwmLabel(record?.fan_on, fan), fan !== null || typeof record?.fan_on === 'boolean', current, record?.fan_on === true || (fan ?? 0) > 0),
    actuatorStatus('LED Grow', actuatorPwmLabel(record?.led_on, led), led !== null || typeof record?.led_on === 'boolean', current, record?.led_on === true || (led ?? 0) > 0),
    actuatorStatus('Bomba', actuatorSwitchLabel(pump), pump !== null, current, pump === true),
  ];

  const groups = [
    { id: 'connectivity', title: 'Conectividad', description: 'Disponibilidad del dispositivo y vigencia de los datos.', items: connectivity },
    { id: 'sensors', title: 'Sensores', description: 'Lecturas ambientales y condiciones que bloquean el riego.', items: sensors },
    { id: 'actuators', title: 'Actuadores', description: 'Señales de salida reportadas; la conexión y el funcionamiento físicos no tienen retroalimentación.', items: actuators },
  ];
  const allItems = groups.flatMap(group => group.items);
  const counts = allItems.reduce((result, current) => {
    result[current.severity] += 1;
    return result;
  }, { critical: 0, warning: 0, unknown: 0, normal: 0 });

  let headline;
  let summary;
  let severity;
  if (!record?.created_at) {
    headline = 'Sin telemetría disponible';
    summary = 'No hay registros para confirmar el estado del sistema.';
    severity = 'critical';
  } else if (!fresh) {
    headline = 'Telemetría desactualizada';
    summary = `${online ? 'El dispositivo responde, pero' : 'El ESP32 no responde y'} los valores mostrados son los últimos recibidos ${telemetryAge}; no representan el estado actual.`;
    severity = 'critical';
  } else if (!online) {
    headline = 'ESP32 sin conexión';
    summary = 'La última telemetría es reciente, pero el heartbeat del dispositivo no está confirmado.';
    severity = 'critical';
  } else if (counts.warning > 0 || counts.unknown > 0) {
    headline = 'Sistema conectado con alertas';
    summary = 'Revisa los sensores marcados antes de operar el sistema.';
    severity = 'warning';
  } else {
    headline = 'Sistema operativo';
    summary = 'Conectividad y telemetría confirmadas. Las salidas físicas continúan sin sensor de retroalimentación.';
    severity = 'normal';
  }

  return { headline, summary, severity, counts, groups, generatedAt: nowMillis };
}

export function technicalReport(model) {
  const lines = [
    'EcoSphere · Reporte técnico de diagnóstico',
    `Generado: ${new Date(model.generatedAt).toLocaleString('es-PE')}`,
    `Resumen: ${model.headline} — ${model.summary}`,
    `Conteo: ${model.counts.critical} críticos, ${model.counts.warning} advertencias, ${model.counts.unknown} sin datos`,
  ];

  model.groups.forEach(group => {
    lines.push('', `[${group.title}]`);
    group.items.forEach(entry => {
      lines.push(`${entry.name} — ${entry.status} — ${entry.reading} — ${entry.detail}`);
    });
  });
  return lines.join('\n');
}
