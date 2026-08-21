package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
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

    fun selectDestination(newDestination: EcoSphereDestination) {
        destinationName = newDestination.name
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "EcoSphere",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))

                NavigationDrawerItem(
                    label = { Text("Panel principal") },
                    selected = destination == EcoSphereDestination.DASHBOARD,
                    onClick = { selectDestination(EcoSphereDestination.DASHBOARD) }
                )
                NavigationDrawerItem(
                    label = { Text("Registros históricos") },
                    selected = destination == EcoSphereDestination.HISTORY,
                    onClick = { selectDestination(EcoSphereDestination.HISTORY) }
                )
                NavigationDrawerItem(
                    label = { Text("Diagnóstico del sistema") },
                    selected = destination == EcoSphereDestination.DIAGNOSTICS,
                    onClick = { selectDestination(EcoSphereDestination.DIAGNOSTICS) }
                )
            }
        }
    ) {
        when (destination) {
            EcoSphereDestination.DASHBOARD -> DashboardScreen(
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
}
