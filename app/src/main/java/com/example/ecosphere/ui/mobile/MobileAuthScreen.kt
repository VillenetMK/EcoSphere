package com.example.ecosphere.ui.mobile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecosphere.R
import com.example.ecosphere.auth.NativeAuthPage
import com.example.ecosphere.auth.NativeAuthUiState
import com.example.ecosphere.auth.NativeAuthViewModel
import com.example.ecosphere.shared.AuthValidation

private val MobileGreen = Color(0xFF66FF7A)
private val MobileBackground = Color(0xFF07100B)
private val MobileSurface = Color(0xFF101914)
private val MobileSurfaceRaised = Color(0xFF17231C)
private val MobileText = Color(0xFFF1F7F3)
private val MobileMuted = Color(0xFF9EACA4)
private val MobileBorder = Color(0xFF34423A)

@Composable
fun MobileAuthScreen(
    state: NativeAuthUiState,
    onShowLogin: () -> Unit,
    onShowRegister: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String, String, String, String, String, String) -> Unit,
    onOAuth: (String) -> Unit,
    onVerifyMfa: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MobileBackground)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                MobileBrandHeader(
                    showBack = state.page == NativeAuthPage.REGISTER,
                    onBack = onShowLogin
                )

                Spacer(Modifier.height(8.dp))

                when (state.page) {
                    NativeAuthPage.INITIALIZING -> LoadingContent()
                    NativeAuthPage.LOGIN -> LoginContent(
                        busy = state.busy,
                        message = state.message,
                        onSignIn = onSignIn,
                        onOAuth = onOAuth,
                        onShowRegister = onShowRegister
                    )
                    NativeAuthPage.REGISTER -> RegisterContent(
                        busy = state.busy,
                        message = state.message,
                        errors = state.fieldErrors,
                        verifiedEmail = state.verifiedEmail,
                        onRegister = onRegister,
                        onOAuth = onOAuth
                    )
                    NativeAuthPage.PENDING -> PendingContent(
                        name = state.profile?.fullName.orEmpty(),
                        message = state.message,
                        busy = state.busy,
                        onSignOut = onSignOut
                    )
                    NativeAuthPage.MFA -> MfaContent(
                        secret = state.mfaSecret,
                        message = state.message,
                        busy = state.busy,
                        onVerify = onVerifyMfa,
                        onSignOut = onSignOut
                    )
                    NativeAuthPage.APP -> Unit
                }
            }
        }

        if (state.busy && state.page != NativeAuthPage.INITIALIZING) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.42f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MobileGreen)
                }
            }
        }
    }
}

@Composable
private fun MobileBrandHeader(showBack: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = MobileGreen)
            ) {
                Text("‹  Volver", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Surface(
                modifier = Modifier.size(44.dp),
                color = MobileGreen,
                contentColor = Color.Black,
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("ES", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "EcoSphere",
                color = MobileText,
                fontSize = 21.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tu microclima, en tus manos",
                color = MobileMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun LoginContent(
    busy: Boolean,
    message: String?,
    onSignIn: (String, String) -> Unit,
    onOAuth: (String) -> Unit,
    onShowRegister: () -> Unit
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    PageHeading(
        eyebrow = "ACCESO SEGURO",
        title = "Bienvenido",
        subtitle = "Controla y supervisa EcoSphere desde tu teléfono."
    )
    MessageBanner(message)

    OutlinedTextField(
        value = identifier,
        onValueChange = { identifier = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Usuario o correo electrónico") },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        ),
        colors = mobileFieldColors()
    )
    MobilePasswordField(
        label = "Contraseña",
        value = password,
        onValue = { password = it },
        busy = busy,
        imeAction = ImeAction.Done,
        onDone = {
            focusManager.clearFocus()
            if (identifier.isNotBlank() && password.isNotBlank()) {
                onSignIn(identifier, password)
            }
        }
    )
    Button(
        onClick = {
            focusManager.clearFocus()
            onSignIn(identifier, password)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
        shape = RoundedCornerShape(17.dp),
        colors = primaryButtonColors()
    ) {
        Text("Iniciar sesión", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }

    OAuthDivider()
    OAuthButtons(busy = busy, onOAuth = onOAuth)
    Text(
        "Si es tu primera vez, Google o GitHub crearán tu cuenta automáticamente.",
        color = MobileMuted,
        fontSize = 12.sp
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MobileSurface,
        contentColor = MobileText,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("¿Primera vez en EcoSphere?", fontWeight = FontWeight.SemiBold)
                Text("Crea una cuenta y solicita acceso.", color = MobileMuted, fontSize = 12.sp)
            }
            TextButton(
                onClick = onShowRegister,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MobileGreen)
            ) {
                Text("Crear cuenta", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RegisterContent(
    busy: Boolean,
    message: String?,
    errors: Map<String, String>,
    verifiedEmail: String?,
    onRegister: (String, String, String, String, String, String, String, String) -> Unit,
    onOAuth: (String) -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var dni by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf(AuthValidation.DEFAULT_PHONE_INPUT) }
    var email by rememberSaveable(verifiedEmail) { mutableStateOf(verifiedEmail.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val completingProfile = !verifiedEmail.isNullOrBlank()

    PageHeading(
        eyebrow = if (completingProfile) "CORREO CONFIRMADO" else "NUEVA CUENTA",
        title = if (completingProfile) "Finaliza tu registro" else "Únete a EcoSphere",
        subtitle = if (completingProfile) {
            "Tu cuenta ya está verificada. Completa tus datos para terminar el registro por correo."
        } else {
            "Completa estos datos sólo si prefieres registrarte con correo y contraseña."
        }
    )
    MessageBanner(message)

    MobileField("Nombre de usuario", username, { username = it }, busy, errors["username"], "usuario123")
    MobileField("Nombres", firstName, { firstName = it }, busy, errors["firstName"])
    MobileField("Apellidos", lastName, { lastName = it }, busy, errors["lastName"])
    MobileField(
        label = "DNI",
        value = dni,
        onValue = { dni = it.filter(Char::isDigit).take(8) },
        busy = busy,
        error = errors["dni"],
        keyboardType = KeyboardType.Number
    )
    MobileField(
        label = "Número de teléfono",
        value = phone,
        onValue = { phone = AuthValidation.formatPhoneInput(it) },
        busy = busy,
        error = errors["phone"],
        placeholder = "+51 999 999 999",
        keyboardType = KeyboardType.Phone
    )
    MobileField(
        label = "Correo electrónico",
        value = email,
        onValue = { email = it },
        busy = busy,
        error = errors["email"],
        placeholder = "usuario@ejemplo.com",
        keyboardType = KeyboardType.Email,
        editable = !completingProfile
    )
    if (!completingProfile) {
        MobilePasswordField("Contraseña", password, { password = it }, busy, errors["password"])
        MobilePasswordField(
            "Confirmar contraseña",
            confirmation,
            { confirmation = it },
            busy,
            errors["passwordConfirmation"]
        )
        Text("Usa al menos 12 caracteres.", color = MobileMuted, fontSize = 12.sp)
    }

    Button(
        onClick = { onRegister(username, firstName, lastName, dni, phone, email, password, confirmation) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = !busy,
        shape = RoundedCornerShape(17.dp),
        colors = primaryButtonColors()
    ) {
        Text(
            if (completingProfile) "Completar registro" else "Crear mi cuenta",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }

    if (!completingProfile) {
        OAuthDivider()
        OAuthButtons(busy = busy, onOAuth = onOAuth)
    }
}

@Composable
private fun PageHeading(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = eyebrow,
            color = MobileGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.1.sp
        )
        Text(
            text = title,
            color = MobileText,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = subtitle,
            color = MobileMuted,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun MessageBanner(message: String?) {
    if (message.isNullOrBlank()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MobileSurfaceRaised,
        contentColor = MobileText,
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(message, modifier = Modifier.padding(14.dp), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun MobileField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    busy: Boolean,
    error: String?,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    editable: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
            singleLine = true,
            enabled = !busy && editable,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            colors = mobileFieldColors()
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MobilePasswordField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    busy: Boolean,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            enabled = !busy,
            isError = error != null,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            trailingIcon = {
                TextButton(
                    onClick = { visible = !visible },
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MobileGreen)
                ) {
                    Text(if (visible) "Ocultar" else "Ver", fontSize = 12.sp)
                }
            },
            colors = mobileFieldColors()
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OAuthDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MobileBorder)
        Text(
            text = "  O CONTINÚA CON  ",
            color = MobileMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        HorizontalDivider(Modifier.weight(1f), color = MobileBorder)
    }
}

@Composable
private fun OAuthButtons(busy: Boolean, onOAuth: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProviderButton(
            label = "Google",
            iconRes = R.drawable.ic_google,
            enabled = !busy,
            onClick = { onOAuth(NativeAuthViewModel.PROVIDER_GOOGLE) },
            modifier = Modifier.weight(1f)
        )
        ProviderButton(
            label = "GitHub",
            iconRes = R.drawable.ic_github,
            enabled = !busy,
            onClick = { onOAuth(NativeAuthViewModel.PROVIDER_GITHUB) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProviderButton(
    label: String,
    iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MobileText)
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            color = Color.White,
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
            }
        }
        Text(label, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        CircularProgressIndicator(color = MobileGreen)
        Text("Preparando tu experiencia EcoSphere…", color = MobileMuted)
    }
}

@Composable
private fun PendingContent(
    name: String,
    message: String?,
    busy: Boolean,
    onSignOut: () -> Unit
) {
    PageHeading(
        eyebrow = "SOLICITUD RECIBIDA",
        title = "Cuenta en revisión",
        subtitle = if (name.isBlank()) {
            "Un administrador debe aprobar el acceso antes de que puedas controlar EcoSphere."
        } else {
            "$name, un administrador debe aprobar tu acceso antes de controlar EcoSphere."
        }
    )
    MessageBanner(message)
    OutlinedButton(
        onClick = onSignOut,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MobileText)
    ) {
        Text("Cerrar sesión")
    }
}

@Composable
private fun MfaContent(
    secret: String?,
    message: String?,
    busy: Boolean,
    onVerify: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }

    PageHeading(
        eyebrow = "PROTECCIÓN ADMINISTRATIVA",
        title = if (secret == null) "Verifica tu acceso" else "Protege tu cuenta",
        subtitle = if (secret == null) {
            "Ingresa el código actual de seis dígitos de Google Authenticator."
        } else {
            "Agrega esta clave a Google Authenticator y confirma el primer código."
        }
    )
    MessageBanner(message)

    if (secret != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MobileSurfaceRaised,
            contentColor = MobileText,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = secret,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        colors = mobileFieldColors()
    )
    Button(
        onClick = { onVerify(code) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = !busy && code.length == 6,
        shape = RoundedCornerShape(17.dp),
        colors = primaryButtonColors()
    ) {
        Text(if (secret == null) "Verificar código" else "Activar autenticador")
    }
    TextButton(
        onClick = onSignOut,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = MobileMuted)
    ) {
        Text("Cerrar sesión")
    }
}

@Composable
private fun mobileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MobileText,
    unfocusedTextColor = MobileText,
    disabledTextColor = MobileMuted,
    focusedBorderColor = MobileGreen,
    unfocusedBorderColor = MobileBorder,
    disabledBorderColor = MobileBorder,
    focusedLabelColor = MobileGreen,
    unfocusedLabelColor = MobileMuted,
    disabledLabelColor = MobileMuted,
    cursorColor = MobileGreen,
    focusedPlaceholderColor = MobileMuted,
    unfocusedPlaceholderColor = MobileMuted
)

@Composable
private fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MobileGreen,
    contentColor = Color.Black,
    disabledContainerColor = Color(0xFF2B372F),
    disabledContentColor = MobileMuted
)
