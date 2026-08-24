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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecosphere.auth.NativeAuthPage
import com.example.ecosphere.auth.NativeAuthUiState
import com.example.ecosphere.auth.NativeAuthViewModel

private val AuthGreen = Color(0xFF5CFF72)
private val AuthBackground = Color(0xFF07100B)
private val AuthSurface = Color(0xFF101914)
private val AuthText = Color(0xFFF1F7F3)
private val AuthMuted = Color(0xFF9EACA4)
private val AuthBorder = Color(0xFF34423A)

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
    val title = when (state.page) {
        NativeAuthPage.LOGIN -> "Bienvenido"
        NativeAuthPage.REGISTER -> "Crear cuenta"
        NativeAuthPage.PENDING -> "Cuenta en revisión"
        NativeAuthPage.MFA -> if (state.mfaSecret == null) "Verifica tu acceso" else "Protege tu cuenta"
        NativeAuthPage.INITIALIZING -> "Preparando EcoSphere"
        NativeAuthPage.APP -> "EcoSphere"
    }
    val subtitle = when (state.page) {
        NativeAuthPage.LOGIN -> "Ingresa para administrar tu microclima."
        NativeAuthPage.REGISTER -> "Completa tus datos para solicitar acceso."
        NativeAuthPage.PENDING -> "Tu registro fue recibido correctamente."
        NativeAuthPage.MFA -> "Autenticación reforzada para administradores."
        NativeAuthPage.INITIALIZING -> "Validando tu sesión segura."
        NativeAuthPage.APP -> "Sistema inteligente de microclima"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBackground)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                AuthBrand()

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AuthSurface,
                    contentColor = AuthText,
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "ECOSPHERE CONTROL",
                            color = AuthGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = title,
                            color = AuthText,
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            color = AuthMuted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )

                        state.message?.let {
                            Surface(
                                color = Color(0xFF18231D),
                                contentColor = AuthText,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = it,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        when (state.page) {
                            NativeAuthPage.INITIALIZING -> LoadingContent()
                            NativeAuthPage.LOGIN -> LoginForm(
                                busy = state.busy,
                                onSignIn = onSignIn,
                                onOAuth = { provider ->
                                    onOAuth(provider, false, "", "", "", "", "", "")
                                },
                                onShowRegister = onShowRegister
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
                                },
                                onShowLogin = onShowLogin
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
private fun AuthBrand() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            color = AuthGreen,
            contentColor = Color.Black,
            shape = RoundedCornerShape(15.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("ES", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "EcoSphere",
                color = AuthText,
                fontSize = 24.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sistema inteligente de microclima",
                color = AuthMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun LoginForm(
    busy: Boolean,
    onSignIn: (String, String) -> Unit,
    onOAuth: (String) -> Unit,
    onShowRegister: () -> Unit
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    OutlinedTextField(
        value = identifier,
        onValueChange = { identifier = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Usuario o correo electrónico") },
        singleLine = true,
        enabled = !busy,
        colors = authFieldColors()
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Contraseña") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        colors = authFieldColors()
    )
    Button(
        onClick = { onSignIn(identifier, password) },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
        colors = primaryButtonColors()
    ) {
        Text("Ingresar", fontWeight = FontWeight.Bold)
    }
    OAuthDivider()
    OAuthButtons(busy = busy, onOAuth = onOAuth)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("¿No tienes cuenta?", color = AuthMuted, fontSize = 13.sp)
        TextButton(
            onClick = onShowRegister,
            enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = AuthGreen)
        ) {
            Text("Crear cuenta", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RegisterForm(
    busy: Boolean,
    errors: Map<String, String>,
    onRegister: (String, String, String, String, String, String, String, String) -> Unit,
    onOAuth: (String, String, String, String, String, String, String) -> Unit,
    onShowLogin: () -> Unit
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
        text = "Todos los campos son obligatorios. Las cuentas nuevas requieren aprobación antes de controlar el equipo.",
        color = AuthMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp
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
    Text("Usa al menos 12 caracteres.", fontSize = 11.sp, color = AuthMuted)
    Button(
        onClick = { onRegister(username, firstName, lastName, dni, phone, email, password, confirmation) },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = !busy,
        colors = primaryButtonColors()
    ) {
        Text("Registrarme con correo", fontWeight = FontWeight.Bold)
    }
    OAuthDivider()
    OAuthButtons(
        busy = busy,
        onOAuth = { provider -> onOAuth(provider, username, firstName, lastName, dni, phone, email) }
    )
    TextButton(
        onClick = onShowLogin,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = AuthGreen)
    ) {
        Text("Ya tengo una cuenta · Iniciar sesión", fontWeight = FontWeight.Bold)
    }
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
            isError = error != null,
            colors = authFieldColors()
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
        }
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
            visualTransformation = PasswordVisualTransformation(),
            colors = authFieldColors()
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
        }
    }
}

@Composable
private fun OAuthDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = AuthBorder)
        Text(
            text = "  O CONTINÚA CON  ",
            color = AuthMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        HorizontalDivider(Modifier.weight(1f), color = AuthBorder)
    }
}

@Composable
private fun OAuthButtons(busy: Boolean, onOAuth: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { onOAuth(NativeAuthViewModel.PROVIDER_GOOGLE) },
            enabled = !busy,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthText)
        ) {
            Text("G  Google", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = { onOAuth(NativeAuthViewModel.PROVIDER_GITHUB) },
            enabled = !busy,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthText)
        ) {
            Text("GH  GitHub", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(color = AuthGreen)
        Text("Validando la sesión segura…", color = AuthMuted)
    }
}

@Composable
private fun PendingContent(name: String, onSignOut: () -> Unit, busy: Boolean) {
    if (name.isNotBlank()) {
        Text(name, color = AuthText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Text(
        text = "Cuando un administrador apruebe la cuenta podrás acceder al microclima.",
        color = AuthMuted,
        lineHeight = 20.sp
    )
    OutlinedButton(
        onClick = onSignOut,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthText)
    ) {
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
        text = if (secret == null) {
            "Abre Google Authenticator e ingresa el código actual de seis dígitos."
        } else {
            "En Google Authenticator elige «Ingresar clave de configuración» y usa esta clave:"
        },
        color = AuthMuted,
        lineHeight = 20.sp
    )
    if (secret != null) {
        Surface(
            color = Color(0xFF18231D),
            contentColor = AuthText,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = secret,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit).take(6) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Código de 6 dígitos") },
        singleLine = true,
        enabled = !busy,
        colors = authFieldColors()
    )
    Button(
        onClick = { onVerify(code) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy && code.length == 6,
        colors = primaryButtonColors()
    ) {
        Text(if (secret == null) "Verificar código" else "Activar autenticador")
    }
    TextButton(
        onClick = onSignOut,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = AuthMuted)
    ) {
        Text("Cerrar sesión")
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AuthText,
    unfocusedTextColor = AuthText,
    disabledTextColor = AuthMuted,
    focusedBorderColor = AuthGreen,
    unfocusedBorderColor = AuthBorder,
    disabledBorderColor = AuthBorder,
    focusedLabelColor = AuthGreen,
    unfocusedLabelColor = AuthMuted,
    disabledLabelColor = AuthMuted,
    cursorColor = AuthGreen,
    focusedPlaceholderColor = AuthMuted,
    unfocusedPlaceholderColor = AuthMuted
)

@Composable
private fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = AuthGreen,
    contentColor = Color.Black,
    disabledContainerColor = Color(0xFF2B372F),
    disabledContentColor = AuthMuted
)
