package com.example.ecosphere.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecosphere.auth.NativeAuthPage
import com.example.ecosphere.auth.NativeAuthUiState
import com.example.ecosphere.auth.NativeAuthViewModel

private val AuthGreen = Color(0xFF5CFF72)
private val AuthSurface = Color(0xFF101914)

@Composable
fun NativeAuthScreen(
    state: NativeAuthUiState,
    onShowLogin: () -> Unit,
    onShowRegister: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String, String, String, String, String, String) -> Unit,
    onOAuth: (String, Boolean, String, String, String, String, String, String) -> Unit,
    onVerifyMfa: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column {
                    Text(
                        text = "ES  EcoSphere",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "EL FUTURO ECHA RAÍCES",
                        color = AuthGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "La vida puede prosperar\nen cualquier lugar.",
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AuthSurface,
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "ECOSPHERE CONTROL",
                            color = AuthGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text("Bienvenido", fontSize = 30.sp, fontWeight = FontWeight.Bold)

                        if (state.page in setOf(NativeAuthPage.LOGIN, NativeAuthPage.REGISTER)) {
                            AuthTabs(
                                loginSelected = state.page == NativeAuthPage.LOGIN,
                                enabled = !state.busy,
                                onShowLogin = onShowLogin,
                                onShowRegister = onShowRegister
                            )
                        }

                        state.message?.let {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(it, Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp)
                            }
                        }

                        when (state.page) {
                            NativeAuthPage.INITIALIZING -> LoadingContent()
                            NativeAuthPage.LOGIN -> LoginForm(
                                busy = state.busy,
                                onSignIn = onSignIn,
                                onOAuth = { provider ->
                                    onOAuth(provider, false, "", "", "", "", "", "")
                                }
                            )
                            NativeAuthPage.REGISTER -> RegisterForm(
                                busy = state.busy,
                                errors = state.fieldErrors,
                                onRegister = onRegister,
                                onOAuth = { provider, username, firstName, lastName, dni, phone, email ->
                                    onOAuth(
                                        provider,
                                        true,
                                        username,
                                        firstName,
                                        lastName,
                                        dni,
                                        phone,
                                        email
                                    )
                                }
                            )
                            NativeAuthPage.PENDING -> PendingContent(
                                name = state.profile?.fullName.orEmpty(),
                                onSignOut = onSignOut,
                                busy = state.busy
                            )
                            NativeAuthPage.MFA -> MfaContent(
                                secret = state.mfaSecret,
                                busy = state.busy,
                                onVerify = onVerifyMfa,
                                onSignOut = onSignOut
                            )
                            NativeAuthPage.APP -> Unit
                        }
                    }
                }
            }
        }

        if (state.busy && state.page != NativeAuthPage.INITIALIZING) {
            CircularProgressIndicator(color = AuthGreen)
        }
    }
}

@Composable
private fun AuthTabs(
    loginSelected: Boolean,
    enabled: Boolean,
    onShowLogin: () -> Unit,
    onShowRegister: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        if (loginSelected) {
            Button(onClick = onShowLogin, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text("Iniciar sesión")
            }
            TextButton(onClick = onShowRegister, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text("Crear cuenta")
            }
        } else {
            TextButton(onClick = onShowLogin, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text("Iniciar sesión")
            }
            Button(onClick = onShowRegister, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text("Crear cuenta")
            }
        }
    }
}

@Composable
private fun LoginForm(
    busy: Boolean,
    onSignIn: (String, String) -> Unit,
    onOAuth: (String) -> Unit
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Text("Accede con tu cuenta o registra una nueva.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = identifier,
        onValueChange = { identifier = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Usuario o correo electrónico") },
        singleLine = true,
        enabled = !busy
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Contraseña") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation()
    )
    Button(
        onClick = { onSignIn(identifier, password) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = AuthGreen, contentColor = Color.Black)
    ) {
        Text("Ingresar", fontWeight = FontWeight.Bold)
    }
    OAuthDivider()
    OAuthButtons(busy = busy, onOAuth = onOAuth)
}

@Composable
private fun RegisterForm(
    busy: Boolean,
    errors: Map<String, String>,
    onRegister: (String, String, String, String, String, String, String, String) -> Unit,
    onOAuth: (String, String, String, String, String, String, String) -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var dni by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }

    Text(
        "Todos los campos son obligatorios. Las cuentas nuevas requieren aprobación antes de controlar el equipo.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    AuthField("Nombre de usuario", username, { username = it }, busy, errors["username"], "Ejemplo: usuario123")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AuthField("Nombres", firstName, { firstName = it }, busy, errors["firstName"], modifier = Modifier.weight(1f))
        AuthField("Apellidos", lastName, { lastName = it }, busy, errors["lastName"], modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AuthField("DNI", dni, { dni = it.filter(Char::isDigit).take(8) }, busy, errors["dni"], modifier = Modifier.weight(1f))
        AuthField("Número de teléfono", phone, { phone = it }, busy, errors["phone"], "+51 999 999 999", Modifier.weight(1f))
    }
    AuthField("Correo electrónico", email, { email = it }, busy, errors["email"], "usuario@ejemplo.com")
    AuthPasswordField("Contraseña", password, { password = it }, busy, errors["password"])
    AuthPasswordField("Confirmar contraseña", confirmation, { confirmation = it }, busy, errors["passwordConfirmation"])
    Text("Usa al menos 12 caracteres.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(
        onClick = { onRegister(username, firstName, lastName, dni, phone, email, password, confirmation) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = AuthGreen, contentColor = Color.Black)
    ) {
        Text("Registrarme con correo", fontWeight = FontWeight.Bold)
    }
    OAuthDivider()
    OAuthButtons(
        busy = busy,
        onOAuth = { provider -> onOAuth(provider, username, firstName, lastName, dni, phone, email) }
    )
}

@Composable
private fun AuthField(
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
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
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
private fun AuthPasswordField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    busy: Boolean,
    error: String?
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
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
private fun OAuthDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text("  O CONTINÚA CON  ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun OAuthButtons(busy: Boolean, onOAuth: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { onOAuth(NativeAuthViewModel.PROVIDER_GOOGLE) },
            enabled = !busy,
            modifier = Modifier.weight(1f)
        ) {
            Text("G  Google", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = { onOAuth(NativeAuthViewModel.PROVIDER_GITHUB) },
            enabled = !busy,
            modifier = Modifier.weight(1f)
        ) {
            Text("GH  GitHub", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(color = AuthGreen)
        Text("Validando la sesión segura…")
    }
}

@Composable
private fun PendingContent(name: String, onSignOut: () -> Unit, busy: Boolean) {
    Text(if (name.isBlank()) "Tu cuenta" else name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Cuando un administrador apruebe la cuenta podrás acceder al microclima.")
    OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Cerrar sesión")
    }
}

@Composable
private fun MfaContent(
    secret: String?,
    busy: Boolean,
    onVerify: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
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
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
            Text(secret, Modifier.fillMaxWidth().padding(14.dp), fontWeight = FontWeight.Bold)
        }
    }
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit).take(6) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Código de 6 dígitos") },
        singleLine = true,
        enabled = !busy
    )
    Button(
        onClick = { onVerify(code) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy && code.length == 6
    ) {
        Text(if (secret == null) "Verificar código" else "Activar autenticador")
    }
    TextButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Cerrar sesión")
    }
}
