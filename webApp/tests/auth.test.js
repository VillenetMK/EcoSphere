import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  DEFAULT_PHONE_INPUT,
  formatPhoneInput,
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
  assert.equal(formatPhoneInput(DEFAULT_PHONE_INPUT), '+51');
  assert.equal(formatPhoneInput('+51 999 888 777 12345'), '+51 999 888 777');
  assert.equal(normalizePhone('+34 612 345 678'), '+34612345678');
});

test('el celular peruano exige nueve dígitos y comenzar con 9', () => {
  const base = {
    username: 'usuario123',
    firstName: 'Ana María',
    lastName: 'De la Cruz',
    dni: '12345678',
    email: 'usuario@example.com',
  };
  assert.equal(validateIdentityFields({ ...base, phone: '+51 999 888 777' }).valid, true);
  assert.match(validateIdentityFields({ ...base, phone: '+51 899 888 777' }).errors.phone, /comenzar con 9/);
  assert.match(validateIdentityFields({ ...base, phone: '+51 999 888 77' }).errors.phone, /9 dígitos/);
});

test('acepta una identidad completa y rechaza datos incompletos', () => {
  const valid = validateIdentityFields({
    username: 'VillenetADMIN',
    firstName: 'María José',
    lastName: 'Pérez de la Cruz',
    dni: '12345678',
    phone: '999888777',
    email: 'usuario@example.com',
  });
  assert.equal(valid.valid, true);
  assert.equal(valid.values.phone, '+51999888777');

  const invalid = validateIdentityFields({ username: 'x', firstName: '1', lastName: '', dni: '123', phone: '99', email: 'correo' });
  assert.equal(invalid.valid, false);
  assert.deepEqual(Object.keys(invalid.errors).sort(), ['dni', 'email', 'firstName', 'lastName', 'phone', 'username']);
});

test('exige una contraseña larga y su confirmación exacta', () => {
  assert.equal(validatePassword('una-clave-segura-2026', 'una-clave-segura-2026').valid, true);
  assert.equal(validatePassword('corta', 'corta').valid, false);
  assert.equal(validatePassword('una-clave-segura-2026', 'otra-clave-segura').valid, false);
});

test('el registro contiene los campos obligatorios y los proveedores aprobados', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  for (const id of ['registerUsername', 'registerFirstName', 'registerLastName', 'registerDni', 'registerPhone', 'registerEmail', 'registerPassword', 'registerPasswordConfirmation']) {
    assert.match(html, new RegExp(`id="${id}"`));
  }
  assert.match(html, /data-oauth-provider="google"/);
  assert.match(html, /data-oauth-provider="github"/);
  assert.match(html, /crearán tu cuenta automáticamente/);
  assert.match(html, /id="mfaCode"/);
  assert.match(html, /Google Authenticator/);
  assert.match(html, /id="loginIdentifier"/);
  assert.match(html, /id="registerPhone"[^>]*maxlength="16"[^>]*value="\+51 "/);
});

test('el registro por correo guarda nombres separados y OAuth no pide contraseña adicional', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  const source = await readFile(new URL('../auth.js', import.meta.url), 'utf8');
  const migration = await readFile(
    new URL('../../supabase/migrations/20260824084354_separate_profile_names.sql', import.meta.url),
    'utf8',
  );
  assert.match(html, /id="registerFirstName"[^>]*autocomplete="given-name"/);
  assert.match(html, /id="registerLastName"[^>]*autocomplete="family-name"/);
  assert.match(source, /p_first_name: pending\.firstName/);
  assert.match(source, /p_last_name: pending\.lastName/);
  assert.match(source, /registerPasswordFields'\)\.hidden = enabled/);
  assert.match(migration, /add column first_name text/);
  assert.match(migration, /add column last_name text/);
  assert.match(migration, /user_profiles_name_consistency/);
});


test('la portada comunica la visión de llevar vida a cualquier lugar', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  assert.match(html, /EL FUTURO ECHA RAÍCES/);
  assert.match(html, /La vida puede prosperar en cualquier lugar\./);
  assert.match(html, /cada ecosistema prospere de forma autónoma/);
});

test('la portada equilibra la jerarquía tipográfica en escritorio y móvil', async () => {
  const styles = await readFile(new URL('../styles.css', import.meta.url), 'utf8');
  assert.match(styles, /grid-template-columns:minmax\(380px,46%\) minmax\(480px,54%\)/);
  assert.match(styles, /\.auth-brand-copy h1\{[^}]*font-size:clamp\(44px,3\.5vw,60px\)[^}]*text-wrap:balance/);
  assert.match(styles, /@media\(max-width:560px\)\{[^}]*\.auth-brand-panel\{padding:22px 20px\}/);
});

test('Google y GitHub usan un único flujo para ingresar o crear la cuenta', async () => {
  const source = await readFile(new URL('../auth.js', import.meta.url), 'utf8');
  const nativeAuth = await readFile(
    new URL('../../app/src/main/java/com/example/ecosphere/auth/NativeAuthViewModel.kt', import.meta.url),
    'utf8',
  );
  const desktopAuth = await readFile(
    new URL('../../desktopApp/src/main/kotlin/com/example/ecosphere/desktop/DesktopAuthController.kt', import.meta.url),
    'utf8',
  );

  assert.match(source, /async function startOAuth\(provider\)/);
  assert.match(source, /sessionStorage\.setItem\(OAUTH_INTENT_KEY, 'oauth'\)/);
  assert.match(nativeAuth, /fun startOAuth\(provider: String\)/);
  assert.match(desktopAuth, /suspend fun startOAuth\(provider: String\)/);
  assert.doesNotMatch(`${source}\n${nativeAuth}\n${desktopAuth}`, /oauth-login|oauth-register/);

  const webOAuth = source.slice(source.indexOf('async function startOAuth'), source.indexOf('async function signInWithIdentifier'));
  const nativeOAuth = nativeAuth.slice(nativeAuth.indexOf('fun startOAuth('), nativeAuth.indexOf('fun onOAuthCallback'));
  const desktopOAuth = desktopAuth.slice(desktopAuth.indexOf('suspend fun startOAuth('), desktopAuth.indexOf('suspend fun verifyMfa'));
  assert.doesNotMatch(webOAuth, /validateIdentityFields|savePendingRegistration/);
  assert.doesNotMatch(nativeOAuth, /validateRegistration|savePendingRegistration/);
  assert.doesNotMatch(desktopOAuth, /validateRegistration|savePendingRegistration/);
});

test('Google permite elegir la cuenta antes de continuar', async () => {
  const source = await readFile(new URL('../auth.js', import.meta.url), 'utf8');
  assert.match(source, /auth\.getSession\(\)/);
  assert.match(source, /auth\.signOut\(\{ scope: 'local' \}\)/);
  assert.match(source, /provider === 'google' \? \{ prompt: 'select_account' \} : undefined/);
});

test('Supabase crea el perfil OAuth desde datos verificados sin inventar DNI ni teléfono', async () => {
  const migration = await readFile(
    new URL('../../supabase/migrations/20260904024445_auto_provision_oauth_profiles.sql', import.meta.url),
    'utf8',
  );

  assert.match(migration, /create trigger provision_ecosphere_oauth_profile/);
  assert.match(migration, /after insert or update of email_confirmed_at, raw_app_meta_data/);
  assert.match(migration, /v_email_confirmed_at is null/);
  assert.match(migration, /v_app_metadata->>'provider'/);
  assert.match(migration, /v_user_metadata->>'given_name'/);
  assert.match(migration, /null,\s*null,\s*v_email,\s*v_provider,\s*'approved',\s*'operator'/);
  assert.match(migration, /registration_method in \('google', 'github'\)/);
  assert.match(migration, /revoke all on function private\.provision_oauth_profile\(uuid\)/);
  assert.doesNotMatch(migration, /v_user_metadata->>'(?:role|status|is_admin)'/);
});

test('el retorno OAuth de Android vuelve al APK y no renderiza el portal web', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  const bridge = await readFile(new URL('../android-auth-return.js', import.meta.url), 'utf8');
  const nativeAuth = await readFile(
    new URL('../../app/src/main/java/com/example/ecosphere/auth/NativeAuthViewModel.kt', import.meta.url),
    'utf8',
  );
  const nativeSupabase = await readFile(
    new URL('../../app/src/main/java/com/example/ecosphere/auth/NativeSupabase.kt', import.meta.url),
    'utf8',
  );

  assert.match(html, /<script src="android-auth-return\.js"><\/script>/);
  assert.match(bridge, /ecosphere_client.*android/);
  assert.match(bridge, /new URL\('ecosphere:\/\/auth-callback'\)/);
  assert.match(bridge, /location\.replace\(callback\.toString\(\)\)/);
  assert.match(nativeSupabase, /ANDROID_OAUTH_RETURN_URL/);
  assert.match(nativeSupabase, /\?ecosphere_client=android/);
  assert.match(nativeAuth, /redirectUrl = NativeSupabase\.ANDROID_OAUTH_RETURN_URL/);
});

test('el administrador reservado se crea aprobado sin inventar datos personales', async () => {
  const migration = await readFile(
    new URL('../../supabase/migrations/20260824085600_bootstrap_reserved_admin_profile.sql', import.meta.url),
    'utf8',
  );
  assert.match(migration, /join private\.reserved_usernames/);
  assert.match(migration, /'VillenetADMIN'/);
  assert.match(migration, /'approved',\s*'admin'/);
  assert.match(migration, /role = 'admin' and dni is null/);
  assert.match(migration, /role = 'admin' and phone is null/);
  assert.doesNotMatch(migration, /[\w.+-]+@[\w.-]+/);
});

test('sin una sesión activa la portada muestra primero el inicio de sesión', async () => {
  const source = await readFile(new URL('../auth.js', import.meta.url), 'utf8');
  assert.match(
    source,
    /if \(!session\) \{\s*setProfileCompletionMode\(null\);\s*resetMfaState\(\);\s*showPanel\('login'\);/,
  );
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

test('la reparación del registro por correo protege el correo confirmado y reutiliza la sesión', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  const source = await readFile(new URL('../auth.js', import.meta.url), 'utf8');
  assert.match(html, /placeholder="Ejemplo: usuario@ejemplo\.com"/);
  assert.match(source, /function maskEmail/);
  assert.match(source, /emailInput\.value = enabled \? maskEmail/);
  assert.match(source, /profileCompletionSession\?\.user\?\.email/);
});

test('la confirmación conserva el alta pendiente y permite reparar cuentas sin perfil', async () => {
  const source = await readFile(new URL('../auth.js', import.meta.url), 'utf8');
  const nativeAuth = await readFile(
    new URL('../../app/src/main/java/com/example/ecosphere/auth/NativeAuthViewModel.kt', import.meta.url),
    'utf8',
  );
  const mobileAuth = await readFile(
    new URL('../../app/src/main/java/com/example/ecosphere/ui/mobile/MobileAuthScreen.kt', import.meta.url),
    'utf8',
  );

  assert.match(source, /localStorage\.setItem\(PENDING_REGISTRATION_KEY/);
  assert.match(source, /localStorage\.removeItem\(PENDING_REGISTRATION_KEY/);
  const loginHandler = source.slice(source.indexOf("loginForm'"), source.indexOf("registerForm'"));
  assert.doesNotMatch(loginHandler, /clearPendingRegistration\(\)/);
  assert.match(source, /Tu correo ya está confirmado\. Completa tus datos/);

  const nativeSignIn = nativeAuth.slice(nativeAuth.indexOf('fun signIn('), nativeAuth.indexOf('fun registerWithEmail('));
  assert.doesNotMatch(nativeSignIn, /clearPendingRegistration\(\)/);
  assert.match(nativeAuth, /signUpWith\(\s*Email,\s*redirectUrl = NativeSupabase\.ANDROID_OAUTH_RETURN_URL/);
  assert.match(nativeAuth, /val verifiedEmail: String\? = null/);
  assert.match(nativeAuth, /Tu correo ya está confirmado\. Completa tus datos/);
  assert.match(mobileAuth, /verifiedEmail = state\.verifiedEmail/);
  assert.match(mobileAuth, /if \(!completingProfile\) \{\s*MobilePasswordField/);
  assert.match(mobileAuth, /"Completar registro"/);
});
