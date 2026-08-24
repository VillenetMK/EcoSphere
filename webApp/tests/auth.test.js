import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  normalizeDni,
  normalizePhone,
  normalizeUsername,
  validateIdentityFields,
  validatePassword,
} from '../auth.js';

test('normaliza el nombre de usuario sin aceptar formatos ambiguos', () => {
  assert.equal(normalizeUsername('  VillenetADMIN  '), 'VillenetADMIN');
  assert.equal(normalizeUsername('usuario con espacios'), 'usuario con espacios');
});

test('normaliza DNI y teléfonos peruanos sin inventar dígitos', () => {
  assert.equal(normalizeDni('12.345.678'), '12345678');
  assert.equal(normalizePhone('999 888 777'), '+51999888777');
  assert.equal(normalizePhone('+34 612 345 678'), '+34612345678');
});

test('acepta una identidad completa y rechaza datos incompletos', () => {
  const valid = validateIdentityFields({
    username: 'VillenetADMIN',
    fullName: 'Gabriel Villenet Montero',
    dni: '12345678',
    phone: '999888777',
    email: 'usuario@example.com',
  });
  assert.equal(valid.valid, true);
  assert.equal(valid.values.phone, '+51999888777');

  const invalid = validateIdentityFields({ username: 'x', fullName: 'Gabriel', dni: '123', phone: '99', email: 'correo' });
  assert.equal(invalid.valid, false);
  assert.deepEqual(Object.keys(invalid.errors).sort(), ['dni', 'email', 'fullName', 'phone', 'username']);
});

test('exige una contraseña larga y su confirmación exacta', () => {
  assert.equal(validatePassword('una-clave-segura-2026', 'una-clave-segura-2026').valid, true);
  assert.equal(validatePassword('corta', 'corta').valid, false);
  assert.equal(validatePassword('una-clave-segura-2026', 'otra-clave-segura').valid, false);
});

test('el registro contiene los campos obligatorios y los proveedores aprobados', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  for (const id of ['registerUsername', 'registerFullName', 'registerDni', 'registerPhone', 'registerEmail', 'registerPassword', 'registerPasswordConfirmation']) {
    assert.match(html, new RegExp(`id="${id}"`));
  }
  assert.match(html, /data-oauth-provider="google"/);
  assert.match(html, /data-oauth-provider="github"/);
  assert.match(html, /requieren aprobación/);
  assert.match(html, /id="mfaCode"/);
  assert.match(html, /Google Authenticator/);
  assert.match(html, /id="loginIdentifier"/);
});


test('la portada comunica el equilibrio entre vida y tecnología', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  assert.match(html, /ECOSISTEMA INTELIGENTE/);
  assert.match(html, /Donde la vida y la tecnología encuentran el equilibrio\./);
  assert.match(html, /cada ecosistema prospere de forma autónoma/);
});

test('el flujo administrativo exige AAL2 y usa las APIs TOTP oficiales', async () => {
  const source = await readFile(new URL('../auth.js', import.meta.url), 'utf8');
  assert.match(source, /profile\.role === 'admin'/);
  assert.match(source, /getAuthenticatorAssuranceLevel/);
  assert.match(source, /factorType: 'totp'/);
  assert.match(source, /challengeAndVerify/);
  assert.match(source, /functions\/v1\/username-login/);
});

test('la base exige MFA administrativo mediante políticas restrictivas', async () => {
  const migration = await readFile(
    new URL('../../supabase/migrations/20260824025744_admin_username_and_totp_enforcement.sql', import.meta.url),
    'utf8',
  );
  assert.equal((migration.match(/as restrictive/g) || []).length, 3);
  assert.match(migration, /auth\.jwt\(\)->>'aal'\) = 'aal2'/);
  assert.match(migration, /values \('villenetadmin', null\)/);
});

test('el acceso por usuario está aislado, limitado y no registra contraseñas', async () => {
  const edge = await readFile(
    new URL('../../supabase/functions/username-login/index.ts', import.meta.url),
    'utf8',
  );
  assert.match(edge, /npm:@supabase\/supabase-js@2\.112\.3/);
  assert.match(edge, /username_login_begin/);
  assert.match(edge, /username_login_failure/);
  assert.match(edge, />= 5|retry_after_seconds/);
  assert.match(edge, /allowedOrigins/);
  assert.doesNotMatch(edge, /console\.(log|debug|info).*password/i);
});

test('los datos personales no se envían como metadata editable del JWT', async () => {
  const source = await readFile(new URL('../auth.js', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /options:\s*\{\s*data\s*:/);
  assert.doesNotMatch(source, /service_role|sb_secret_/);
});

test('el registro social no muestra el correo completo de la sesión', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  const source = await readFile(new URL('../auth.js', import.meta.url), 'utf8');
  assert.match(html, /placeholder="Ejemplo: usuario@ejemplo\.com"/);
  assert.match(source, /emailInput\.value = ''/);
  assert.match(source, /\? 'usuario@ejemplo\.com'/);
  assert.doesNotMatch(source, /Correo verificado mediante/);
  assert.match(source, /profileCompletionSession\?\.user\?\.email/);
});
