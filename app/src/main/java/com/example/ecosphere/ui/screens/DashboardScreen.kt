package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecosphere.ui.viewmodel.EcoSphereUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: EcoSphereUiState,
    onRefresh: () -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onFanTargetChange: (Boolean) -> Unit,
    onLedTargetChange: (Boolean) -> Unit,
    onPumpRequest: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("EcoSphere") },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && !uiState.isUpdatingControl
            ) {
                Text("Actualizar")
            }

            if (uiState.isLoading) {
                CircularProgressIndicator()
                Text("Cargando datos desde Supabase...")
            }

            uiState.error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            uiState.controlMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            DeviceConnectionCard(
                esp32Online = uiState.deviceControl?.esp32Online ?: false,
                lastSeenAt = uiState.deviceControl?.lastSeenAt ?: "Sin conexión registrada"
            )

            uiState.record?.let { record ->
                MetricCard("Temperatura", "${record.temperature ?: 0.0} °C")
                MetricCard("Humedad del aire", "${record.airHumidity ?: 0.0} %")
                MetricCard("Humedad del suelo", "${record.soilHumidity ?: 0.0} %")
                MetricCard("Luz", "${record.lightLux ?: 0.0} lux")
                MetricCard("Nivel de agua", record.waterLevel ?: "unknown")
                MetricCard("Última lectura", record.createdAt ?: "Sin fecha")

                StatusCard(
                    fanOn = record.fanOn ?: false,
                    pumpOn = record.pumpOn ?: false,
                    ledOn = record.ledOn ?: false,
                    autoMode = record.autoMode ?: false
                )
            }

            ControlCard(
                autoMode = uiState.deviceControl?.autoMode ?: false,
                fanTarget = uiState.deviceControl?.fanTarget ?: false,
                ledTarget = uiState.deviceControl?.ledTarget ?: false,
                pumpRequest = uiState.deviceControl?.pumpRequest ?: 0L,
                pumpDurationMs = uiState.deviceControl?.pumpDurationMs ?: 3000,
                isUpdating = uiState.isUpdatingControl,
                onAutoModeChange = onAutoModeChange,
                onFanTargetChange = onFanTargetChange,
                onLedTargetChange = onLedTargetChange,
                onPumpRequest = onPumpRequest
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DeviceConnectionCard(
    esp32Online: Boolean,
    lastSeenAt: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Conexión del ESP32", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Estado")
                Text(if (esp32Online) "Online" else "Offline")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Última conexión")
                Text(lastSeenAt)
            }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Estado real del sistema", style = MaterialTheme.typography.titleMedium)
            StatusRow("Ventilador", if (fanOn) "Encendido" else "Apagado")
            StatusRow("Bomba", if (pumpOn) "Encendida" else "Apagada")
            StatusRow("LED grow", if (ledOn) "Encendido" else "Apagado")
            StatusRow("Modo automático", if (autoMode) "Activo" else "Manual")
        }
    }
}

@Composable
private fun ControlCard(
    autoMode: Boolean,
    fanTarget: Boolean,
    ledTarget: Boolean,
    pumpRequest: Long,
    pumpDurationMs: Int,
    isUpdating: Boolean,
    onAutoModeChange: (Boolean) -> Unit,
    onFanTargetChange: (Boolean) -> Unit,
    onLedTargetChange: (Boolean) -> Unit,
    onPumpRequest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Control remoto", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "La app escribe órdenes en Supabase y el ESP32 las ejecuta por Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            ControlSwitchRow(
                title = "Modo automático",
                checked = autoMode,
                enabled = !isUpdating,
                onCheckedChange = onAutoModeChange
            )

            ControlSwitchRow(
                title = "Ventilador",
                checked = fanTarget,
                enabled = !autoMode && !isUpdating,
                onCheckedChange = onFanTargetChange
            )

            ControlSwitchRow(
                title = "LED grow",
                checked = ledTarget,
                enabled = !autoMode && !isUpdating,
                onCheckedChange = onLedTargetChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPumpRequest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUpdating
            ) {
                Text("Regar ahora (${pumpDurationMs} ms)")
            }

            Text(
                text = "Solicitudes de riego enviadas: $pumpRequest",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (isUpdating) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ControlSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun StatusRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title)
        Text(value)
    }
}
