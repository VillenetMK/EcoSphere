/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

export const CONTROL_POLICY = Object.freeze({
  soilManualDenyThreshold: 60,
  soilDryThreshold: 35,
  pumpDurationMs: 3000,
  onlineTimeoutMs: 30000,
  clockSkewToleranceMs: 60000,
});

export function clampPower(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return 0;
  return Math.min(100, Math.max(0, Math.round(numeric)));
}

export function normalizeControlPermissions(result) {
  const row = Array.isArray(result) ? (result.length === 1 ? result[0] : null) : result;
  return {
    allowWetSoilManualWatering: row?.allow_wet_soil_manual_watering === true,
  };
}

export function irrigationDecision(soilHumidity, waterLevel, permissions = {}) {
  const soil = Number(soilHumidity);
  if (soilHumidity == null || soilHumidity === '' || !Number.isFinite(soil) || soil < 0 || soil > 100) {
    return {
      allowed: false,
      reason: 'missing-soil-reading',
      message: 'Riego manual denegado. No hay lectura válida de humedad del suelo.',
    };
  }

  const wetSoilAuthorized = permissions?.allowWetSoilManualWatering === true;
  if (soil >= CONTROL_POLICY.soilManualDenyThreshold && !wetSoilAuthorized) {
    return {
      allowed: false,
      reason: 'soil-too-wet',
      message: `Suelo húmedo. Riego manual denegado. Humedad actual: ${Math.round(soil)} %.`,
    };
  }

  const water = String(waterLevel ?? '').toLowerCase();
  if (!['high', 'low'].includes(water)) {
    return {
      allowed: false,
      reason: 'missing-water-reading',
      message: 'Riego manual denegado. No hay lectura válida del nivel de agua.',
    };
  }

  if (water === 'low') {
    return {
      allowed: false,
      reason: 'low-water',
      message: 'Riego manual denegado. Nivel de agua bajo.',
    };
  }

  return {
    allowed: true,
    reason: 'none',
    message: soil >= CONTROL_POLICY.soilManualDenyThreshold
      ? 'Riego manual con suelo húmedo autorizado para tu cuenta. Pulso de 3 segundos.'
      : 'Riego manual disponible.',
  };
}

export function irrigationStatus(soilHumidity, waterLevel, permissions = {}) {
  const decision = irrigationDecision(soilHumidity, waterLevel, permissions);
  if (!decision.allowed) return decision.message;
  if (Number(soilHumidity) >= CONTROL_POLICY.soilManualDenyThreshold) return decision.message;
  return Number(soilHumidity) <= CONTROL_POLICY.soilDryThreshold
    ? 'Suelo seco: riego permitido.'
    : 'Rango aceptable: riego manual disponible.';
}

export function manualIrrigationDecision(record, control, profile, permissions = {}, nowMillis = Date.now()) {
  if (profile?.status !== 'approved' || !['operator', 'admin'].includes(profile?.role)) {
    return { allowed: false, reason: 'operator-required', message: 'Tu cuenta no tiene permiso para realizar esta acción.' };
  }
  if (!isTelemetryCurrent(record, control, nowMillis)) {
    return { allowed: false, reason: 'telemetry-unavailable', message: 'Riego bloqueado: se necesita telemetría actual y el ESP32 conectado.' };
  }
  if (control.auto_mode !== false) {
    return { allowed: false, reason: 'automatic-mode', message: 'Desactiva el modo automático antes de solicitar riego manual.' };
  }
  const decision = irrigationDecision(record.soil_humidity, record.water_level, permissions);
  return decision.allowed
    ? { ...decision, message: irrigationStatus(record.soil_humidity, record.water_level, permissions) }
    : decision;
}

export function waterLevelLabel(value) {
  const normalized = String(value ?? '').toLowerCase();
  if (normalized === 'high') return 'Disponible';
  if (normalized === 'low') return 'Bajo';
  return 'Sin lectura válida';
}

export function actuatorPwmLabel(reportedOn, reportedPower) {
  const hasReportedPower = reportedPower !== null && reportedPower !== undefined && reportedPower !== '';
  const numericPower = hasReportedPower ? Number(reportedPower) : Number.NaN;
  if (reportedOn == null && !hasReportedPower) return 'SIN REGISTRO';

  const power = Number.isFinite(numericPower)
    ? clampPower(numericPower)
    : reportedOn === true ? 100 : 0;
  return `SALIDA PWM ${power} %`;
}

export function actuatorSwitchLabel(reportedOn) {
  if (reportedOn == null) return 'SIN REGISTRO';
  return reportedOn ? 'SALIDA ACTIVA' : 'SALIDA INACTIVA';
}

export function isTelemetryFresh(record, nowMillis = Date.now()) {
  if (!record?.created_at) return false;
  const createdAtMillis = Date.parse(record.created_at);
  if (Number.isNaN(createdAtMillis)) return false;
  const age = nowMillis - createdAtMillis;
  return age >= -CONTROL_POLICY.clockSkewToleranceMs &&
    age <= CONTROL_POLICY.onlineTimeoutMs;
}

export function isDeviceOnline(control, nowMillis = Date.now()) {
  if (!control?.esp32_online || !control?.last_seen_at) return false;
  const lastSeenMillis = Date.parse(control.last_seen_at);
  if (Number.isNaN(lastSeenMillis)) return false;
  const age = nowMillis - lastSeenMillis;
  return age >= -CONTROL_POLICY.clockSkewToleranceMs &&
    age <= CONTROL_POLICY.onlineTimeoutMs;
}

export function isTelemetryCurrent(record, control, nowMillis = Date.now()) {
  return isDeviceOnline(control, nowMillis) && isTelemetryFresh(record, nowMillis);
}
