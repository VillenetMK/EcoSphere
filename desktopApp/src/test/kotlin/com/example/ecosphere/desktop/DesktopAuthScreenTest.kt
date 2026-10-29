package com.example.ecosphere.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAuthScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactLoginSupportsKeyboardAndDisplaysErrors() {
        var submitted = ""
        compose.setContent {
            DesktopTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    Screen(DesktopAuthState(page = DesktopAuthPage.LOGIN, busy = false,
                        message = "Usuario o contraseña incorrectos."), onSignIn = { user, _ -> submitted = user })
                }
            }
        }
        compose.onNodeWithTag("auth-message").assertIsDisplayed()
        compose.onNodeWithTag("login-identifier").performTextInput("testuser")
        compose.onNodeWithTag("login-password").performScrollTo().performTextInput("sample-password")
        compose.onNodeWithTag("login-password").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("testuser", submitted) }
        capture("login-800x600")
    }

    @Test fun smallRegistrationCanReachSubmitAndPreservesEditsAcrossTabs() {
        var state by mutableStateOf(DesktopAuthState(page = DesktopAuthPage.REGISTER, busy = false))
        compose.setContent {
            DesktopTheme {
                Box(Modifier.requiredSize(640.dp, 480.dp)) {
                    Screen(state, onShowLogin = { state = state.copy(page = DesktopAuthPage.LOGIN) },
                        onShowRegister = { state = state.copy(page = DesktopAuthPage.REGISTER) })
                }
            }
        }
        compose.onNodeWithTag("register-username").performScrollTo().performTextInput("retaineduser")
        compose.onNodeWithTag("register-submit").performScrollTo().assertIsDisplayed()
        capture("register-640x480-bottom")
        compose.onNodeWithText("Iniciar sesión", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Crear cuenta", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("register-username").performScrollTo().assertTextContains("retaineduser")
    }

    @Test fun oauthShowsBrowserStatusAndWorkingCancelAction() {
        var cancelled = false
        compose.setContent {
            DesktopTheme {
                Screen(DesktopAuthState(page = DesktopAuthPage.LOGIN, busy = true, oauthProvider = "google"),
                    onCancelOAuth = { cancelled = true })
            }
        }
        compose.onNodeWithText("Continúa en Google").assertIsDisplayed()
        compose.onNodeWithText("Cancelar acceso").performClick()
        compose.onNodeWithTag("login-submit").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    private fun capture(name: String) {
        val file = File("build/reports/desktop-ui/$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", file)
    }
}

@Composable
private fun Screen(
    state: DesktopAuthState,
    onShowLogin: () -> Unit = {}, onShowRegister: () -> Unit = {},
    onSignIn: (String, String) -> Unit = { _, _ -> }, onCancelOAuth: () -> Unit = {}
) = DesktopAuthScreen(state, onShowLogin, onShowRegister, onSignIn,
    onRegister = { _, _, _, _, _, _, _, _ -> }, onOAuth = {}, onVerifyMfa = {}, onSignOut = {}, onCancelOAuth = onCancelOAuth)
