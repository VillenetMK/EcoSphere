package com.example.ecosphere.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ecosphere.shared.AuthValidation
import com.example.ecosphere.shared.EcoSphereConfig
import com.example.ecosphere.shared.EcoSphereUserProfile
import com.example.ecosphere.shared.RegistrationDraft
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.AuthenticatorAssuranceLevel
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.providers.invoke
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.prefs.Preferences

enum class DesktopAuthPage {
    INITIALIZING,
    LOGIN,
    REGISTER,
    PENDING,
    MFA,
    APP
}

data class DesktopAuthState(
    val page: DesktopAuthPage = DesktopAuthPage.INITIALIZING,
    val busy: Boolean = true,
    val message: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val profile: EcoSphereUserProfile? = null,
    val mfaFactorId: String? = null,
    val mfaSecret: String? = null
)

private data class UsernameLoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)

class DesktopAuthController {
    private val supabase = DesktopSupabase.client
    private val preferences = Preferences.userRoot().node("com/example/ecosphere/auth")
    private val gson = Gson()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build()

    var state by mutableStateOf(DesktopAuthState())
        private set

    suspend fun initialize() = runBusy {
        supabase.auth.awaitInitialization()
        resolveCurrentSession()
    }

    fun showLogin() {
        state = state.copy(page = DesktopAuthPage.LOGIN, message = null, fieldErrors = emptyMap())
    }

    fun showRegister() {
        state = state.copy(page = DesktopAuthPage.REGISTER, message = null, fieldErrors = emptyMap())
    }

    suspend fun signIn(identifier: String, password: String) = runBusy {
        clearPendingRegistration()
        saveIntent(INTENT_LOGIN)
        if (identifier.trim().contains('@')) {
            supabase.auth.signInWith(Email) {
                email = identifier.trim().lowercase()
                this.password = password
            }
        } else {
            val normalized = identifier.trim()
            require(Regex("^[A-Za-z][A-Za-z0-9._-]{2,31}$").matches(normalized)) {
                "Usuario o contraseña incorrectos."
            }
            val tokens = signInWithUsername(normalized, password)
            supabase.auth.importAuthToken(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                retrieveUser = true
            )
        }
        resolveCurrentSession()
    }

    suspend fun registerWithEmail(
        username: String,
        firstName: String,
        lastName: String,
        dni: String,
        phone: String,
        email: String,
        password: String,
        confirmation: String
    ) {
        val identity = AuthValidation.validateRegistration(
            username, firstName, lastName, dni, phone, email, PROVIDER_EMAIL
        )
        val errors = identity.errors + AuthValidation.validatePassword(password, confirmation)
        if (errors.isNotEmpty()) {
            state = state.copy(fieldErrors = errors, message = "Revisa los datos del formulario.")
            return
        }
        runBusy {
            savePendingRegistration(identity.draft)
            saveIntent(INTENT_REGISTER)
            supabase.auth.signUpWith(Email) {
                this.email = identity.draft.email
                this.password = password
            }
            if (supabase.auth.currentSessionOrNull() != null) {
                resolveCurrentSession()
            } else {
                state = state.copy(
                    page = DesktopAuthPage.LOGIN,
                    message = "Revisa tu correo y confirma la cuenta antes de iniciar sesión."
                )
            }
        }
    }

    suspend fun startOAuth(provider: String) {
        clearPendingRegistration()
        saveIntent(INTENT_OAUTH)

        runBusy {
            if (supabase.auth.currentSessionOrNull() != null) supabase.auth.signOut()
            val oauthProvider = when (provider) {
                PROVIDER_GOOGLE -> OAuthProvider(PROVIDER_GOOGLE)
                PROVIDER_GITHUB -> Github
                else -> error("Proveedor de acceso no permitido.")
            }
            supabase.auth.signInWith(oauthProvider) {
                if (provider == PROVIDER_GOOGLE) {
                    queryParams["prompt"] = "select_account"
                }
            }
            resolveCurrentSession()
        }
    }

    suspend fun verifyMfa(code: String) {
        val factorId = state.mfaFactorId
        if (factorId == null) {
            state = state.copy(message = "La verificación expiró. Inicia sesión nuevamente.")
            return
        }
        if (!Regex("^[0-9]{6}$").matches(code)) {
            state = state.copy(message = "Ingresa exactamente los seis dígitos del autenticador.")
            return
        }
        runBusy {
            supabase.auth.mfa.createChallengeAndVerify(factorId, code)
            val level = supabase.auth.mfa.getAuthenticatorAssuranceLevel()
            check(level.current == AuthenticatorAssuranceLevel.AAL2) {
                "No se pudo completar la verificación en dos pasos."
            }
            state = state.copy(
                page = DesktopAuthPage.APP,
                mfaFactorId = null,
                mfaSecret = null,
                message = null
            )
        }
    }

    suspend fun signOut() = runBusy {
        supabase.auth.signOut()
        clearPendingRegistration()
        state = DesktopAuthState(page = DesktopAuthPage.LOGIN, busy = false)
    }

    fun accessToken(): String? = supabase.auth.currentAccessTokenOrNull()

    private suspend fun resolveCurrentSession() {
        val session = supabase.auth.currentSessionOrNull()
        if (session == null) {
            state = DesktopAuthState(page = DesktopAuthPage.LOGIN, busy = false)
            return
        }

        val intent = readIntent()
        val draft = readPendingRegistration()
        if (draft != null) {
            val verifiedEmail = session.user?.email?.lowercase().orEmpty()
            if (verifiedEmail != draft.email) {
                supabase.auth.signOut()
                clearPendingRegistration()
                error("El correo verificado no coincide con el correo ingresado en el registro.")
            }
            completeProfile(draft)
            clearPendingRegistration()
        }

        val profile = loadProfile()
        if (profile == null && intent == INTENT_OAUTH) {
            supabase.auth.signOut()
            clearPendingRegistration()
            state = DesktopAuthState(
                page = DesktopAuthPage.LOGIN,
                busy = false,
                message = "No se pudo preparar tu cuenta de Google o GitHub. Inténtalo nuevamente."
            )
            return
        }
        if (profile == null) {
            state = DesktopAuthState(
                page = DesktopAuthPage.REGISTER,
                busy = false,
                message = "Completa tus datos obligatorios para finalizar el registro."
            )
            return
        }

        clearIntent()
        if (profile.status != "approved") {
            state = DesktopAuthState(
                page = DesktopAuthPage.PENDING,
                busy = false,
                profile = profile,
                message = if (profile.status == "blocked") {
                    "La cuenta está bloqueada. Comunícate con el administrador."
                } else {
                    "El registro está completo y espera aprobación del administrador."
                }
            )
            return
        }

        if (profile.role == "admin") {
            requireAdminMfa(profile)
            return
        }
        state = DesktopAuthState(page = DesktopAuthPage.APP, busy = false, profile = profile)
    }

    private suspend fun requireAdminMfa(profile: EcoSphereUserProfile) {
        val level = supabase.auth.mfa.getAuthenticatorAssuranceLevel()
        if (level.current == AuthenticatorAssuranceLevel.AAL2) {
            state = DesktopAuthState(page = DesktopAuthPage.APP, busy = false, profile = profile)
            return
        }
        val factors = supabase.auth.mfa.retrieveFactorsForCurrentUser()
        val verified = factors.firstOrNull { it.factorType == "totp" && it.isVerified }
        if (verified != null) {
            state = DesktopAuthState(
                page = DesktopAuthPage.MFA,
                busy = false,
                profile = profile,
                mfaFactorId = verified.id
            )
            return
        }

        factors.filter { it.factorType == "totp" && !it.isVerified }
            .forEach { supabase.auth.mfa.unenroll(it.id) }
        val enrollment = supabase.auth.mfa.enroll(
            FactorType.TOTP,
            friendlyName = "EcoSphere ${profile.username}"
        ) { issuer = "EcoSphere" }
        state = DesktopAuthState(
            page = DesktopAuthPage.MFA,
            busy = false,
            profile = profile,
            mfaFactorId = enrollment.id,
            mfaSecret = enrollment.data.secret,
            message = "Agrega esta clave en Google Authenticator y escribe el primer código."
        )
    }

    private suspend fun loadProfile(): EcoSphereUserProfile? =
        supabase.postgrest.rpc("my_profile").decodeSingleOrNull()

    private suspend fun completeProfile(draft: RegistrationDraft) {
        supabase.postgrest.rpc(
            "complete_user_profile",
            buildJsonObject {
                put("p_username", draft.username)
                put("p_first_name", draft.firstName)
                put("p_last_name", draft.lastName)
                put("p_dni", draft.dni)
                put("p_phone", draft.phone)
                put("p_expected_email", draft.email)
            }
        )
    }

    private suspend fun signInWithUsername(username: String, password: String): UsernameLoginResponse =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(mapOf("username" to username, "password" to password))
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${EcoSphereConfig.SUPABASE_URL}/functions/v1/username-login"))
                .timeout(Duration.ofSeconds(15))
                .header("apikey", EcoSphereConfig.SUPABASE_PUBLISHABLE_KEY)
                .header("Authorization", "Bearer ${EcoSphereConfig.SUPABASE_PUBLISHABLE_KEY}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            require(response.statusCode() in 200..299) {
                runCatching {
                    gson.fromJson(response.body(), Map::class.java)["error"]?.toString()
                }.getOrNull() ?: "No se pudo iniciar sesión."
            }
            gson.fromJson(response.body(), UsernameLoginResponse::class.java)
        }

    private suspend fun runBusy(block: suspend () -> Unit) {
        state = state.copy(busy = true, message = null, fieldErrors = emptyMap())
        try {
            block()
        } catch (error: Throwable) {
            state = state.copy(busy = false, message = friendlyMessage(error))
        } finally {
            if (state.busy) state = state.copy(busy = false)
        }
    }

    private fun friendlyMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Invalid login", ignoreCase = true) -> "Usuario o contraseña incorrectos."
            message.contains("Email not confirmed", ignoreCase = true) -> "Confirma tu correo antes de iniciar sesión."
            message.contains("reserved", ignoreCase = true) -> "Ese nombre de usuario está reservado."
            message.contains("duplicate", ignoreCase = true) -> "El usuario, DNI, teléfono o correo ya está registrado."
            message.isNotBlank() -> message
            else -> "No se pudo completar la operación."
        }
    }

    private fun savePendingRegistration(draft: RegistrationDraft) {
        preferences.put(KEY_DRAFT, gson.toJson(draft))
        preferences.flush()
    }

    private fun readPendingRegistration(): RegistrationDraft? = runCatching {
        preferences.get(KEY_DRAFT, null)?.let { gson.fromJson(it, RegistrationDraft::class.java) }
    }.getOrNull()

    private fun clearPendingRegistration() {
        preferences.remove(KEY_DRAFT)
        preferences.remove(KEY_INTENT)
        preferences.flush()
    }

    private fun saveIntent(intent: String) {
        preferences.put(KEY_INTENT, intent)
        preferences.flush()
    }

    private fun readIntent(): String? = preferences.get(KEY_INTENT, null)

    private fun clearIntent() {
        preferences.remove(KEY_INTENT)
        preferences.flush()
    }

    companion object {
        const val PROVIDER_EMAIL = "email"
        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_GITHUB = "github"

        private const val KEY_DRAFT = "pending-registration"
        private const val KEY_INTENT = "oauth-intent"
        private const val INTENT_LOGIN = "password-login"
        private const val INTENT_REGISTER = "register"
        private const val INTENT_OAUTH = "oauth"
    }
}
