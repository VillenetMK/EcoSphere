/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

export async function readJsonResponse(response) {
  const text = await response.text();

  if (!response.ok) {
    let message = '';
    try {
      const payload = JSON.parse(text);
      if (typeof payload?.message === 'string') message = payload.message.trim();
    } catch {
      // Preserve non-JSON responses as a useful fallback without exposing an
      // entire structured database error to the interface.
    }

    throw new Error(message || `HTTP ${response.status}: ${text || response.statusText}`);
  }

  return text ? JSON.parse(text) : null;
}
