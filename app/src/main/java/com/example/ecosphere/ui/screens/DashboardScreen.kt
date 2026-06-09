package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecosphere.ui.viewmodel.EcoSphereUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: EcoSphereUiState,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text("EcoSphere")
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Actualizar")
            }

            when {
                uiState.isLoading -> {
                    CircularProgressIndicator()
                    Text("Cargando datos desde Supabase...")
                }

                uiState.error != null -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = uiState.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                uiState.record != null -> {
                    MetricCard("Temperatura", "${uiState.record.temperature ?: 0.0} °C")
                    MetricCard("Humedad del aire", "${uiState.record.airHumidity ?: 0.0} %")
                    MetricCard("Humedad del suelo", "${uiState.record.soilHumidity ?: 0.0} %")
                    MetricCard("Luz", "${uiState.record.lightLux ?: 0.0} lux")
                    MetricCard("Nivel de agua", uiState.record.waterLevel ?: "unknown")

                    StatusCard(
                        fanOn = uiState.record.fanOn ?: false,
                        pumpOn = uiState.record.pumpOn ?: false,
                        ledOn = uiState.record.ledOn ?: false,
                        autoMode = uiState.record.autoMode ?: false
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun StatusCard(
    fanOn: Boolean,
    pumpOn: Boolean,
    ledOn: Boolean,
    autoMode: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Estado del sistema", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ventilador")
                Text(if (fanOn) "Encendido" else "Apagado")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Bomba")
                Text(if (pumpOn) "Encendida" else "Apagada")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LED")
                Text(if (ledOn) "Encendido" else "Apagado")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Modo automático")
                Text(if (autoMode) "Activo" else "Manual")
            }
        }
    }
}