package com.example.ecosphere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ecosphere.data.network.NetworkModule
import com.example.ecosphere.data.repository.SensorRepository
import com.example.ecosphere.ui.screens.DashboardScreen
import com.example.ecosphere.ui.theme.EcoSphereTheme
import com.example.ecosphere.ui.viewmodel.EcoSphereViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EcoSphereTheme {
                val repository = remember {
                    SensorRepository(NetworkModule.api)
                }

                val ecoSphereViewModel: EcoSphereViewModel = viewModel(
                    factory = EcoSphereViewModel.factory(repository)
                )

                DashboardScreen(
                    uiState = ecoSphereViewModel.uiState,
                    onRefresh = ecoSphereViewModel::refreshDashboard,
                    onAutoModeChange = ecoSphereViewModel::setAutoMode,
                    onFanTargetChange = ecoSphereViewModel::setFanTarget,
                    onLedTargetChange = ecoSphereViewModel::setLedTarget,
                    onPumpRequest = ecoSphereViewModel::requestPump
                )
            }
        }
    }
}
