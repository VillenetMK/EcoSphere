package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecosphere.ui.viewmodel.EcoSphereUiState
import kotlinx.coroutines.launch

private enum class EcoSphereDestination {
    DASHBOARD,
    HISTORY,
    DIAGNOSTICS
}

@Composable
fun EcoSphereApp(
    uiState: EcoSphereUiState,
    onRefresh: () -> Unit,
    onRefreshHistory: () -> Unit,
    onSelectHistoryMonth: (String) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onFanPowerChange: (Int) -> Unit,
    onLedPowerChange: (Int) -> Unit,
    onPumpRequest: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destinationName by rememberSaveable {
        mutableStateOf(EcoSphereDestination.DASHBOARD.name)
    }
    val destination = EcoSphereDestination.valueOf(destinationName)

    LaunchedEffect(destination) {
        if (destination == EcoSphereDestination.HISTORY) {
            onRefreshHistory()
        }
    }

    fun selectDestination(newDestination: EcoSphereDestination, closeDrawer: Boolean) {
        destinationName = newDestination.name
        if (closeDrawer) {
            scope.launch { drawerState.close() }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactNavigation = maxWidth < 720.dp
        val sidebarWidth = if (maxWidth >= 1200.dp) 260.dp else 210.dp

        if (compactNavigation) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    ModalDrawerSheet {
                        EcoSphereNavigation(
                            destination = destination,
                            onSelect = { selectDestination(it, closeDrawer = true) }
                        )
                    }
                }
            ) {
                DestinationContent(
                    destination = destination,
                    uiState = uiState,
                    onRefresh = onRefresh,
                    onRefreshHistory = onRefreshHistory,
                    onSelectHistoryMonth = onSelectHistoryMonth,
                    onLoadMoreHistory = onLoadMoreHistory,
                    onAutoModeChange = onAutoModeChange,
                    onFanPowerChange = onFanPowerChange,
                    onLedPowerChange = onLedPowerChange,
                    onPumpRequest = onPumpRequest
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(sidebarWidth),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    EcoSphereNavigation(
                        destination = destination,
                        onSelect = { selectDestination(it, closeDrawer = false) }
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DestinationContent(
                        destination = destination,
                        uiState = uiState,
                        onRefresh = onRefresh,
                        onRefreshHistory = onRefreshHistory,
                        onSelectHistoryMonth = onSelectHistoryMonth,
                        onLoadMoreHistory = onLoadMoreHistory,
                        onAutoModeChange = onAutoModeChange,
                        onFanPowerChange = onFanPowerChange,
                        onLedPowerChange = onLedPowerChange,
                        onPumpRequest = onPumpRequest
                    )
                }
            }
        }
    }
}

@Composable
private fun EcoSphereNavigation(
    destination: EcoSphereDestination,
    onSelect: (EcoSphereDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 22.dp)) {
            Text(
                text = "EcoSphere",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Microclima inteligente",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        NavigationDrawerItem(
            label = { Text("Panel principal") },
            selected = destination == EcoSphereDestination.DASHBOARD,
            onClick = { onSelect(EcoSphereDestination.DASHBOARD) }
        )
        NavigationDrawerItem(
            label = { Text("Registros históricos") },
            selected = destination == EcoSphereDestination.HISTORY,
            onClick = { onSelect(EcoSphereDestination.HISTORY) }
        )
        NavigationDrawerItem(
            label = { Text("Diagnóstico del sistema") },
            selected = destination == EcoSphereDestination.DIAGNOSTICS,
            onClick = { onSelect(EcoSphereDestination.DIAGNOSTICS) }
        )
    }
}

@Composable
private fun DestinationContent(
    destination: EcoSphereDestination,
    uiState: EcoSphereUiState,
    onRefresh: () -> Unit,
    onRefreshHistory: () -> Unit,
    onSelectHistoryMonth: (String) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onFanPowerChange: (Int) -> Unit,
    onLedPowerChange: (Int) -> Unit,
    onPumpRequest: () -> Unit
) {
    when (destination) {
        EcoSphereDestination.DASHBOARD -> AdaptiveDashboardScreen(
            uiState = uiState,
            onRefresh = onRefresh,
            onAutoModeChange = onAutoModeChange,
            onFanPowerChange = onFanPowerChange,
            onLedPowerChange = onLedPowerChange,
            onPumpRequest = onPumpRequest
        )

        EcoSphereDestination.HISTORY -> HistoryScreen(
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

        EcoSphereDestination.DIAGNOSTICS -> DiagnosticsScreen(
            record = uiState.record,
            control = uiState.deviceControl,
            onRefresh = onRefresh
        )
    }
}
