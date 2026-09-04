package com.example.ecosphere.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecosphere.shared.AuthValidation

private val LoginGreen = Color(0xFF5CFF72)
private val LoginSurface = Color(0xFF101914)
private val LoginBackground = Color(0xFF0B0F0D)

@Composable
fun DesktopAuthScreen(
    state: DesktopAuthState,
    onShowLogin: () -> Unit,
    onShowRegister: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String, String, String, String, String, String) -> Unit,
    onOAuth: (String) -> Unit,
    onVerifyMfa: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Row(Modifier.fillMaxSize().background(LoginBackground)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF0D2117))
                .padding(54.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("ES  EcoSphere", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.widthIn(max = 620.dp)) {
                Text(
                    "EL FUTURO ECHA RAÍCES",
                    color = LoginGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "La vida puede prosperar\nen cualquier lugar.",
                    fontSize = 52.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "EcoSphere crea y regula las condiciones ideales para que cada ecosistema prospere de forma autónoma.",
                    color = Color(0xFFB8C9BF),
                    fontSize = 16.sp,
                    lineHeight = 26.sp
                )
            }
            Text("EcoSphere Desktop 1.4.5", color = Color(0xFF88A396), fontSize = 11.sp)
        }

        Box(
            modifier = Modifier.weight(1.15f).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 480.dp, max = 580.dp)
                    .heightIn(max = 840.dp),
                color = LoginSurface,
                shape = RoundedCornerShape(24.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(34.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "ECOSPHERE CONTROL",
                        color = LoginGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text("Bienvenido", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("Accede con tu cuenta o registra una nueva.", color = Color(0xFFB8C9BF))

                    if (state.page in setOf(DesktopAuthPage.LOGIN, DesktopAuthPage.REGISTER)) {
                        DesktopAuthTabs(
                            login = state.page == DesktopAuthPage.LOGIN,
                            busy = state.busy,
                            onShowLogin = onShowLogin,
                            onShowRegister = onShowRegister
                        )
                    }

                    state.message?.let {
                        Surface(color = Color(0xFF17251D), shape = RoundedCornerShape(12.dp)) {
                            Text(it, Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp)
                        }
                    }

                    when (state.page) {
                        DesktopAuthPage.INITIALIZING -> DesktopLoading()
                        DesktopAuthPage.LOGIN -> DesktopLoginForm(state.busy, onSignIn, onOAuth)
                        DesktopAuthPage.REGISTER -> DesktopRegisterForm(
                            busy = state.busy,
                            errors = state.fieldErrors,
                            onRegister = onRegister,
                            onOAuth = onOAuth
                        )
                        DesktopAuthPage.PENDING -> DesktopPending(
                            state.profile?.fullName.orEmpty(),
                            state.busy,
                            onSignOut
                        )
                        DesktopAuthPage.MFA -> DesktopMfa(
                            state.mfaSecret,
                            state.busy,
                            onVerifyMfa,
                            onSignOut
                        )
                        DesktopAuthPage.APP -> Unit
                    }
                }
            }

            if (state.busy && state.page != DesktopAuthPage.INITIALIZING) {
                CircularProgressIndicator(color = LoginGreen)
            }
        }
    }
}

@Composable
private fun DesktopAuthTabs(
    login: Boolean,
    busy: Boolean,
    onShowLogin: () -> Unit,
    onShowRegister: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(LoginBackground, RoundedCornerShape(14.dp)).padding(4.dp)
    ) {
        if (login) {
            Button(onClick = onShowLogin, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Iniciar sesión") }
            TextButton(onClick = onShowRegister, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Crear cuenta") }
        } else {
            TextButton(onClick = onShowLogin, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Iniciar sesión") }
            Button(onClick = onShowRegister, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Crear cuenta") }
        }
    }
}

@Composable
private fun DesktopLoginForm(
    busy: Boolean,
    onSignIn: (String, String) -> Unit,
    onOAuth: (String) -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    OutlinedTextField(
        identifier,
        { identifier = it },
        Modifier.fillMaxWidth(),
        label = { Text("Usuario o correo electrónico") },
        singleLine = true,
        enabled = !busy
    )
    OutlinedTextField(
        password,
        { password = it },
        Modifier.fillMaxWidth(),
        label = { Text("Contraseña") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation()
    )
    Button(
        onClick = { onSignIn(identifier, password) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = LoginGreen, contentColor = Color.Black)
    ) { Text("Ingresar", fontWeight = FontWeight.Bold) }
    DesktopOAuthDivider()
    DesktopOAuthButtons(busy, onOAuth)
    Text(
        "Si es tu primera vez, Google o GitHub crearán tu cuenta automáticamente.",
        color = Color(0xFFB8C9BF),
        fontSize = 11.sp
    )
}

@Composable
private fun DesktopRegisterForm(
    busy: Boolean,
    errors: Map<String, String>,
    onRegister: (String, String, String, String, String, String, String, String) -> Unit,
    onOAuth: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var dni by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(AuthValidation.DEFAULT_PHONE_INPUT) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    Text(
        "Completa todos los campos sólo si prefieres registrarte con correo y contraseña.",
        color = Color(0xFFB8C9BF),
        fontSize = 12.sp
    )
    DesktopField("Nombre de usuario", username, { username = it }, busy, errors["username"], "Ejemplo: usuario123")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DesktopField("Nombres", firstName, { firstName = it }, busy, errors["firstName"], modifier = Modifier.weight(1f))
        DesktopField("Apellidos", lastName, { lastName = it }, busy, errors["lastName"], modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DesktopField("DNI", dni, { dni = it.filter(Char::isDigit).take(8) }, busy, errors["dni"], modifier = Modifier.weight(1f))
        DesktopField(
            "Número de teléfono",
            phone,
            { phone = AuthValidation.formatPhoneInput(it) },
            busy,
            errors["phone"],
            "+51 999 999 999",
            Modifier.weight(1f)
        )
    }
    DesktopField("Correo electrónico", email, { email = it }, busy, errors["email"], "usuario@ejemplo.com")
    DesktopPasswordField("Contraseña", password, { password = it }, busy, errors["password"])
    DesktopPasswordField("Confirmar contraseña", confirmation, { confirmation = it }, busy, errors["passwordConfirmation"])
    Text("Usa al menos 12 caracteres.", color = Color(0xFFB8C9BF), fontSize = 11.sp)
    Button(
        onClick = { onRegister(username, firstName, lastName, dni, phone, email, password, confirmation) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = LoginGreen, contentColor = Color.Black)
    ) { Text("Registrarme con correo", fontWeight = FontWeight.Bold) }
    DesktopOAuthDivider()
    DesktopOAuthButtons(busy, onOAuth)
}

@Composable
private fun DesktopField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    busy: Boolean,
    error: String?,
    placeholder: String = "",
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Column(modifier) {
        OutlinedTextField(
            value,
            onValue,
            Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
            singleLine = true,
            enabled = !busy,
            isError = error != null
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
    }
}

@Composable
private fun DesktopPasswordField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    busy: Boolean,
    error: String?
) {
    Column {
        OutlinedTextField(
            value,
            onValue,
            Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            enabled = !busy,
            isError = error != null,
            visualTransformation = PasswordVisualTransformation()
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
    }
}

@Composable
private fun DesktopOAuthDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text("  O CONTINÚA CON  ", fontSize = 10.sp, color = Color(0xFF8DA498))
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun DesktopOAuthButtons(busy: Boolean, onOAuth: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            { onOAuth(DesktopAuthController.PROVIDER_GOOGLE) },
            Modifier.weight(1f),
            enabled = !busy
        ) { Text("G  Google", fontWeight = FontWeight.Bold) }
        OutlinedButton(
            { onOAuth(DesktopAuthController.PROVIDER_GITHUB) },
            Modifier.weight(1f),
            enabled = !busy
        ) { Text("GH  GitHub", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun DesktopLoading() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(color = LoginGreen)
        Text("Validando la sesión segura…")
    }
}

@Composable
private fun DesktopPending(name: String, busy: Boolean, onSignOut: () -> Unit) {
    Text(name.ifBlank { "Tu cuenta" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Cuando un administrador apruebe la cuenta podrás acceder al microclima.")
    OutlinedButton(onSignOut, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cerrar sesión") }
}

@Composable
private fun DesktopMfa(
    secret: String?,
    busy: Boolean,
    onVerify: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    Text(
        if (secret == null) "Verificación en dos pasos" else "Configura Google Authenticator",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        if (secret == null) {
            "Abre Google Authenticator e ingresa el código actual de seis dígitos."
        } else {
            "En Google Authenticator elige «Ingresar clave de configuración» y usa esta clave:"
        }
    )
    if (secret != null) {
        Surface(color = Color(0xFF17251D), shape = RoundedCornerShape(12.dp)) {
            Text(secret, Modifier.fillMaxWidth().padding(14.dp), fontWeight = FontWeight.Bold)
        }
    }
    OutlinedTextField(
        code,
        { code = it.filter(Char::isDigit).take(6) },
        Modifier.fillMaxWidth(),
        label = { Text("Código de 6 dígitos") },
        singleLine = true,
        enabled = !busy
    )
    Button(
        { onVerify(code) },
        Modifier.fillMaxWidth(),
        enabled = !busy && code.length == 6
    ) { Text(if (secret == null) "Verificar código" else "Activar autenticador") }
    TextButton(onSignOut, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cerrar sesión") }
}
