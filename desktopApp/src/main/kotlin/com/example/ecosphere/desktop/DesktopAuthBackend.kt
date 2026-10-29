package com.example.ecosphere.desktop

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
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.invoke
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

internal data class DesktopMfaChallenge(val factorId: String, val secret: String? = null)

/** Network boundary: the controller can be verified without production accounts or device commands. */
internal interface DesktopAuthBackend {
    suspend fun initialize()
    fun sessionEmail(): String?
    fun accessToken(): String?
    suspend fun signIn(identifier: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun oauth(provider: String)
    suspend fun signOut()
    suspend fun profile(): EcoSphereUserProfile?
    suspend fun completeProfile(draft: RegistrationDraft)
    suspend fun adminChallenge(profile: EcoSphereUserProfile): DesktopMfaChallenge?
    suspend fun verifyMfa(factorId: String, code: String)
}

internal class SupabaseDesktopAuthBackend : DesktopAuthBackend {
    private val supabase = DesktopSupabase.client
    private val gson = Gson()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build()

    override suspend fun initialize() = supabase.auth.awaitInitialization()
    override fun sessionEmail() = supabase.auth.currentSessionOrNull()?.user?.email?.lowercase()
    override fun accessToken() = supabase.auth.currentAccessTokenOrNull()

    override suspend fun signIn(identifier: String, password: String) {
        val normalized = identifier.trim()
        if ('@' in normalized) {
            supabase.auth.signInWith(Email) {
                email = normalized.lowercase()
                this.password = password
            }
        } else {
            require(Regex("^[A-Za-z][A-Za-z0-9._-]{2,31}$").matches(normalized)) {
                "Usuario o contraseña incorrectos."
            }
            val tokens = withContext(Dispatchers.IO) {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("${EcoSphereConfig.SUPABASE_URL}/functions/v1/username-login"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", EcoSphereConfig.SUPABASE_PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer ${EcoSphereConfig.SUPABASE_PUBLISHABLE_KEY}")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                        gson.toJson(mapOf("username" to normalized, "password" to password))
                    )).build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                require(response.statusCode() in 200..299) {
                    runCatching { gson.fromJson(response.body(), Map::class.java)["error"]?.toString() }
                        .getOrNull() ?: "No se pudo iniciar sesión."
                }
                gson.fromJson(response.body(), UsernameLoginResponse::class.java)
            }
            supabase.auth.importAuthToken(tokens.accessToken, tokens.refreshToken, retrieveUser = true)
        }
    }

    override suspend fun signUp(email: String, password: String) {
        supabase.auth.signUpWith(Email) { this.email = email; this.password = password }
    }

    override suspend fun oauth(provider: String) {
        val oauthProvider = when (provider) {
            DesktopAuthController.PROVIDER_GOOGLE -> OAuthProvider(provider)
            DesktopAuthController.PROVIDER_GITHUB -> Github
            else -> error("Proveedor de acceso no permitido.")
        }
        // supabase-kt 3.7.0 on desktop waits for the local callback before returning.
        // Do not pass redirectUrl here: that bypasses the SDK's local callback server.
        supabase.auth.signInWith(oauthProvider) {
            if (provider == DesktopAuthController.PROVIDER_GOOGLE) queryParams["prompt"] = "select_account"
        }
    }

    override suspend fun signOut() = supabase.auth.signOut()
    override suspend fun profile(): EcoSphereUserProfile? =
        supabase.postgrest.rpc("my_profile").decodeSingleOrNull()

    override suspend fun completeProfile(draft: RegistrationDraft) {
        supabase.postgrest.rpc("complete_user_profile", buildJsonObject {
            put("p_username", draft.username)
            put("p_first_name", draft.firstName)
            put("p_last_name", draft.lastName)
            put("p_dni", draft.dni)
            put("p_phone", draft.phone)
            put("p_expected_email", draft.email)
        })
    }

    override suspend fun adminChallenge(profile: EcoSphereUserProfile): DesktopMfaChallenge? {
        if (supabase.auth.mfa.getAuthenticatorAssuranceLevel().current == AuthenticatorAssuranceLevel.AAL2) return null
        val factors = supabase.auth.mfa.retrieveFactorsForCurrentUser()
        factors.firstOrNull { it.factorType == "totp" && it.isVerified }?.let {
            return DesktopMfaChallenge(it.id)
        }
        factors.filter { it.factorType == "totp" && !it.isVerified }.forEach { supabase.auth.mfa.unenroll(it.id) }
        val enrollment = supabase.auth.mfa.enroll(FactorType.TOTP, friendlyName = "EcoSphere ${profile.username}") {
            issuer = "EcoSphere"
        }
        return DesktopMfaChallenge(enrollment.id, enrollment.data.secret)
    }

    override suspend fun verifyMfa(factorId: String, code: String) {
        supabase.auth.mfa.createChallengeAndVerify(factorId, code)
        check(supabase.auth.mfa.getAuthenticatorAssuranceLevel().current == AuthenticatorAssuranceLevel.AAL2) {
            "No se pudo completar la verificación en dos pasos."
        }
    }

    private data class UsernameLoginResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("refresh_token") val refreshToken: String
    )
}
