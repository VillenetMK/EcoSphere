import test from 'node:test';
import assert from 'node:assert/strict';

import { clientErrorMessage, readJsonResponse } from '../api-response.js';

test('no expone detalles internos de un error estructurado de Supabase', async () => {
  const response = new Response(JSON.stringify({
    code: 'ECOSPHERE_CONTROL_RATE_LIMIT',
    details: null,
    hint: 'Espera un instante.',
    message: 'Estás enviando órdenes demasiado rápido.',
  }), { status: 429, headers: { 'Content-Type': 'application/json' } });

  await assert.rejects(
    readJsonResponse(response),
    { message: 'Se enviaron demasiadas órdenes. Espera un momento e inténtalo nuevamente.' },
  );
});

test('conserva una respuesta correcta y admite un cuerpo vacío', async () => {
  const data = await readJsonResponse(new Response('{"ok":true}', { status: 200 }));
  const empty = await readJsonResponse(new Response(null, { status: 204 }));

  assert.deepEqual(data, { ok: true });
  assert.equal(empty, null);
});

test('no expone cuerpos de error no estructurados', async () => {
  const response = new Response('Servicio temporalmente no disponible', { status: 503 });

  await assert.rejects(
    readJsonResponse(response),
    { message: 'No se pudo completar la solicitud (HTTP 503).' },
  );
});

test('traduce una denegación de permisos sin mostrar nombres de tablas', async () => {
  const response = new Response(JSON.stringify({
    code: '42501',
    hint: 'GRANT UPDATE ON public.device_control TO authenticated;',
    message: 'permission denied for table device_control',
  }), { status: 403 });

  await assert.rejects(
    readJsonResponse(response),
    { message: 'Tu cuenta no tiene permiso para realizar esta acción.' },
  );
});

test('los errores locales desconocidos tampoco exponen detalles internos', () => {
  assert.equal(
    clientErrorMessage(new Error('permission denied for table private.secret_table'), 'Error seguro.'),
    'Error seguro.',
  );
  assert.equal(
    clientErrorMessage(new TypeError('Failed to fetch'), 'Error seguro.'),
    'No se pudo conectar con EcoSphere. Revisa tu conexión e inténtalo nuevamente.',
  );
});
