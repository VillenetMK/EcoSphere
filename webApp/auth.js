import {
  SUPABASE_PUBLISHABLE_KEY,
  SUPABASE_URL,
  supabase,
} from './supabase-client.js';

const PENDING_REGISTRATION_KEY = 'ecosphere.pending-registration';
const OAUTH_INTENT_KEY = 'ecosphere.oauth-intent';
const ALLOWED_PROVIDERS = new Set(['google', 'github']);
let profileCompletionSession = null;
let pendingMfa = null;

export function normalizeUsername(value) {
  return String(value ?? '').trim();
}

export function normalizeDni(value) {
  return String(value ?? '').replace(/\D/g, '').slice(0, 8);
}

export function normalizePhone(value) {
  const raw = String(value ?? '').trim();
  const digits = raw.replace(/\D/g, '');
  if (/^9\d{8}$/.test(digits)) return `+51${digits}`;
  return raw.startsWith('+') ? `+${digits}` : digits;
}

export function validateIdentityFields(values) {
  const username = normalizeUsername(values.username);
  const firstName = String(values.firstName ?? '').trim().replace(/\s+/g, ' ');
  const lastName = String(values.lastName ?? '').trim().replace(/\s+/g, ' ');
  const dni = normalizeDni(values.dni);
  const phone = normalizePhone(values.phone);
  const email = String(values.email ?? '').trim().toLowerCase();
  const errors = {};

  if (!/^[A-Za-z][A-Za-z0-9._-]{2,31}$/.test(username)) {
    errors.username = 'Usa entre 3 y 32 caracteres: letras, números, punto, guion o guion bajo.';
  }
  const personNamePattern = /^[\p{L}][\p{L} .'’-]*[\p{L}]$/u;
  if (firstName.length < 2 || firstName.length > 80 || !personNamePattern.test(firstName)) {
    errors.firstName = 'Ingresa tus nombres usando sólo letras, espacios, apóstrofes o guiones.';
  }
  if (lastName.length < 2 || lastName.length > 80 || !personNamePattern.test(lastName)) {
    errors.lastName = 'Ingresa tus apellidos usando sólo letras, espacios, apóstrofes o guiones.';
  }
  if (!/^\d{8}$/.test(dni)) errors.dni = 'El DNI debe tener exactamente 8 dígitos.';
  if (!/^\+[1-9]\d{7,14}$/.test(phone)) errors.phone = 'Ingresa un teléfono válido; por ejemplo, +51 999 999 999.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
    errors.email = 'Ingresa un correo electrónico válido.';
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors,
    values: { username, firstName, lastName, dni, phone, email },
  };
}

export function validatePassword(password, confirmation) {
  const errors = {};
  if (String(password ?? '').length < 12) errors.password = 'La contraseña debe tener al menos 12 caracteres.';
  if (password !== confirmation) errors.passwordConfirmation = 'Las contraseñas no coinciden.';
  return { valid: Object.keys(errors).length === 0, errors };
}

function callbackUrl() {
  const url = new URL(window.location.href);
  url.search = '';
  url.hash = '';
  return url.toString();
}

function setMessage(message, kind = 'info') {
  const element = document.getElementById('authMessage');
  element.textContent = message;
  element.dataset.kind = kind;
  element.hidden = !message;
}

function setAuthBusy(value) {
  document.querySelectorAll('#authGate button, #authGate input').forEach(element => {
    element.disabled = value;
  });
  document.getElementById('authGate').classList.toggle('is-busy', value);
}

function showPanel(panel) {
  document.querySelectorAll('[data-auth-panel]').forEach(element => {
    element.hidden = element.dataset.authPanel !== panel;
  });
  document.querySelectorAll('[data-auth-tab]').forEach(element => {
    element.classList.toggle('active', element.dataset.authTab === panel);
  });
  document.getElementById('authTabs').hidden = ['pending', 'mfa'].includes(panel);
  setMessage('');
}

function setProfileCompletionMode(session) {
  profileCompletionSession = session;
  const enabled = Boolean(session);
  const emailInput = document.getElementById('registerEmail');
  emailInput.readOnly = enabled;
  emailInput.type = enabled ? 'text' : 'email';
  emailInput.autocomplete = enabled ? 'off' : 'email';
  emailInput.value = '';
  emailInput.placeholder = enabled
    ? 'usuario@ejemplo.com'
    : 'Ejemplo: usuario@ejemplo.com';
  document.getElementById('registerPasswordFields').hidden = enabled;
  document.getElementById('registerPasswordNote').hidden = enabled;
  document.getElementById('registerOAuthFields').hidden = enabled;
  document.getElementById('registerSubmitBtn').textContent = enabled
    ? 'Completar registro'
    : 'Registrarme con correo';
}

function registrationValues() {
  return {
    username: document.getElementById('registerUsername').value,
    firstName: document.getElementById('registerFirstName').value,
    lastName: document.getElementById('registerLastName').value,
    dni: document.getElementById('registerDni').value,
    phone: document.getElementById('registerPhone').value,
    email: profileCompletionSession?.user?.email ?? document.getElementById('registerEmail').value,
  };
}

function showFieldErrors(errors) {
  document.querySelectorAll('[data-field-error]').forEach(element => { element.textContent = ''; });
  Object.entries(errors).forEach(([field, message]) => {
    const element = document.querySelector(`[data-field-error="${field}"]`);
    if (element) element.textContent = message;
  });
}

function savePendingRegistration(values, provider) {
  sessionStorage.setItem(PENDING_REGISTRATION_KEY, JSON.stringify({ ...values, provider }));
  sessionStorage.setItem(OAUTH_INTENT_KEY, provider === 'email' ? 'register' : 'oauth-register');
}

function readPendingRegistration() {
  try {
    return JSON.parse(sessionStorage.getItem(PENDING_REGISTRATION_KEY) || 'null');
  } catch {
    return null;
  }
}

function clearPendingRegistration() {
  sessionStorage.removeItem(PENDING_REGISTRATION_KEY);
  sessionStorage.removeItem(OAUTH_INTENT_KEY);
}

async function completePendingProfile(session) {
  const pending = readPendingRegistration();
  if (!pending) return false;
  if (String(session.user.email ?? '').toLowerCase() !== pending.email) {
    await supabase.auth.signOut();
    clearPendingRegistration();
    throw new Error('El correo verificado no coincide con el correo ingresado en el registro.');
  }

  const { error } = await supabase.rpc('complete_user_profile', {
    p_username: pending.username,
    p_first_name: pending.firstName,
    p_last_name: pending.lastName,
    p_dni: pending.dni,
    p_phone: pending.phone,
    p_expected_email: pending.email,
  });
  if (error) throw error;
  clearPendingRegistration();
  return true;
}

async function loadMyProfile() {
  const { data, error } = await supabase.rpc('my_profile');
  if (error) throw error;
  return Array.isArray(data) ? data[0] ?? null : data;
}

function showPending(profile) {
  showPanel('pending');
  const displayName = profile?.full_name
    || [profile?.first_name, profile?.last_name].filter(Boolean).join(' ');
  document.getElementById('pendingName').textContent = displayName || 'Tu cuenta';
  document.getElementById('pendingStatus').textContent = profile?.status === 'blocked'
    ? 'La cuenta está bloqueada. Comunícate con el administrador.'
    : 'El registro está completo y espera aprobación del administrador.';
}

function resetMfaState() {
  pendingMfa = null;
  document.getElementById('mfaCode').value = '';
  document.getElementById('mfaEnrollment').hidden = true;
  document.getElementById('mfaQrCode').removeAttribute('src');
  document.getElementById('mfaSecret').textContent = '';
}

function showMfaChallenge({ enrollment, qrCode = '', secret = '' }) {
  showPanel('mfa');
  document.getElementById('mfaEnrollment').hidden = !enrollment;
  document.getElementById('mfaTitle').textContent = enrollment
    ? 'Configura Google Authenticator'
    : 'Verificación en dos pasos';
  document.getElementById('mfaDescription').textContent = enrollment
    ? 'Escanea el código y escribe el primer código de seis dígitos para proteger tu cuenta administrativa.'
    : 'Abre Google Authenticator e ingresa el código actual de seis dígitos.';
  document.getElementById('mfaVerifyBtn').textContent = enrollment
    ? 'Activar autenticador'
    : 'Verificar código';
  if (enrollment) {
    document.getElementById('mfaQrCode').src = qrCode;
    document.getElementById('mfaSecret').textContent = secret;
  }
  document.getElementById('mfaCode').focus();
}

async function requireAdminMfa(session, profile, onAccessGranted) {
  const { data: assurance, error: assuranceError } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel();
  if (assuranceError) throw assuranceError;
  if (assurance.currentLevel === 'aal2') {
    onAccessGranted({ session, profile });
    return true;
  }

  const { data: factors, error: factorsError } = await supabase.auth.mfa.listFactors();
  if (factorsError) throw factorsError;
  const verifiedFactor = factors.totp[0];
  if (verifiedFactor) {
    pendingMfa = { factorId: verifiedFactor.id, profile, onAccessGranted };
    showMfaChallenge({ enrollment: false });
    return false;
  }

  const abandonedFactors = factors.all.filter(factor => (
    factor.factor_type === 'totp' && factor.status !== 'verified'
  ));
  for (const factor of abandonedFactors) {
    const { error } = await supabase.auth.mfa.unenroll({ factorId: factor.id });
    if (error) throw error;
  }

  const { data: enrollment, error: enrollmentError } = await supabase.auth.mfa.enroll({
    factorType: 'totp',
    friendlyName: `EcoSphere ${profile.username || 'Administrador'}`,
  });
  if (enrollmentError) throw enrollmentError;
  pendingMfa = { factorId: enrollment.id, profile, onAccessGranted };
  showMfaChallenge({
    enrollment: true,
    qrCode: enrollment.totp.qr_code,
    secret: enrollment.totp.secret,
  });
  return false;
}

async function verifyMfaCode(code) {
  if (!pendingMfa) throw new Error('La verificación expiró. Inicia sesión nuevamente.');
  if (!/^\d{6}$/.test(code)) throw new Error('Ingresa exactamente los seis dígitos del autenticador.');
  const { error } = await supabase.auth.mfa.challengeAndVerify({
    factorId: pendingMfa.factorId,
    code,
  });
  if (error) throw error;

  const { data: assurance, error: assuranceError } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel();
  if (assuranceError) throw assuranceError;
  if (assurance.currentLevel !== 'aal2') throw new Error('No se pudo elevar la sesión a verificación completa.');

  const { data: { session }, error: sessionError } = await supabase.auth.getSession();
  if (sessionError) throw sessionError;
  if (!session) throw new Error('La sesión expiró durante la verificación.');
  const context = pendingMfa;
  resetMfaState();
  context.onAccessGranted({ session, profile: context.profile });
}

async function resolveSession(session, onAccessGranted) {
  if (!session) return false;
  const intent = sessionStorage.getItem(OAUTH_INTENT_KEY);
  const registrationIntent = ['register', 'oauth-register'].includes(intent);
  await completePendingProfile(session);
  const profile = await loadMyProfile();
  if (!profile && !registrationIntent) {
    clearPendingRegistration();
    await supabase.auth.signOut();
    setProfileCompletionMode(null);
    showPanel('login');
    setMessage('Esta cuenta aún no está registrada. Usa «Crear cuenta» para completar el alta.', 'error');
    return false;
  }
  if (!profile) {
    setProfileCompletionMode(session);
    showPanel('register');
    setMessage('Completa tus datos obligatorios para finalizar el registro.', 'info');
    return false;
  }
  clearPendingRegistration();
  setProfileCompletionMode(null);
  if (profile.status !== 'approved') {
    showPending(profile);
    return false;
  }
  if (profile.role === 'admin') {
    return requireAdminMfa(session, profile, onAccessGranted);
  }
  onAccessGranted({ session, profile });
  return true;
}

async function startOAuth(provider, intent) {
  if (!ALLOWED_PROVIDERS.has(provider)) throw new Error('Proveedor de acceso no permitido.');
  if (intent === 'register') {
    const identity = validateIdentityFields(registrationValues());
    showFieldErrors(identity.errors);
    if (!identity.valid) throw new Error('Revisa los datos obligatorios antes de continuar.');
    savePendingRegistration(identity.values, provider);
  } else {
    clearPendingRegistration();
    sessionStorage.setItem(OAUTH_INTENT_KEY, 'oauth-login');
  }

  const { data: { session }, error: sessionError } = await supabase.auth.getSession();
  if (sessionError) throw sessionError;
  if (session) {
    const { error: signOutError } = await supabase.auth.signOut({ scope: 'local' });
    if (signOutError) throw signOutError;
  }

  const { error } = await supabase.auth.signInWithOAuth({
    provider,
    options: {
      redirectTo: callbackUrl(),
      queryParams: { prompt: 'select_account' },
    },
  });
  if (error) throw error;
}

async function signInWithIdentifier(identifier, password) {
  const normalized = String(identifier ?? '').trim();
  if (normalized.includes('@')) {
    const { data, error } = await supabase.auth.signInWithPassword({
      email: normalized.toLowerCase(),
      password,
    });
    if (error) throw error;
    return data.session;
  }

  if (!/^[A-Za-z][A-Za-z0-9._-]{2,31}$/.test(normalized)) {
    throw new Error('Usuario o contraseña incorrectos.');
  }
  const response = await fetch(`${SUPABASE_URL}/functions/v1/username-login`, {
    method: 'POST',
    headers: {
      apikey: SUPABASE_PUBLISHABLE_KEY,
      Authorization: `Bearer ${SUPABASE_PUBLISHABLE_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username: normalized, password }),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.error || 'No se pudo iniciar sesión.');
  const { data, error } = await supabase.auth.setSession({
    access_token: result.access_token,
    refresh_token: result.refresh_token,
  });
  if (error) throw error;
  return data.session;
}

export async function initializeAuth({ onAccessGranted, onSignedOut }) {
  document.querySelectorAll('[data-auth-tab]').forEach(button => {
    button.addEventListener('click', () => showPanel(button.dataset.authTab));
  });

  document.getElementById('loginForm').addEventListener('submit', async event => {
    event.preventDefault();
    setAuthBusy(true);
    setMessage('');
    try {
      clearPendingRegistration();
      sessionStorage.setItem(OAUTH_INTENT_KEY, 'password-login');
      const identifier = document.getElementById('loginIdentifier').value;
      const password = document.getElementById('loginPassword').value;
      const session = await signInWithIdentifier(identifier, password);
      await resolveSession(session, onAccessGranted);
    } catch (error) {
      setMessage(error.message || 'No se pudo iniciar sesión.', 'error');
    } finally {
      setAuthBusy(false);
    }
  });

  document.getElementById('registerForm').addEventListener('submit', async event => {
    event.preventDefault();
    setAuthBusy(true);
    setMessage('');
    try {
      const identity = validateIdentityFields(registrationValues());
      if (profileCompletionSession) {
        showFieldErrors(identity.errors);
        if (!identity.valid) throw new Error('Revisa los datos del formulario.');
        if (identity.values.email !== String(profileCompletionSession.user.email ?? '').toLowerCase()) {
          throw new Error('El correo debe coincidir con la cuenta verificada.');
        }
        savePendingRegistration(identity.values, 'email');
        await completePendingProfile(profileCompletionSession);
        const profile = await loadMyProfile();
        if (!profile) throw new Error('No se pudo completar el perfil.');
        setProfileCompletionMode(null);
        showPending(profile);
        return;
      }
      const password = validatePassword(
        document.getElementById('registerPassword').value,
        document.getElementById('registerPasswordConfirmation').value,
      );
      const errors = { ...identity.errors, ...password.errors };
      showFieldErrors(errors);
      if (!identity.valid || !password.valid) throw new Error('Revisa los datos del formulario.');
      savePendingRegistration(identity.values, 'email');
      const { data, error } = await supabase.auth.signUp({
        email: identity.values.email,
        password: document.getElementById('registerPassword').value,
        options: { emailRedirectTo: callbackUrl() },
      });
      if (error) throw error;
      if (data.session) await resolveSession(data.session, onAccessGranted);
      else setMessage('Revisa tu correo y confirma la cuenta para completar el registro.', 'success');
    } catch (error) {
      setMessage(error.message || 'No se pudo crear la cuenta.', 'error');
    } finally {
      setAuthBusy(false);
    }
  });

  document.querySelectorAll('[data-oauth-provider]').forEach(button => {
    button.addEventListener('click', async () => {
      setAuthBusy(true);
      setMessage('');
      try {
        await startOAuth(button.dataset.oauthProvider, button.dataset.oauthIntent);
      } catch (error) {
        setMessage(error.message || 'No se pudo iniciar el acceso social.', 'error');
        setAuthBusy(false);
      }
    });
  });

  document.getElementById('mfaForm').addEventListener('submit', async event => {
    event.preventDefault();
    setAuthBusy(true);
    setMessage('');
    try {
      await verifyMfaCode(document.getElementById('mfaCode').value.trim());
    } catch (error) {
      setMessage(error.message || 'No se pudo verificar el autenticador.', 'error');
    } finally {
      setAuthBusy(false);
    }
  });

  document.querySelectorAll('[data-auth-signout]').forEach(button => {
    button.addEventListener('click', async () => {
      await supabase.auth.signOut();
      setProfileCompletionMode(null);
      resetMfaState();
      onSignedOut();
      showPanel('login');
    });
  });

  supabase.auth.onAuthStateChange(event => {
    if (event !== 'SIGNED_OUT') return;
    setProfileCompletionMode(null);
    resetMfaState();
    onSignedOut();
    showPanel('login');
  });

  const { data: { session }, error } = await supabase.auth.getSession();
  if (error) setMessage(error.message, 'error');
  if (!session) {
    setProfileCompletionMode(null);
    resetMfaState();
    showPanel('login');
    return;
  }
  setAuthBusy(true);
  try {
    await resolveSession(session, onAccessGranted);
  } catch (sessionError) {
    setMessage(sessionError.message || 'No se pudo validar la sesión.', 'error');
  } finally {
    setAuthBusy(false);
  }
}
