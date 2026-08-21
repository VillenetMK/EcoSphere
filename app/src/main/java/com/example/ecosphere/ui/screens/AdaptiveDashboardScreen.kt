package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecosphere.ui.viewmodel.EcoSphereUiState

/**
 * Adaptive host for the current dashboard.
 *
 * The existing phone UI is preserved, while large tablets, Chromebooks,
 * convertible laptops and desktop-sized Android windows get a constrained,
 * centered work area instead of an endlessly stretched layout.
 */
@Composable
fun AdaptiveDashboardScreen(
    uiState: EcoSphereUiState,
    onRefresh: () -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onFanPowerChange: (Int) -> Unit,
    onLedPowerChange: (Int) -> Unit,
    onPumpRequest: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxContentWidth = when {
            maxWidth >= 1600.dp -> 1280.dp
            maxWidth >= 1200.dp -> 1120.dp
            maxWidth >= 840.dp -> 920.dp
            else -> maxWidth
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxContentWidth)
            ) {
                DashboardScreen(
                    uiState = uiState,
                    onRefresh = onRefresh,
                    onAutoModeChange = onAutoModeChange,
                    onFanPowerChange = onFanPowerChange,
                    onLedPowerChange = onLedPowerChange,
                    onPumpRequest = onPumpRequest
                )
            }
        }
    }
}
