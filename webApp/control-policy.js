const REQUIRED_POSITIVE_INTEGER_FIELDS = [
  'pollIntervalMs',
  'onlineTimeoutMs',
  'futureClockSkewMs',
];

export function validatePolicy(candidate) {
  if (!candidate || candidate.schemaVersion !== 1) {
    throw new Error('Contrato de control incompatible: schemaVersion debe ser 1.');
  }

  for (const field of REQUIRED_POSITIVE_INTEGER_FIELDS) {
    if (!Number.isInteger(candidate[field]) || candidate[field] <= 0) {
      throw new Error(`Contrato de control inválido: ${field} debe ser un entero positivo.`);
    }
  }

  const manual = candidate.manualWatering;
  if (!manual || !Number.isFinite(manual.soilDenyAtOrAbovePercent)) {
    throw new Error('Contrato de control inválido: falta el umbral manual de humedad.');
  }
  if (!Number.isInteger(manual.pumpDurationMs) || manual.pumpDurationMs <= 0) {
    throw new Error('Contrato de control inválido: pumpDurationMs debe ser un entero positivo.');
  }
  if (!Array.isArray(manual.blockedWaterLevels) || manual.blockedWaterLevels.some(value => typeof value !== 'string')) {
    throw new Error('Contrato de control inválido: blockedWaterLevels debe ser una lista de texto.');
  }

  return Object.freeze({
    ...candidate,
    manualWatering: Object.freeze({
      ...manual,
      blockedWaterLevels: Object.freeze(
        manual.blockedWaterLevels.map(value => value.trim().toLowerCase())
      ),
    }),
    automaticWatering: Object.freeze({ ...candidate.automaticWatering }),
  });
}

export async function loadControlPolicy(url = './config/control-policy.json') {
  const response = await fetch(url, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`No se pudo cargar el contrato de control (HTTP ${response.status}).`);
  }
  return validatePolicy(await response.json());
}

export function isDeviceOnline(control, policy, nowMs = Date.now()) {
  if (!control?.esp32_online || !control?.last_seen_at) return false;
  const lastSeenMs = Date.parse(control.last_seen_at);
  if (Number.isNaN(lastSeenMs)) return false;

  const ageMs = nowMs - lastSeenMs;
  return ageMs >= -policy.futureClockSkewMs && ageMs <= policy.onlineTimeoutMs;
}

export function evaluateManualWatering(soilHumidity, waterLevel, policy) {
  if (soilHumidity === null || soilHumidity === undefined || !Number.isFinite(Number(soilHumidity))) {
    return {
      allowed: false,
      message: 'Riego manual denegado. No hay lectura válida de humedad del suelo.',
    };
  }

  const soil = Number(soilHumidity);
  const threshold = policy.manualWatering.soilDenyAtOrAbovePercent;
  if (soil >= threshold) {
    return {
      allowed: false,
      message: `Suelo húmedo. Riego manual denegado. Humedad actual: ${Math.round(soil)} %.`,
    };
  }

  const normalizedWater = String(waterLevel ?? '').trim().toLowerCase();
  if (policy.manualWatering.blockedWaterLevels.includes(normalizedWater)) {
    return {
      allowed: false,
      message: 'Riego manual denegado. Nivel de agua bajo.',
    };
  }

  return { allowed: true, message: null };
}
