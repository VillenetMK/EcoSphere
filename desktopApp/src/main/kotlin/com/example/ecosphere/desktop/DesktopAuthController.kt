package com.example.ecosphere.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ecosphere.shared.AuthValidation
import com.example.ecosphere.shared.EcoSphereUserProfile
import com.example.ecosphere.shared.RegistrationDraft
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import java.net.BindException
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
    val verifiedEmail: String? = null,
    val mfaFactorId: String? = null,
    val mfaSecret: String? = null,
    val oauthProvider: String? = null
)

class DesktopAuthController internal constructor(
    private val backend: DesktopAuthBackend = SupabaseDesktopAuthBackend(),
    private val preferences: Preferences = Preferences.userRoot().node("com/example/ecosphere/auth"),
    private val recordFailure: (String, Throwable) -> Unit = DesktopDiagnostics::record
) {
    private val gson = Gson()
    private var operationInProgress = false
    private var oauthJob: Job? = null

    var state by mutableStateOf(DesktopAuthState())
        private set

    suspend fun initialize() = runBusy("initialize") {
        readPendingRegistration()
        backend.initialize()
        resolveCurrentSession()
    }

    fun showLogin() {
        if (operationInProgress) return
        state = state.copy(page = DesktopAuthPage.LOGIN, message = null, fieldErrors = emptyMap())
    }

    fun showRegister() {
        if (operationInProgress) return
        state = state.copy(page = DesktopAuthPage.REGISTER, message = null, fieldErrors = emptyMap())
    }

    suspend fun signIn(identifier: String, password: String) = runBusy("sign-in") {
        saveIntent(INTENT_LOGIN)
        backend.signIn(identifier, password)
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
        if (operationInProgress) return
        val identity = AuthValidation.validateRegistration(
            username, firstName, lastName, dni, phone, email, PROVIDER_EMAIL
        )
        val activeSession = backend.accessToken()
        val passwordErrors = if (activeSession == null) {
            AuthValidation.validatePassword(password, confirmation)
        } else {
            emptyMap()
        }
        val errors = identity.errors + passwordErrors
        if (errors.isNotEmpty()) {
            state = state.copy(fieldErrors = errors, message = "Revisa los datos del formulario.")
            return
        }
        runBusy("register") {
            val session = backend.accessToken()
            if (session != null) {
                val verifiedEmail = backend.sessionEmail().orEmpty()
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
                backend.signUp(identity.draft.email, password)
                if (backend.accessToken() != null) {
                    resolveCurrentSession()
                } else {
                    state = state.copy(
                        page = DesktopAuthPage.LOGIN,
                        message = "Revisa tu correo y confirma la cuenta antes de iniciar sesión."
                    )
                }
            }
        }
    }

    suspend fun startOAuth(provider: String) = runBusy("oauth") {
        require(provider in setOf(PROVIDER_GOOGLE, PROVIDER_GITHUB)) { "Proveedor de acceso no permitido." }
        clearPendingRegistration()
        saveIntent(INTENT_OAUTH)
        state = state.copy(oauthProvider = provider)
        oauthJob = currentCoroutineContext().job
        if (backend.accessToken() != null) backend.signOut()
        backend.oauth(provider)
        if (backend.accessToken() == null) {
            clearIntent()
            state = DesktopAuthState(page = DesktopAuthPage.LOGIN, busy = false, message = OAUTH_NOT_COMPLETED)
        } else {
            resolveCurrentSession()
        }
    }

    fun cancelOAuth() {
        oauthJob?.cancel()
    }

    suspend fun verifyMfa(code: String) {
        if (operationInProgress) return
        val factorId = state.mfaFactorId
        if (factorId == null) {
            state = state.copy(message = "La verificación expiró. Inicia sesión nuevamente.")
            return
        }
        if (!Regex("^[0-9]{6}$").matches(code)) {
            state = state.copy(message = "Ingresa exactamente los seis dígitos del autenticador.")
            return
        }
        runBusy("mfa") {
            backend.verifyMfa(factorId, code)
            state = state.copy(
                page = DesktopAuthPage.APP,
                mfaFactorId = null,
                mfaSecret = null,
                message = null
            )
        }
    }

    suspend fun signOut() = runBusy("sign-out") {
        backend.signOut()
        clearPendingRegistration()
        state = DesktopAuthState(page = DesktopAuthPage.LOGIN, busy = false)
    }

    fun accessToken(): String? = backend.accessToken()

    private suspend fun resolveCurrentSession() {
        val session = backend.accessToken()
        if (session == null) {
            state = DesktopAuthState(page = DesktopAuthPage.LOGIN, busy = false)
            return
        }

        val intent = readIntent()
        val draft = readPendingRegistration()
        if (draft != null) {
            val verifiedEmail = backend.sessionEmail().orEmpty()
            if (verifiedEmail != draft.email) {
                backend.signOut()
                clearPendingRegistration()
                error("El correo verificado no coincide con el correo ingresado en el registro.")
            }
            completeProfile(draft)
            clearPendingRegistration()
        }

        val profile = loadProfile()
        if (profile == null && intent == INTENT_OAUTH) {
            backend.signOut()
            clearPendingRegistration()
            state = DesktopAuthState(
                page = DesktopAuthPage.LOGIN,
                busy = false,
                message = "No se pudo preparar tu cuenta de Google o GitHub. Inténtalo nuevamente."
            )
            return
        }
        if (profile == null) {
            val verifiedEmail = backend.sessionEmail().orEmpty()
            state = DesktopAuthState(
                page = DesktopAuthPage.REGISTER,
                busy = false,
                verifiedEmail = verifiedEmail,
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
        val challenge = backend.adminChallenge(profile)
        state = if (challenge == null) {
            DesktopAuthState(page = DesktopAuthPage.APP, busy = false, profile = profile)
        } else {
            DesktopAuthState(
                page = DesktopAuthPage.MFA, busy = false, profile = profile,
                mfaFactorId = challenge.factorId, mfaSecret = challenge.secret
            )
        }
    }

    private suspend fun loadProfile() = backend.profile()
    private suspend fun completeProfile(draft: RegistrationDraft) = backend.completeProfile(draft)

    private suspend fun runBusy(operation: String, block: suspend () -> Unit) {
        if (operationInProgress) return
        operationInProgress = true
        state = state.copy(busy = true, message = null, fieldErrors = emptyMap())
        try {
            block()
        } catch (error: CancellationException) {
            if (operation == "oauth") {
                state = state.copy(message = "Acceso cancelado. Puedes intentarlo de nuevo.")
            }
            throw error
        } catch (error: Exception) {
            // Log only operation/type/stack locations, never the exception message or request data.
            runCatching { recordFailure(operation, error) }
            state = state.copy(
                page = if (state.page == DesktopAuthPage.INITIALIZING) DesktopAuthPage.LOGIN else state.page,
                message = friendlyMessage(error, operation)
            )
        } finally {
            state = state.copy(busy = false, oauthProvider = null)
            oauthJob = null
            operationInProgress = false
        }
    }

    private fun friendlyMessage(error: Throwable, operation: String): String {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        if (operation == "oauth") {
            if (causes.any { it is BindException }) {
                return "Otra aplicación está usando el acceso local de EcoSphere. Cierra otras ventanas de EcoSphere e inténtalo de nuevo."
            }
            if (causes.any { it.message.orEmpty().contains("timeout", ignoreCase = true) ||
                    it.message.orEmpty().contains("timed out", ignoreCase = true) }) return OAUTH_NOT_COMPLETED
        }
        return AuthValidation.safeAuthErrorMessage(error.message)
    }

    private fun savePendingRegistration(draft: RegistrationDraft) {
        preferences.put(KEY_DRAFT, gson.toJson(draft))
        preferences.flush()
    }

    private fun readPendingRegistration(): RegistrationDraft? {
        val draft = try {
            preferences.get(KEY_DRAFT, null)?.let {
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
        const val OAUTH_NOT_COMPLETED = "No se completó el acceso desde el navegador. Inténtalo de nuevo; si terminas en la web, el retorno de escritorio necesita revisión."
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
