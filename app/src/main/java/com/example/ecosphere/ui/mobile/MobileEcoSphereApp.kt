package com.example.ecosphere.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecosphere.ui.icons.EcoSphereIcons
import com.example.ecosphere.ui.screens.AdaptiveDashboardScreen
import com.example.ecosphere.ui.screens.DiagnosticsScreen
import com.example.ecosphere.ui.screens.HistoryScreen
import com.example.ecosphere.ui.viewmodel.EcoSphereUiState

private val MobileNavigationGreen = Color(0xFF66FF7A)
private val MobileNavigationSurface = Color(0xFF101914)
private val MobileNavigationIndicator = Color(0xFF203729)

private enum class MobileDestination(
    val label: String,
    val icon: ImageVector
) {
    DASHBOARD("Inicio", EcoSphereIcons.Dashboard),
    HISTORY("Historial", EcoSphereIcons.History),
    DIAGNOSTICS("Diagnóstico", EcoSphereIcons.Diagnostics),
    ACCOUNT("Cuenta", EcoSphereIcons.Settings)
}

@Composable
fun MobileEcoSphereApp(
    uiState: EcoSphereUiState,
    profileName: String,
    profileRole: String,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshHistory: () -> Unit,
    onSelectHistoryMonth: (String) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onFanPowerChange: (Int) -> Unit,
    onLedPowerChange: (Int) -> Unit,
    onPumpRequest: () -> Unit,
    onRefreshController: () -> Unit,
    onReplaceController: (String) -> Unit
) {
    var destinationName by rememberSaveable { mutableStateOf(MobileDestination.DASHBOARD.name) }
    val destination = MobileDestination.valueOf(destinationName)

    LaunchedEffect(destination) {
        when (destination) {
            MobileDestination.HISTORY -> onRefreshHistory()
            MobileDestination.DIAGNOSTICS -> onRefreshController()
            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MobileNavigationSurface,
                tonalElevation = 0.dp
            ) {
                MobileDestination.entries.forEach { item ->
                    val selected = item == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { destinationName = item.name },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = Color.Unspecified
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = MobileNavigationGreen,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MobileNavigationIndicator
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (destination) {
                MobileDestination.DASHBOARD -> AdaptiveDashboardScreen(
                    uiState = uiState,
                    onRefresh = onRefresh,
                    onAutoModeChange = onAutoModeChange,
                    onFanPowerChange = onFanPowerChange,
                    onLedPowerChange = onLedPowerChange,
                    onPumpRequest = onPumpRequest
                )

                MobileDestination.HISTORY -> HistoryScreen(
                    records = uiState.history,
                    months = uiState.historyMonths,
                    selectedMonth = uiState.selectedHistoryMonth,
                    hasMore = uiState.historyHasMore,
                    isLoading = uiState.isLoadingHistory,
                    isLoadingMore = uiState.isLoadingMoreHistory,
                    error = uiState.error,
                    onRefresh = onRefreshHistory,
                    onSelectMonth = onSelectHistoryMonth,
                    onLoadMore = onLoadMoreHistory
                )

                MobileDestination.DIAGNOSTICS -> DiagnosticsScreen(
                    record = uiState.record,
                    control = uiState.deviceControl,
                    onRefresh = onRefresh,
                    isAdmin = profileRole == "admin",
                    controllerStatus = uiState.controllerStatus,
                    isReplacingController = uiState.isReplacingController,
                    controllerMessage = uiState.controllerMessage,
                    onRefreshController = onRefreshController,
                    onReplaceController = onReplaceController
                )

                MobileDestination.ACCOUNT -> MobileAccountScreen(
                    profileName = profileName,
                    profileRole = profileRole,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@Composable
private fun MobileAccountScreen(
    profileName: String,
    profileRole: String,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Tu cuenta",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Sesión y permisos de EcoSphere",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = profileName.ifBlank { "Cuenta verificada" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (profileRole) {
                        "admin" -> "Administrador"
                        "operator" -> "Operador"
                        else -> "Visualizador"
                    },
                    color = MobileNavigationGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "La navegación, los controles y los estados mostrados aquí pertenecen exclusivamente a la aplicación Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text("Cerrar sesión", fontWeight = FontWeight.Bold)
        }
    }
}
