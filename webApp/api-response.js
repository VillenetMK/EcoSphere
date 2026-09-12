/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

function safeErrorMessage(status, serverMessage = '') {
  const message = serverMessage.toLowerCase();

  if (message.includes('manual fan control is disabled in automatic mode')) {
    return 'Desactiva el modo automático antes de ajustar el ventilador.';
  }
  if (message.includes('manual led control is disabled in automatic mode')) {
    return 'Desactiva el modo automático antes de ajustar la iluminación.';
  }
  if (message.includes('manual watering is disabled in automatic mode')) {
    return 'Desactiva el modo automático antes de solicitar riego manual.';
  }
  if (message.includes('pairing code is invalid or expired')) {
    return 'El código del controlador es inválido o expiró.';
  }
  if (message.includes('a controller pairing request is already pending')) {
    return 'Ya existe un código temporal pendiente. Búscalo en el Monitor Serie y úsalo antes de que expire.';
  }
  if (message.includes('active two-factor session required')) {
    return 'Confirma de nuevo tu autenticador para realizar esta acción administrativa.';
  }
  if (message.includes('replacement controller firmware does not support')) {
    return 'Actualiza el firmware del ESP32 antes de usarlo como reemplazo.';
  }
  if (message.includes('watering denied:')) {
    return 'El riego fue bloqueado porque las condiciones actuales no son seguras.';
  }
  if (status === 401 || message.includes('authentication required') || message.includes('active authenticated session')) {
    return 'Tu sesión expiró. Inicia sesión nuevamente.';
  }
  if (status === 429 || message.includes('rate limit') || message.includes('cooldown')) {
    return 'Se enviaron demasiadas órdenes. Espera un momento e inténtalo nuevamente.';
  }
  if (status === 403 || message.includes('operator access required') || message.includes('administrator access required')) {
    return 'Tu cuenta no tiene permiso para realizar esta acción.';
  }

  return `No se pudo completar la solicitud (HTTP ${status}).`;
}

const SAFE_CLIENT_MESSAGES = new Set([
  'Tu sesión expiró. Inicia sesión nuevamente.',
  'Tu cuenta no tiene permiso para realizar esta acción.',
  'Desactiva el modo automático antes de ajustar el ventilador.',
  'Desactiva el modo automático antes de ajustar la iluminación.',
  'Desactiva el modo automático antes de solicitar riego manual.',
  'El código del controlador es inválido o expiró.',
  'Ya existe un código temporal pendiente. Búscalo en el Monitor Serie y úsalo antes de que expire.',
  'Confirma de nuevo tu autenticador para realizar esta acción administrativa.',
  'Actualiza el firmware del ESP32 antes de usarlo como reemplazo.',
  'El riego fue bloqueado porque las condiciones actuales no son seguras.',
  'Se enviaron demasiadas órdenes. Espera un momento e inténtalo nuevamente.',
]);

export function clientErrorMessage(error, fallback) {
  const message = String(error?.message ?? error ?? '').trim();
  const normalized = message.toLowerCase();
  if (SAFE_CLIENT_MESSAGES.has(message)) return message;
  if (/^No se pudo completar la solicitud \(HTTP [0-9]{3}\)\.$/.test(message)) return message;
  if (normalized.includes('network') || normalized.includes('failed to fetch') || normalized.includes('timeout')) {
    return 'No se pudo conectar con EcoSphere. Revisa tu conexión e inténtalo nuevamente.';
  }
  return fallback;
}

export async function readJsonResponse(response) {
  const text = await response.text();

  if (!response.ok) {
    let serverMessage = '';
    try {
      const payload = JSON.parse(text);
      if (typeof payload?.message === 'string') serverMessage = payload.message.trim();
    } catch {
      // Error bodies can contain proxy or database details. Status-based
      // messages keep the interface useful without displaying those details.
    }

    throw new Error(safeErrorMessage(response.status, serverMessage));
  }

  return text ? JSON.parse(text) : null;
}
