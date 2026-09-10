/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

// Presentation only: never pass these values to telemetry, irrigation, history
// or AI. A username is editable; the exhibition is scoped to one session UUID.
const EXHIBITION_USER_ID = '367e842b-fd47-4c38-a3fc-c54c47732a9e';
const EXAMPLE_ENVIRONMENT = Object.freeze({
  temperature: 25.4,
  air_humidity: 62,
});

// Uncalibrated exhibition reference for the LED contribution, not a BH1750
// measurement. The caller passes only current telemetry from an online ESP32.
export function estimateLedLux(currentRecord) {
  const power = currentRecord?.led_power;
  const on = currentRecord?.led_on;
  if (typeof power !== 'number' || !Number.isFinite(power) || power < 0 || power > 100 ||
      typeof on !== 'boolean' || on !== (power > 0)) return null;
  return Math.round(850 * power / 100 * 10) / 10;
}

export function environmentPresentation(record, { userId, profile } = {}) {
  const simulated = userId === EXHIBITION_USER_ID &&
    profile?.status === 'approved' && profile?.role === 'operator';
  const values = simulated ? EXAMPLE_ENVIRONMENT : record;
  return Object.freeze({
    simulated,
    temperature: values?.temperature ?? null,
    air_humidity: values?.air_humidity ?? null,
    light_lux: simulated ? estimateLedLux(record) : record?.light_lux ?? null,
  });
}
