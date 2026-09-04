package com.example.ecosphere.auth

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecosphere.data.network.NetworkModule
import com.example.ecosphere.data.network.SupabaseConfig
import com.example.ecosphere.shared.AuthValidation
import com.example.ecosphere.shared.EcoSphereUserProfile
import com.example.ecosphere.shared.RegistrationDraft
import com.google.gson.Gson
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.AuthenticatorAssuranceLevel
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.providers.invoke
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class NativeAuthPage {
    INITIALIZING,
    LOGIN,
    REGISTER,
    PENDING,
    MFA,
    APP
}

data class NativeAuthUiState(
    val page: NativeAuthPage = NativeAuthPage.INITIALIZING,
    val busy: Boolean = true,
    val message: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val profile: EcoSphereUserProfile? = null,
    val verifiedEmail: String? = null,
    val mfaFactorId: String? = null,
    val mfaSecret: String? = null
)

class NativeAuthViewModel(application: Application) : AndroidViewModel(application) {
    private val supabase = NativeSupabase.client
    private val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()

    var uiState by mutableStateOf(NativeAuthUiState())
        private set

    init {
        viewModelScope.launch {
            runBusy {
                readPendingRegistration()
                supabase.auth.awaitInitialization()
                resolveCurrentSession()
            }
        }
    }

    fun showLogin() {
        uiState = uiState.copy(
            page = NativeAuthPage.LOGIN,
            message = null,
            fieldErrors = emptyMap()
        )
    }

    fun showRegister() {
        uiState = uiState.copy(
            page = NativeAuthPage.REGISTER,
            message = null,
            fieldErrors = emptyMap()
        )
    }

    fun signIn(identifier: String, password: String) {
        viewModelScope.launch {
            runBusy {
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
                    val response = NetworkModule.api.signInWithUsername(
                        apiKey = SupabaseConfig.API_KEY,
                        authorization = "Bearer ${SupabaseConfig.API_KEY}",
                        body = mapOf("username" to normalized, "password" to password)
                    )
                    supabase.auth.importAuthToken(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        retrieveUser = true
                    )
                }
                resolveCurrentSession()
            }
        }
    }

    fun registerWithEmail(
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
        val activeSession = supabase.auth.currentSessionOrNull()
        val passwordErrors = if (activeSession == null) {
            AuthValidation.validatePassword(password, confirmation)
        } else {
            emptyMap()
        }
        val errors = identity.errors + passwordErrors
        if (errors.isNotEmpty()) {
            uiState = uiState.copy(fieldErrors = errors, message = "Revisa los datos del formulario.")
            return
        }

        viewModelScope.launch {
            runBusy {
                val session = supabase.auth.currentSessionOrNull()
                if (session != null) {
                    val verifiedEmail = session.user?.email?.lowercase().orEmpty()
                    require(verifiedEmail == identity.draft.email) {
                        "El correo debe coincidir con la cuenta confirmada."
                    }
                    savePendingRegistration(identity.draft)
                    saveIntent(INTENT_REGISTER)
                    completeProfile(identity.draft)
                    clearPendingRegistration()
                    resolveCurrentSession()
                } else {
                    savePendingRegistration(identity.draft)
                    saveIntent(INTENT_REGISTER)
                    supabase.auth.signUpWith(
                        Email,
                        redirectUrl = NativeSupabase.ANDROID_OAUTH_RETURN_URL
                    ) {
                        this.email = identity.draft.email
                        this.password = password
                    }
                    if (supabase.auth.currentSessionOrNull() != null) {
                        resolveCurrentSession()
                    } else {
                        uiState = uiState.copy(
                            page = NativeAuthPage.LOGIN,
                            message = "Revisa tu correo y confirma la cuenta. Volverás a EcoSphere para finalizar el registro."
                        )
                    }
                }
            }
        }
    }

    fun startOAuth(provider: String) {
        clearPendingRegistration()
        saveIntent(INTENT_OAUTH)

        viewModelScope.launch {
            runBusy(resetBusyWhenComplete = true) {
                if (supabase.auth.currentSessionOrNull() != null) {
                    supabase.auth.signOut()
                }
                val oauthProvider = when (provider) {
                    PROVIDER_GOOGLE -> OAuthProvider(PROVIDER_GOOGLE)
                    PROVIDER_GITHUB -> Github
                    else -> error("Proveedor de acceso no permitido.")
                }
                supabase.auth.signInWith(
                    oauthProvider,
                    redirectUrl = NativeSupabase.ANDROID_OAUTH_RETURN_URL
                ) {
                    if (provider == PROVIDER_GOOGLE) {
                        queryParams["prompt"] = "select_account"
                    }
                }
            }
        }
    }

    fun onOAuthCallback() {
        viewModelScope.launch {
            runBusy { resolveCurrentSession() }
        }
    }

    fun onOAuthError(error: Throwable) {
        uiState = NativeAuthUiState(
            page = NativeAuthPage.LOGIN,
            busy = false,
            message = AuthValidation.safeAuthErrorMessage(
                error.message,
                "No se pudo completar el acceso externo. Inténtalo nuevamente."
            )
        )
    }

    fun verifyMfa(code: String) {
        val factorId = uiState.mfaFactorId
        if (factorId == null) {
            uiState = uiState.copy(message = "La verificación expiró. Inicia sesión nuevamente.")
            return
        }
        if (!Regex("^[0-9]{6}$").matches(code)) {
            uiState = uiState.copy(message = "Ingresa exactamente los seis dígitos del autenticador.")
            return
        }
        viewModelScope.launch {
            runBusy {
                supabase.auth.mfa.createChallengeAndVerify(factorId, code)
                val level = supabase.auth.mfa.getAuthenticatorAssuranceLevel()
                check(level.current == AuthenticatorAssuranceLevel.AAL2) {
                    "No se pudo completar la verificación en dos pasos."
                }
                uiState = uiState.copy(
                    page = NativeAuthPage.APP,
                    mfaFactorId = null,
                    mfaSecret = null,
                    message = null
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runBusy {
                supabase.auth.signOut()
                clearPendingRegistration()
                uiState = NativeAuthUiState(
                    page = NativeAuthPage.LOGIN,
                    busy = false
                )
            }
        }
    }

    suspend fun resolveCurrentSession() {
        val session = supabase.auth.currentSessionOrNull()
        if (session == null) {
            uiState = NativeAuthUiState(page = NativeAuthPage.LOGIN, busy = false)
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
            uiState = NativeAuthUiState(
                page = NativeAuthPage.LOGIN,
                busy = false,
                message = "No se pudo preparar tu cuenta de Google o GitHub. Inténtalo nuevamente."
            )
            return
        }
        if (profile == null) {
            val verifiedEmail = session.user?.email?.lowercase().orEmpty()
            uiState = NativeAuthUiState(
                page = NativeAuthPage.REGISTER,
                busy = false,
                verifiedEmail = verifiedEmail,
                message = "Tu correo ya está confirmado. Completa tus datos para finalizar el registro."
            )
            return
        }

        clearIntent()
        if (profile.status != "approved") {
            uiState = NativeAuthUiState(
                page = NativeAuthPage.PENDING,
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

        uiState = NativeAuthUiState(
            page = NativeAuthPage.APP,
            busy = false,
            profile = profile
        )
    }

    private suspend fun requireAdminMfa(profile: EcoSphereUserProfile) {
        val level = supabase.auth.mfa.getAuthenticatorAssuranceLevel()
        if (level.current == AuthenticatorAssuranceLevel.AAL2) {
            uiState = NativeAuthUiState(
                page = NativeAuthPage.APP,
                busy = false,
                profile = profile
            )
            return
        }

        val factors = supabase.auth.mfa.retrieveFactorsForCurrentUser()
        val verified = factors.firstOrNull { it.factorType == "totp" && it.isVerified }
        if (verified != null) {
            uiState = NativeAuthUiState(
                page = NativeAuthPage.MFA,
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
        uiState = NativeAuthUiState(
            page = NativeAuthPage.MFA,
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

    private suspend fun runBusy(
        resetBusyWhenComplete: Boolean = true,
        block: suspend () -> Unit
    ) {
        uiState = uiState.copy(busy = true, message = null, fieldErrors = emptyMap())
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            uiState = uiState.copy(
                busy = false,
                message = friendlyMessage(error)
            )
        } finally {
            if (resetBusyWhenComplete && uiState.busy) {
                uiState = uiState.copy(busy = false)
            }
        }
    }

    private fun friendlyMessage(error: Throwable): String =
        AuthValidation.safeAuthErrorMessage(error.message)

    private fun savePendingRegistration(draft: RegistrationDraft) {
        preferences.edit().putString(KEY_DRAFT, gson.toJson(draft)).apply()
    }

    private fun readPendingRegistration(): RegistrationDraft? {
        val draft = try {
            preferences.getString(KEY_DRAFT, null)?.let {
                gson.fromJson(it, RegistrationDraft::class.java)
            }
        } catch (_: Exception) {
            clearPendingRegistration()
            return null
        }
        if (draft != null && !AuthValidation.isRegistrationDraftCurrent(draft)) {
            clearPendingRegistration()
            return null
        }
        return draft
    }

    private fun clearPendingRegistration() {
        preferences.edit().remove(KEY_DRAFT).remove(KEY_INTENT).apply()
    }

    private fun saveIntent(intent: String) {
        preferences.edit().putString(KEY_INTENT, intent).apply()
    }

    private fun readIntent(): String? = preferences.getString(KEY_INTENT, null)

    private fun clearIntent() {
        preferences.edit().remove(KEY_INTENT).apply()
    }

    companion object {
        const val PROVIDER_EMAIL = "email"
        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_GITHUB = "github"

        private const val PREFERENCES = "ecosphere-auth"
        private const val KEY_DRAFT = "pending-registration"
        private const val KEY_INTENT = "oauth-intent"
        private const val INTENT_LOGIN = "password-login"
        private const val INTENT_REGISTER = "register"
        private const val INTENT_OAUTH = "oauth"

        fun factory(application: Application): ViewModelProvider.Factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
}
