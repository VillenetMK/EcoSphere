import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');

test('la PWA restringe recursos mediante una política CSP sin ejecución insegura', () => {
  assert.match(html, /http-equiv="Content-Security-Policy"/);
  assert.match(html, /default-src 'self'/);
  assert.match(html, /connect-src https:\/\/kslzmrddrhfyyrxyfmbw\.supabase\.co/);
  assert.match(html, /object-src 'none'/);
  assert.match(html, /base-uri 'none'/);
  assert.doesNotMatch(html, /'unsafe-inline'|'unsafe-eval'/);
});

test('la PWA no envía la URL de origen como referencia', () => {
  assert.match(html, /<meta name="referrer" content="no-referrer" \/>/);
});
