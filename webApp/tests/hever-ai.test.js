import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { createHeverAssistant } from '../hever-ai.js';
import { realReading, timestampIsCurrent, deviceIsCurrent } from '../hever-ai/panel.js';

function environment(request) {
  const events = {};
  const messages = [];
  const button = { hidden: true };
  const host = { children: [], replaceChildren() { this.children = []; }, appendChild(frame) { this.children.push(frame); } };
  globalThis.location = { origin: 'https://villenetmk.github.io' };
  globalThis.window = { addEventListener(type, listener) { events[type] = listener; } };
  globalThis.document = { createElement() { return { contentWindow: { postMessage(message) { messages.push(message); } }, remove() { host.children = []; } }; } };
  const assistant = createHeverAssistant({ button, host, request });
  return { assistant, button, host, events, messages };
}
test('el asistente permanece oculto hasta confirmar el permiso del servidor', async () => {
  const env = environment(async () => ({ allowed: false }));
  assert.equal(env.assistant.open(), false);
  await env.assistant.checkAccess();
  assert.equal(env.button.hidden, true);
  assert.equal(env.assistant.open(), false);
});
test('una respuesta antigua de acceso no habilita otra sesión', async () => {
  let resolve;
  const env = environment(() => new Promise(done => { resolve = done; }));
  const pending = env.assistant.checkAccess();
  env.assistant.reset();
  resolve({ allowed: true });
  await pending;
  assert.equal(env.button.hidden, true);
  assert.equal(env.assistant.open(), false);
});
test('navegar por otras pantallas no cancela la comprobación inicial de acceso', async () => {
  let resolve;
  const env = environment(() => new Promise(done => { resolve = done; }));
  const pending = env.assistant.checkAccess();
  env.assistant.close();
  resolve({ allowed: true });
  await pending;
  assert.equal(env.button.hidden, false);
  assert.equal(env.assistant.open(), true);
});
test('el puente rechaza ventanas y operaciones ajenas y no entrega tokens tras salir', async () => {
  let resolveToken;
  const calls = [];
  const env = environment(action => {
    calls.push(action);
    return action === 'access' ? Promise.resolve({ allowed: true }) : new Promise(done => { resolveToken = done; });
  });
  await env.assistant.checkAccess();
  env.assistant.open();
  const frame = env.host.children[0];
  const message = { channel: 'ecosphere-ai', type: 'request', action: 'token', id: 'request-1' };
  await env.events.message({ origin: 'https://other.invalid', source: frame.contentWindow, data: message });
  await env.events.message({ origin: location.origin, source: {}, data: message });
  await env.events.message({ origin: location.origin, source: frame.contentWindow, data: { ...message, action: 'pump' } });
  assert.deepEqual(calls, ['access']);
  const pending = env.events.message({ origin: location.origin, source: frame.contentWindow, data: message });
  env.assistant.reset();
  resolveToken({ token: 'auth_tokens/private-session' });
  await pending;
  assert.equal(env.messages.some(item => item.result?.token), false);
  assert.equal(env.host.children.length, 0);
});
test('las lecturas ausentes, simuladas o inválidas nunca se convierten en valores reales', () => {
  for (const value of [null, {}, { valor: null }, { valor: '30' }, { valor: NaN }, { valor: 30, simulado: true }]) assert.ok(!realReading(value));
  assert.ok(realReading({ valor: 0, vigente: false }));
  assert.ok(realReading({ valor: 30, vigente: true }));
});
test('el paso del tiempo y los errores de consulta invalidan la conexión aunque la respuesta anterior decía conectado', () => {
  const now = Date.parse('2026-09-09T23:00:00Z');
  const device = { conectado: true, ultima_conexion: '2026-09-09T22:59:50Z' };
  assert.equal(deviceIsCurrent(device, true, now), true);
  assert.equal(deviceIsCurrent(device, false, now), false);
  assert.equal(deviceIsCurrent(device, true, now + 30000), false);
  assert.equal(timestampIsCurrent('2026-09-09T23:01:00Z', now), false);
  assert.equal(timestampIsCurrent(null, now), false);
});
test('el audio mantiene la frecuencia de 16 kHz a través de bloques de 44.1 y 48 kHz', async () => {
  const code = await readFile(new URL('../hever-ai/pcm-worklet.js', import.meta.url), 'utf8');
  for (const rate of [44100, 48000]) {
    let Processor;
    const chunks = [];
    vm.runInNewContext(code, {
      AudioWorkletProcessor: class { constructor() { this.port = { postMessage(chunk) { chunks.push(chunk); } }; } },
      sampleRate: rate,
      registerProcessor(_name, implementation) { Processor = implementation; },
    });
    const processor = new Processor();
    for (let offset = 0; offset < rate * 2 + 256; offset += 128) processor.process([[new Float32Array(128).fill(.5)]]);
    const count = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    assert.ok(count >= 32000 && count < 32320, `${rate}: emitted ${count} samples`);
    assert.equal(chunks[0].length, 320);
    assert.equal(chunks[0][0], 16384);
  }
});
