import test from 'node:test';
import assert from 'node:assert/strict';
import { createHandler, liveTokenBody } from './handler.ts';

const BASE = 'https://project.supabase.co';
const ORIGIN = 'https://villenetmk.github.io';
function harness(responses) {
  const calls = [];
  const handler = createHandler({
    supabaseUrl: BASE, serviceRoleKey: 'server-secret', now: () => 1_800_000_000_000,
    fetcher: async (url, options) => {
      calls.push({ url, options });
      const next = responses.shift();
      assert.ok(next, `Unexpected outbound call: ${url}`);
      return new Response(JSON.stringify(next.body), { status: next.status ?? 200 });
    },
  });
  const request = (action = 'token', extra = {}) => handler(new Request('https://edge/hever-ai', {
    method: 'POST', headers: { origin: ORIGIN, authorization: 'Bearer user-jwt', 'content-type': 'application/json', ...extra },
    body: JSON.stringify(typeof action === 'string' ? { action } : action),
  }));
  return { calls, request, handler };
}

test('unauthenticated request never accesses data or provider', async () => {
  const h = harness([]);
  assert.equal((await h.request('token', { authorization: '' })).status, 401);
  assert.equal(h.calls.length, 0);
});
test('disallowed origin is rejected before auth', async () => {
  const h = harness([]);
  assert.equal((await h.request('token', { origin: 'https://evil.example' })).status, 403);
});
test('ineligible signed-in account cannot mint token or read data', async () => {
  for (const action of ['token', 'data', 'access']) {
    const h = harness([{ body: false }]);
    assert.equal((await h.request(action)).status, 403);
    assert.equal(h.calls.length, 1);
    assert.ok(h.calls[0].url.endsWith('/my_ai_access'));
    assert.equal(h.calls[0].options.headers.Authorization, 'Bearer user-jwt');
  }
});
test('eligible accounts can open the assistant using their own sessions', async () => {
  for (const jwt of ['operator-one-jwt', 'operator-two-jwt', 'admin-mfa-jwt']) {
    const h = harness([{ body: true }]);
    const response = await h.request('access', { authorization: `Bearer ${jwt}` });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { allowed: true });
    assert.equal(h.calls.length, 1);
    assert.equal(h.calls[0].options.headers.Authorization, `Bearer ${jwt}`);
    assert.equal(h.calls[0].options.body, '{}');
  }
});
test('invalid JWT stays unauthorized', async () => {
  const h = harness([{ status: 401, body: { message: 'JWT expired' } }]);
  assert.equal((await h.request()).status, 401);
  assert.equal(h.calls.length, 1);
});
test('extra user identity or model parameters cannot alter access/config', async () => {
  const h = harness([]);
  assert.equal((await h.request({ action: 'token', user_id: 'hever', model: 'other' })).status, 400);
  assert.equal(h.calls.length, 0);
});
test('rate limit prevents key retrieval and provider call', async () => {
  const h = harness([{ body: true }, { status: 400, body: { message: 'AI_SESSION_RATE_LIMIT' } }]);
  assert.equal((await h.request()).status, 429);
  assert.equal(h.calls.length, 2);
});
test('permanent key is used only server-side; limited token is returned', async () => {
  const h = harness([{ body: true }, { body: true }, { body: 'permanent-google-key' }, { body: { name: 'auth_tokens/short-lived' } }]);
  const response = await h.request();
  assert.equal(response.status, 200);
  const raw = await response.text();
  assert.ok(!raw.includes('permanent-google-key'));
  assert.ok(!raw.includes('server-secret'));
  assert.equal(JSON.parse(raw).token, 'auth_tokens/short-lived');
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal(h.calls[2].options.headers.Authorization, 'Bearer server-secret');
  assert.equal(h.calls[3].options.headers['x-goog-api-key'], 'permanent-google-key');
  const body = JSON.parse(h.calls[3].options.body);
  assert.equal(body.uses, 1);
  assert.equal(Date.parse(body.expireTime) - 1_800_000_000_000, 600_000);
  assert.equal(Date.parse(body.newSessionExpireTime) - 1_800_000_000_000, 60_000);
  assert.equal(body.fieldMask, undefined);
  assert.deepEqual(body.bidiGenerateContentSetup.tools[0].functionDeclarations.map(x => x.name), ['consultar_biohuerto']);
});
test('data endpoint preserves missing/stale readings; never synthesizes values', async () => {
  const data = { fuente: 'api', lecturas: { temperatura: { valor: null, vigente: false } }, dispositivo: { conectado: false } };
  const h = harness([{ body: true }, { body: data }]);
  assert.deepEqual(await (await h.request('data')).json(), data);
  assert.equal(h.calls.length, 2);
  assert.equal(h.calls[1].options.headers.Authorization, 'Bearer user-jwt');
});
test('provider error text and keys are never reflected', async () => {
  const h = harness([{ body: true }, { body: true }, { body: 'private-key' }, { status: 403, body: { error: 'private-key debug' } }]);
  const r = await h.request();
  assert.equal(r.status, 503);
  assert.ok(!(await r.text()).includes('private-key'));
});
test('configuration cannot grant control of actuators', () => {
  const setup = liveTokenBody(Date.now()).bidiGenerateContentSetup;
  assert.match(setup.systemInstruction.parts[0].text, /no puede encender/);
  assert.match(setup.systemInstruction.parts[0].text, /Nunca inventes/);
});
