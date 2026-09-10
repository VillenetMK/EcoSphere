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
  light_lux: 850,
});

export function environmentPresentation(record, { userId, profile } = {}) {
  const simulated = userId === EXHIBITION_USER_ID &&
    profile?.status === 'approved' && profile?.role === 'operator';
  const values = simulated ? EXAMPLE_ENVIRONMENT : record;
  return Object.freeze({
    simulated,
    temperature: values?.temperature ?? null,
    air_humidity: values?.air_humidity ?? null,
    light_lux: values?.light_lux ?? null,
  });
}
