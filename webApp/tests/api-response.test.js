import test from 'node:test';
import assert from 'node:assert/strict';

import { readJsonResponse } from '../api-response.js';

test('muestra sólo el mensaje útil de un error estructurado de Supabase', async () => {
  const response = new Response(JSON.stringify({
    code: 'ECOSPHERE_CONTROL_RATE_LIMIT',
    details: null,
    hint: 'Espera un instante.',
    message: 'Estás enviando órdenes demasiado rápido.',
  }), { status: 429, headers: { 'Content-Type': 'application/json' } });

  await assert.rejects(
    readJsonResponse(response),
    { message: 'Estás enviando órdenes demasiado rápido.' },
  );
});

test('conserva una respuesta correcta y admite un cuerpo vacío', async () => {
  const data = await readJsonResponse(new Response('{"ok":true}', { status: 200 }));
  const empty = await readJsonResponse(new Response(null, { status: 204 }));

  assert.deepEqual(data, { ok: true });
  assert.equal(empty, null);
});

test('usa un mensaje HTTP legible cuando el servidor no responde con JSON', async () => {
  const response = new Response('Servicio temporalmente no disponible', { status: 503 });

  await assert.rejects(
    readJsonResponse(response),
    { message: 'HTTP 503: Servicio temporalmente no disponible' },
  );
});
