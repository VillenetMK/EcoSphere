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

test('explica que ya existe un código temporal en vez de mostrar HTTP 500', async () => {
  const response = new Response(JSON.stringify({
    code: '55000',
    details: null,
    hint: null,
    message: 'a controller pairing request is already pending',
  }), { status: 500, headers: { 'Content-Type': 'application/json' } });

  await assert.rejects(
    readJsonResponse(response),
    { message: 'Ya existe un código temporal pendiente. Búscalo en el Monitor Serie y úsalo antes de que expire.' },
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

test('explica cada bloqueo de riego y conserva el mensaje seguro hasta la interfaz', async (t) => {
  const cases = [
    ['system pump cooldown is active', 429,
      'Espera 10 segundos desde el último riego antes de volver a regar.'],
    ['operator pump cooldown is active', 429,
      'Espera 60 segundos entre riegos de tu cuenta.'],
    ['current telemetry is unavailable', 400,
      'No hay datos recientes del ESP32. Actualiza los datos antes de regar.'],
    ['soil sensor is unavailable', 400,
      'El sensor de humedad del suelo no tiene una lectura válida.'],
    ['soil humidity is already 60 percent or higher', 400,
      'La humedad del suelo es de 60 % o más. Tu cuenta no tiene habilitado el riego con suelo húmedo.'],
    ['water level is not sufficient', 400,
      'No hay suficiente agua para activar el riego.'],
  ];

  for (const [reason, status, expectedMessage] of cases) {
    await t.test(reason, async () => {
      const response = new Response(JSON.stringify({
        message: `watering denied: ${reason}`,
        details: 'private.internal_pump_state',
        hint: 'Internal database diagnostic',
      }), { status });

      await assert.rejects(readJsonResponse(response), (error) => {
        assert.equal(error.message, expectedMessage);
        assert.equal(clientErrorMessage(error, 'Error seguro.'), expectedMessage);
        return true;
      });
    });
  }
});

test('oculta una causa desconocida de riego sin perder el fallback seguro', async () => {
  const response = new Response(JSON.stringify({
    message: 'watering denied: private.secret_table rejected internal cooldown configuration',
    details: 'internal details',
  }), { status: 400 });
  const expectedMessage = 'El riego fue bloqueado porque las condiciones actuales no son seguras.';

  await assert.rejects(readJsonResponse(response), (error) => {
    assert.equal(error.message, expectedMessage);
    assert.equal(clientErrorMessage(error, 'Error seguro.'), expectedMessage);
    return true;
  });
  assert.equal(
    clientErrorMessage(new Error('watering denied: private.secret_table rejected a request'), 'Error seguro.'),
    'Error seguro.',
  );
});
