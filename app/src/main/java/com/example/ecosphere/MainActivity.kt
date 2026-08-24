package com.example.ecosphere

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ecosphere.auth.NativeAuthPage
import com.example.ecosphere.auth.NativeAuthViewModel
import com.example.ecosphere.auth.NativeSupabase
import com.example.ecosphere.data.network.NetworkModule
import com.example.ecosphere.data.repository.SensorRepository
import com.example.ecosphere.ui.icons.DashboardControlIcons
import com.example.ecosphere.ui.screens.EcoSphereApp
import com.example.ecosphere.ui.screens.NativeAuthScreen
import com.example.ecosphere.ui.theme.EcoSphereTheme
import com.example.ecosphere.ui.viewmodel.EcoSphereViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    private lateinit var authViewModel: NativeAuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        authViewModel = ViewModelProvider(
            this,
            NativeAuthViewModel.factory(application)
        )[NativeAuthViewModel::class.java]

        handleAuthIntent(intent)

        setContent {
            EcoSphereTheme {
                val authState = authViewModel.uiState
                if (authState.page == NativeAuthPage.APP) {
                    val repository = remember(authState.profile?.email) {
                        SensorRepository(NetworkModule.api) {
                            NativeSupabase.client.auth.currentAccessTokenOrNull()
                        }
                    }

                    val ecoSphereViewModel: EcoSphereViewModel = viewModel(
                        key = "dashboard-${authState.profile?.email}",
                        factory = EcoSphereViewModel.factory(repository)
                    )

                    EcoSphereApp(
                        uiState = ecoSphereViewModel.uiState,
                        profileName = authState.profile?.fullName.orEmpty(),
                        profileRole = authState.profile?.role.orEmpty(),
                        onSignOut = authViewModel::signOut,
                        onRefresh = {
                            DashboardControlIcons.triggerRefreshAnimation()
                            ecoSphereViewModel.refreshDashboard()
                        },
                        onRefreshHistory = ecoSphereViewModel::refreshHistory,
                        onSelectHistoryMonth = ecoSphereViewModel::selectHistoryMonth,
                        onLoadMoreHistory = ecoSphereViewModel::loadMoreHistory,
                        onAutoModeChange = ecoSphereViewModel::setAutoMode,
                        onFanPowerChange = ecoSphereViewModel::setFanPower,
                        onLedPowerChange = ecoSphereViewModel::setLedPower,
                        onPumpRequest = ecoSphereViewModel::requestPump
                    )
                } else {
                    NativeAuthScreen(
                        state = authState,
                        onShowLogin = authViewModel::showLogin,
                        onShowRegister = authViewModel::showRegister,
                        onSignIn = authViewModel::signIn,
                        onRegister = authViewModel::registerWithEmail,
                        onOAuth = authViewModel::startOAuth,
                        onVerifyMfa = authViewModel::verifyMfa,
                        onSignOut = authViewModel::signOut
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent) {
        NativeSupabase.client.handleDeeplinks(
            intent = intent,
            onSessionSuccess = { authViewModel.onOAuthCallback() },
            onError = { authViewModel.onOAuthCallback() }
        )
    }
}
