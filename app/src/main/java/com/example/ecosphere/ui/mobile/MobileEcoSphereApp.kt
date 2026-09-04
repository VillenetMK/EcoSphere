package com.example.ecosphere.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecosphere.ui.icons.EcoSphereIcons
import com.example.ecosphere.ui.screens.AdaptiveDashboardScreen
import com.example.ecosphere.ui.screens.ControlAuditScreen
import com.example.ecosphere.ui.screens.DiagnosticsScreen
import com.example.ecosphere.ui.screens.HistoryScreen
import com.example.ecosphere.ui.viewmodel.EcoSphereUiState
import kotlinx.coroutines.launch

private val MobileNavigationGreen = Color(0xFF66FF7A)
private val MobileNavigationSurface = Color(0xFF101914)

private enum class MobileDestination(
    val menuLabel: String,
    val icon: ImageVector
) {
    DASHBOARD("Panel principal", EcoSphereIcons.Dashboard),
    HISTORY("Registros históricos", EcoSphereIcons.History),
    DIAGNOSTICS("Diagnóstico del sistema", EcoSphereIcons.Diagnostics),
    AUDIT("Registro de actividad", EcoSphereIcons.History),
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
    onRefreshControlAudit: () -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onFanPowerChange: (Int) -> Unit,
    onLedPowerChange: (Int) -> Unit,
    onPumpRequest: () -> Unit,
    onRefreshController: () -> Unit,
    onAuthorizeController: (String, String) -> Unit,
    onReplaceController: (String) -> Unit
) {
    var destinationName by rememberSaveable { mutableStateOf(MobileDestination.DASHBOARD.name) }
    val destination = MobileDestination.entries
        .firstOrNull { it.name == destinationName }
        ?: MobileDestination.DASHBOARD
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val availableDestinations = MobileDestination.entries.filter { item ->
        item != MobileDestination.AUDIT || profileRole == "admin"
    }

    LaunchedEffect(profileRole, destination) {
        if (destination == MobileDestination.AUDIT && profileRole != "admin") {
            destinationName = MobileDestination.DASHBOARD.name
            return@LaunchedEffect
        }

        when (destination) {
            MobileDestination.HISTORY -> onRefreshHistory()
            MobileDestination.DIAGNOSTICS -> if (profileRole == "admin") onRefreshController()
            MobileDestination.AUDIT -> onRefreshControlAudit()
            else -> Unit
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.84f),
                drawerContainerColor = MobileNavigationSurface
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "EcoSphere",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MobileNavigationGreen
                    )
                    Text(
                        text = "Navegación",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    availableDestinations.forEach { item ->
                        NavigationDrawerItem(
                            selected = item == destination,
                            onClick = {
                                destinationName = item.name
                                coroutineScope.launch { drawerState.close() }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.menuLabel,
                                    tint = Color.Unspecified
                                )
                            },
                            label = {
                                Text(
                                    text = item.menuLabel,
                                    fontWeight = if (item == destination) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
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
                        isControllerBusy = uiState.isControllerBusy,
                        controllerMessage = uiState.controllerMessage,
                        onAuthorizeController = onAuthorizeController,
                        onReplaceController = onReplaceController
                    )

                    MobileDestination.AUDIT -> ControlAuditScreen(
                        entries = uiState.controlAudit,
                        isLoading = uiState.isLoadingControlAudit,
                        error = uiState.controlAuditError,
                        onRefresh = onRefreshControlAudit
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
                        else -> "Operador"
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
