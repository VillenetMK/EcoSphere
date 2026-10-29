package com.example.ecosphere.desktop

import com.example.ecosphere.shared.EcoSphereUserProfile
import com.example.ecosphere.shared.RegistrationDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DesktopAuthControllerTest {
    private val prefs = Preferences.userRoot().node("com/example/ecosphere/tests/${UUID.randomUUID()}")
    private val backend = FakeBackend()
    private val controller = DesktopAuthController(backend, prefs) { _, _ -> }

    @AfterTest fun cleanup() { prefs.removeNode(); prefs.flush() }

    @Test fun initializationFailureReturnsToUsableLogin() = runTest {
        backend.failure = IllegalStateException("network unavailable")
        controller.initialize()
        assertEquals(DesktopAuthPage.LOGIN, controller.state.page)
        assertFalse(controller.state.busy)
        assertNotNull(controller.state.message)
        backend.failure = null
        controller.signIn("test@example.invalid", "sample-password")
        assertEquals(DesktopAuthPage.APP, controller.state.page)
    }

    @Test fun invalidPasswordDoesNotEnterApp() = runTest {
        controller.initialize()
        backend.failure = IllegalArgumentException("Invalid login credentials")
        controller.signIn("test@example.invalid", "wrong")
        assertEquals(DesktopAuthPage.LOGIN, controller.state.page)
        assertEquals("Usuario o contraseña incorrectos.", controller.state.message)
        assertFalse(controller.state.busy)
    }

    @Test fun oauthWithoutCallbackExplainsFailureInsteadOfSilentLogin() = runTest {
        controller.initialize()
        backend.oauthCompletes = false
        controller.startOAuth("google")
        assertEquals(DesktopAuthPage.LOGIN, controller.state.page)
        assertEquals(DesktopAuthController.OAUTH_NOT_COMPLETED, controller.state.message)
        assertFalse(controller.state.busy)
        assertNull(controller.state.oauthProvider)
    }

    @Test fun bothOAuthProvidersResolveProfileAfterCallback() = runTest {
        for (provider in listOf("google", "github")) {
            controller.initialize()
            controller.startOAuth(provider)
            assertEquals(provider, backend.lastProvider)
            assertEquals(DesktopAuthPage.APP, controller.state.page)
            assertEquals("operator", controller.state.profile?.role)
            controller.signOut()
        }
    }

    @Test fun missingOAuthProfileDoesNotGrantAccessOrInventIdentity() = runTest {
        backend.userProfile = null
        controller.startOAuth("google")
        assertEquals(DesktopAuthPage.LOGIN, controller.state.page)
        assertNull(backend.accessToken())
        assertTrue(controller.state.message.orEmpty().contains("preparar tu cuenta"))
    }

    @Test fun blockedAndPendingAccountsRemainOutsideApp() = runTest {
        for (status in listOf("blocked", "pending")) {
            backend.userProfile = profile().copy(status = status)
            controller.signIn("test@example.invalid", "sample-password")
            assertEquals(DesktopAuthPage.PENDING, controller.state.page)
            assertEquals(status, controller.state.profile?.status)
            controller.signOut()
        }
    }

    @Test fun administratorRequiresVerifiedMfaBeforeEnteringApp() = runTest {
        backend.userProfile = profile().copy(role = "admin")
        backend.challenge = DesktopMfaChallenge("test-factor")
        controller.signIn("test@example.invalid", "sample-password")
        assertEquals(DesktopAuthPage.MFA, controller.state.page)
        controller.verifyMfa("123")
        assertEquals(0, backend.mfaCalls)
        backend.failure = IllegalArgumentException("invalid totp")
        controller.verifyMfa("123456")
        assertEquals(DesktopAuthPage.MFA, controller.state.page)
        backend.failure = null
        controller.verifyMfa("123456")
        assertEquals(DesktopAuthPage.APP, controller.state.page)
        assertNull(controller.state.mfaFactorId)
        assertNull(controller.state.mfaSecret)
    }

    @Test fun invalidRegistrationDoesNotCallServer() = runTest {
        controller.showRegister()
        controller.registerWithEmail("a", "", "", "", "", "bad", "short", "different")
        assertEquals(0, backend.signupCalls)
        assertTrue(controller.state.fieldErrors.keys.containsAll(listOf("username", "email", "password")))
    }

    @Test fun confirmedRegistrationSurvivesRestartAndCompletesProfileWithoutPasswordStorage() = runTest {
        register()
        assertEquals(DesktopAuthPage.LOGIN, controller.state.page)
        assertEquals(1, backend.signupCalls)
        assertTrue(prefs.keys().none { prefs.get(it, "").contains("sample-password") })
        backend.signedInEmail = "test@example.invalid"
        DesktopAuthController(backend, prefs) { _, _ -> }.also { restarted ->
            restarted.initialize()
            assertEquals(DesktopAuthPage.APP, restarted.state.page)
            assertEquals("test@example.invalid", backend.completedDraft?.email)
            assertNull(prefs.get("pending-registration", null))
        }
    }

    @Test fun draftForDifferentAccountIsNeverApplied() = runTest {
        register()
        backend.signedInEmail = "different@example.invalid"
        controller.initialize()
        assertNull(backend.completedDraft)
        assertNull(backend.accessToken())
        assertNotEquals(DesktopAuthPage.APP, controller.state.page)
        assertTrue(controller.state.message.orEmpty().contains("no coincide"))
    }

    @Test fun missingEmailProfileOpensCompletionForm() = runTest {
        backend.userProfile = null
        controller.signIn("test@example.invalid", "sample-password")
        assertEquals(DesktopAuthPage.REGISTER, controller.state.page)
        assertEquals("test@example.invalid", controller.state.verifiedEmail)
    }

    @Test fun concurrentOAuthClicksDoNotOpenMultipleCallbackServers() = runTest {
        val gate = CompletableDeferred<Unit>()
        backend.oauthWait = { gate.await() }
        val login = launch { controller.startOAuth("google") }
        runCurrent()
        assertTrue(controller.state.busy)
        assertEquals("google", controller.state.oauthProvider)
        controller.startOAuth("github")
        assertEquals(1, backend.oauthCalls)
        gate.complete(Unit)
        login.join()
        assertEquals(DesktopAuthPage.APP, controller.state.page)
    }

    @Test fun cancellingOAuthReleasesBusyStateAndAllowsRetry() = runTest {
        controller.initialize()
        backend.oauthWait = { awaitCancellation() }
        val login = launch { controller.startOAuth("google") }
        runCurrent()
        controller.cancelOAuth()
        login.join()
        assertFalse(controller.state.busy)
        assertNull(controller.state.oauthProvider)
        assertTrue(controller.state.message.orEmpty().contains("cancelado"))
        backend.oauthWait = {}
        controller.startOAuth("github")
        assertEquals(DesktopAuthPage.APP, controller.state.page)
    }

    @Test fun boundCallbackPortGetsActionableMessage() = runTest {
        controller.initialize()
        backend.failure = java.net.BindException("Address already in use")
        controller.startOAuth("google")
        assertTrue(controller.state.message.orEmpty().contains("Otra aplicación"))
        assertFalse(controller.state.busy)
    }

    @Test fun diagnosticsNeverIncludeSensitiveExceptionMessages() {
        val sensitive = "password=secret token=abcd email=test@example.invalid code=123456"
        val log = DesktopDiagnostics.safeEntry(sensitive, IllegalStateException(sensitive, IllegalArgumentException(sensitive)))
        for (value in listOf("secret", "abcd", "test@example.invalid", "123456")) assertFalse(log.contains(value))
        assertTrue(log.contains("operation=unknown"))
        assertTrue(log.contains("IllegalArgumentException"))
    }

    private suspend fun register() {
        controller.showRegister()
        controller.registerWithEmail("testuser", "Test", "Person", "12345678", "+51987654321", "test@example.invalid", "sample-password", "sample-password")
    }

    private class FakeBackend : DesktopAuthBackend {
        var signedInEmail: String? = null
        var userProfile: EcoSphereUserProfile? = profile()
        var challenge: DesktopMfaChallenge? = null
        var failure: Exception? = null
        var oauthCompletes = true
        var lastProvider: String? = null
        var oauthCalls = 0
        var signupCalls = 0
        var mfaCalls = 0
        var completedDraft: RegistrationDraft? = null
        var oauthWait: suspend () -> Unit = {}
        private fun failIfRequested() { failure?.let { throw it } }
        override suspend fun initialize() { failIfRequested() }
        override fun sessionEmail() = signedInEmail
        override fun accessToken() = signedInEmail?.let { "test-token" }
        override suspend fun signIn(identifier: String, password: String) { failIfRequested(); signedInEmail = "test@example.invalid" }
        override suspend fun signUp(email: String, password: String) { failIfRequested(); signupCalls++ }
        override suspend fun oauth(provider: String) {
            oauthCalls++; lastProvider = provider; failIfRequested(); oauthWait()
            if (oauthCompletes) signedInEmail = "test@example.invalid"
        }
        override suspend fun signOut() { signedInEmail = null }
        override suspend fun profile(): EcoSphereUserProfile? { failIfRequested(); return userProfile }
        override suspend fun completeProfile(draft: RegistrationDraft) { failIfRequested(); completedDraft = draft }
        override suspend fun adminChallenge(profile: EcoSphereUserProfile) = challenge
        override suspend fun verifyMfa(factorId: String, code: String) { mfaCalls++; failIfRequested() }
    }

    companion object {
        private fun profile() = EcoSphereUserProfile("testuser", "Test", "Person", "Test Person", "test@example.invalid", "email", "approved", "operator")
    }
}
