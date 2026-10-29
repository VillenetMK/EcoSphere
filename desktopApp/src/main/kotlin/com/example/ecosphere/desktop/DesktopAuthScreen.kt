package com.example.ecosphere.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecosphere.shared.AuthValidation

private class AuthFormValues {
    var identifier by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var username by mutableStateOf("")
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var dni by mutableStateOf("")
    var phone by mutableStateOf(AuthValidation.DEFAULT_PHONE_INPUT)
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmation by mutableStateOf("")
}

@Composable
fun DesktopAuthScreen(
    state: DesktopAuthState,
    onShowLogin: () -> Unit,
    onShowRegister: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String, String, String, String, String, String) -> Unit,
    onOAuth: (String) -> Unit,
    onVerifyMfa: (String) -> Unit,
    onSignOut: () -> Unit,
    onCancelOAuth: () -> Unit = {}
) {
    // Preserve edits while switching tabs. Passwords remain only in memory, never Preferences.
    val form = remember { AuthFormValues() }
    LaunchedEffect(state.verifiedEmail) {
        state.verifiedEmail?.let { form.email = it }
    }
    Surface(Modifier.fillMaxSize(), color = EcoBackground, contentColor = EcoText) {
        BoxWithConstraints {
            val wide = maxWidth >= 1040.dp && maxHeight >= 640.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) AuthBrandPanel(Modifier.weight(0.9f).fillMaxHeight())
                val scroll = rememberScrollState()
                LaunchedEffect(state.page, state.message) { scroll.scrollTo(0) }
                Box(Modifier.weight(1.1f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scroll).padding(if (wide) 32.dp else 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (!wide) DesktopBrand(Modifier.widthIn(max = 540.dp).fillMaxWidth())
                        Surface(
                            modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth(),
                            color = EcoSurface, contentColor = EcoText,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(if (wide) 30.dp else 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("TU ECOSISTEMA, MÁS CERCA", color = EcoAccent, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    when (state.page) {
                                        DesktopAuthPage.REGISTER -> if (state.verifiedEmail == null) "Crea tu cuenta" else "Completa tu perfil"
                                        DesktopAuthPage.PENDING -> "Estado de tu cuenta"
                                        DesktopAuthPage.MFA -> "Protege tu ecosistema"
                                        DesktopAuthPage.INITIALIZING -> "Preparando EcoSphere"
                                        else -> "Qué bueno verte"
                                    },
                                    fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold
                                )
                                Text("Un espacio para cuidar lo que crece.", color = EcoMuted, fontSize = 14.sp)
                                if (state.page in setOf(DesktopAuthPage.LOGIN, DesktopAuthPage.REGISTER)) {
                                    AuthTabs(state.page == DesktopAuthPage.LOGIN, state.busy, onShowLogin, onShowRegister)
                                }
                                state.message?.let { message ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().testTag("auth-message").semantics { liveRegion = LiveRegionMode.Polite },
                                        color = Color(0xFF263A2D), contentColor = EcoText, shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text("Estado del acceso", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(message, fontSize = 13.sp, lineHeight = 19.sp)
                                        }
                                    }
                                }
                                if (state.oauthProvider != null) {
                                    OAuthWaiting(state.oauthProvider, onCancelOAuth)
                                } else if (state.busy && state.page != DesktopAuthPage.INITIALIZING) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Text("Procesando tu solicitud…", color = EcoMuted, fontSize = 12.sp)
                                }
                                when (state.page) {
                                    DesktopAuthPage.INITIALIZING -> {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                        Text("Comprobando tu sesión…", color = EcoMuted)
                                    }
                                    DesktopAuthPage.LOGIN -> LoginForm(form, state.busy, onSignIn, onOAuth)
                                    DesktopAuthPage.REGISTER -> RegisterForm(form, state, onRegister, onOAuth)
                                    DesktopAuthPage.PENDING -> {
                                        Text(state.profile?.fullName.orEmpty().ifBlank { "Tu cuenta" }, fontWeight = FontWeight.Bold)
                                        Text(if (state.profile?.status == "blocked") "Contacta con el administrador para revisar el acceso." else "Tu perfil está guardado. Podrás entrar cuando se apruebe el acceso.")
                                        OutlinedButton(onSignOut, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Cerrar sesión") }
                                    }
                                    DesktopAuthPage.MFA -> MfaForm(state, onVerifyMfa, onSignOut)
                                    DesktopAuthPage.APP -> Unit
                                }
                            }
                        }
                        Text("EcoSphere Desktop ${DesktopBuild.version}", color = EcoMuted, fontSize = 12.sp)
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scroll),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp).testTag("auth-scrollbar")
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthBrandPanel(modifier: Modifier) {
    Column(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFF163B25), Color(0xFF0D2117)))).padding(40.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        DesktopBrand()
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Image(painterResource("brand/ecosphere.png"), null, Modifier.size(100.dp))
            Text("EL FUTURO ECHA RAÍCES", color = EcoAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text("La vida puede\nprosperar en\ncualquier lugar.", color = EcoText, fontSize = 40.sp, lineHeight = 45.sp, fontWeight = FontWeight.Bold)
            Text("Observa tu microclima. Entiende sus cambios. Dale a cada ecosistema las condiciones para crecer.", color = EcoMuted, fontSize = 16.sp, lineHeight = 25.sp)
        }
        Text("Cuidado conectado. Control consciente.", color = EcoMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AuthTabs(login: Boolean, busy: Boolean, onLogin: () -> Unit, onRegister: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(EcoBackground, RoundedCornerShape(14.dp)).padding(4.dp)) {
        if (login) {
            Button(onLogin, Modifier.weight(1f), enabled = !busy) { Text("Iniciar sesión") }
            TextButton(onRegister, Modifier.weight(1f), enabled = !busy) { Text("Crear cuenta") }
        } else {
            TextButton(onLogin, Modifier.weight(1f), enabled = !busy) { Text("Iniciar sesión") }
            Button(onRegister, Modifier.weight(1f), enabled = !busy) { Text("Crear cuenta") }
        }
    }
}

private fun Modifier.submitOnEnter(enabled: Boolean, submit: () -> Unit): Modifier = onPreviewKeyEvent {
    if (enabled && it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
        submit(); true
    } else false
}

@Composable
private fun LoginForm(form: AuthFormValues, busy: Boolean, onSignIn: (String, String) -> Unit, onOAuth: (String) -> Unit) {
    val canSubmit = !busy && form.identifier.isNotBlank() && form.loginPassword.isNotBlank()
    val submit = { onSignIn(form.identifier, form.loginPassword) }
    OutlinedTextField(
        form.identifier, { form.identifier = it },
        Modifier.fillMaxWidth().testTag("login-identifier").submitOnEnter(canSubmit, submit),
        label = { Text("Usuario o correo electrónico") }, singleLine = true, enabled = !busy
    )
    PasswordField("Contraseña", form.loginPassword, { form.loginPassword = it }, busy,
        modifier = Modifier.testTag("login-password").submitOnEnter(canSubmit, submit))
    Button(submit, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("login-submit"), enabled = canSubmit) {
        Text("Entrar a mi ecosistema", fontWeight = FontWeight.Bold)
    }
    OAuthButtons(busy, onOAuth)
    Text("También puedes crear tu cuenta directamente con Google o GitHub.", color = EcoMuted, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
private fun RegisterForm(
    form: AuthFormValues, state: DesktopAuthState,
    onRegister: (String, String, String, String, String, String, String, String) -> Unit,
    onOAuth: (String) -> Unit
) {
    val completing = !state.verifiedEmail.isNullOrBlank()
    val submit = { onRegister(form.username, form.firstName, form.lastName, form.dni, form.phone, form.email, form.password, form.confirmation) }
    Text(if (completing) "Tu correo ya está confirmado. Falta completar tus datos." else "Usa tu correo o continúa con una cuenta de Google o GitHub.", color = EcoMuted, fontSize = 13.sp)
    AuthField("Nombre de usuario", form.username, { form.username = it }, state.busy, state.fieldErrors["username"], tag = "register-username")
    BoxWithConstraints {
        if (maxWidth >= 440.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthField("Nombres", form.firstName, { form.firstName = it }, state.busy, state.fieldErrors["firstName"], Modifier.weight(1f))
                AuthField("Apellidos", form.lastName, { form.lastName = it }, state.busy, state.fieldErrors["lastName"], Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthField("Nombres", form.firstName, { form.firstName = it }, state.busy, state.fieldErrors["firstName"])
                AuthField("Apellidos", form.lastName, { form.lastName = it }, state.busy, state.fieldErrors["lastName"])
            }
        }
    }
    AuthField("DNI", form.dni, { form.dni = it.filter(Char::isDigit).take(8) }, state.busy, state.fieldErrors["dni"])
    AuthField("Número de teléfono", form.phone, { form.phone = AuthValidation.formatPhoneInput(it) }, state.busy, state.fieldErrors["phone"])
    AuthField("Correo electrónico", form.email, { form.email = it }, state.busy, state.fieldErrors["email"], editable = !completing)
    if (!completing) {
        PasswordField("Contraseña", form.password, { form.password = it }, state.busy, state.fieldErrors["password"])
        Text("Al menos 12 caracteres.", color = EcoMuted, fontSize = 12.sp)
        PasswordField("Confirmar contraseña", form.confirmation, { form.confirmation = it }, state.busy,
            state.fieldErrors["passwordConfirmation"], Modifier.submitOnEnter(!state.busy, submit))
    }
    Button(submit, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("register-submit"), enabled = !state.busy) {
        Text(if (completing) "Guardar mi perfil" else "Crear mi cuenta", fontWeight = FontWeight.Bold)
    }
    if (!completing) OAuthButtons(state.busy, onOAuth)
}

@Composable
private fun AuthField(
    label: String, value: String, onValue: (String) -> Unit, busy: Boolean, error: String?,
    modifier: Modifier = Modifier, editable: Boolean = true, tag: String = label
) {
    OutlinedTextField(
        value, onValue, modifier.fillMaxWidth().testTag(tag), label = { Text(label) },
        singleLine = true, enabled = !busy, readOnly = !editable, isError = error != null,
        supportingText = error?.let { { Text(it) } }
    )
}

@Composable
private fun PasswordField(
    label: String, value: String, onValue: (String) -> Unit, busy: Boolean,
    error: String? = null, modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onValue, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        enabled = !busy, isError = error != null,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton({ visible = !visible }, enabled = !busy) { Text(if (visible) "Ocultar" else "Mostrar", fontSize = 11.sp) }
        },
        supportingText = error?.let { { Text(it) } }
    )
}

@Composable
private fun OAuthButtons(busy: Boolean, onOAuth: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text("  O CONTINÚA CON  ", color = EcoMuted, fontSize = 10.sp)
        HorizontalDivider(Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("google" to "Google", "github" to "GitHub").forEach { (provider, label) ->
            OutlinedButton({ onOAuth(provider) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy) {
                Image(painterResource("brand/ic_$provider.svg"), null, Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text(label, color = if (busy) EcoMuted else EcoText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OAuthWaiting(provider: String, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("oauth-waiting"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Continúa en ${if (provider == "google") "Google" else "GitHub"}", fontWeight = FontWeight.Bold)
        Text("Termina el acceso en tu navegador. Esta ventana lo recibirá automáticamente; esperaremos hasta 3 minutos.", color = EcoMuted, fontSize = 13.sp)
        OutlinedButton(onCancel) { Text("Cancelar acceso") }
    }
}

@Composable
private fun MfaForm(state: DesktopAuthState, onVerify: (String) -> Unit, onSignOut: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Text(if (state.mfaSecret == null) "Verificación en dos pasos" else "Configura tu autenticador", fontWeight = FontWeight.Bold)
    Text(if (state.mfaSecret == null) "Escribe el código de seis dígitos de tu aplicación de autenticación." else "En tu autenticador elige «Ingresar clave de configuración» y usa esta clave:", color = EcoMuted)
    state.mfaSecret?.let {
        Surface(color = Color(0xFF263A2D), contentColor = EcoText, shape = RoundedCornerShape(12.dp)) {
            Text(it, Modifier.fillMaxWidth().padding(14.dp), fontWeight = FontWeight.Bold)
        }
    }
    val canSubmit = !state.busy && code.length == 6
    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) },
        Modifier.fillMaxWidth().submitOnEnter(canSubmit) { onVerify(code) },
        label = { Text("Código de 6 dígitos") }, singleLine = true, enabled = !state.busy)
    Button({ onVerify(code) }, Modifier.fillMaxWidth(), enabled = canSubmit) { Text("Verificar código") }
    TextButton(onSignOut, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Cerrar sesión") }
}
