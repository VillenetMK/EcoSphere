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

export function irrigationDecision(soilHumidity, waterLevel) {
  const soil = Number(soilHumidity);
  if (soilHumidity == null || !Number.isFinite(soil)) {
    return {
      allowed: false,
      reason: 'missing-soil-reading',
      message: 'Riego manual denegado. No hay lectura válida de humedad del suelo.',
    };
  }

  if (soil >= CONTROL_POLICY.soilManualDenyThreshold) {
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

  return { allowed: true, reason: 'none', message: 'Riego manual disponible.' };
}

export function irrigationStatus(soilHumidity, waterLevel) {
  const decision = irrigationDecision(soilHumidity, waterLevel);
  if (!decision.allowed) return decision.message;
  return Number(soilHumidity) <= CONTROL_POLICY.soilDryThreshold
    ? 'Suelo seco: riego permitido.'
    : 'Rango aceptable: riego manual disponible.';
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
