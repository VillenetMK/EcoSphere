package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecosphere.data.model.SensorRecord
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<SensorRecord>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Registros históricos", fontWeight = FontWeight.Bold)
                        Text(
                            "Últimas 100 lecturas de EcoSphere",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 10.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (isLoading) "Cargando..." else "Actualizar historial")
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!isLoading && records.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "Todavía no hay registros históricos disponibles.",
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        HistoryRecordCard(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(record: SensorRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formatHistoryTimestamp(record.createdAt),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HistoryPair("Temperatura", record.temperature?.let { "${formatValue(it)} °C" } ?: "Sin dato")
            HistoryPair("Humedad aire", record.airHumidity?.let { "${formatValue(it)} %" } ?: "Sin dato")
            HistoryPair("Humedad suelo", record.soilHumidity?.let { "${formatValue(it)} %" } ?: "Sin dato")
            HistoryPair("Iluminación", record.lightLux?.let { "${formatValue(it)} lx" } ?: "Sin dato")
            HistoryPair("Depósito", waterLevelLabel(record.waterLevel))
            HistoryPair("Ventilador", actuatorLabel(record.fanOn, record.fanPower))
            HistoryPair("Bomba", if (record.pumpOn == true) "Encendida" else "Apagada")
            HistoryPair("LED grow", actuatorLabel(record.ledOn, record.ledPower))
            HistoryPair("Modo", if (record.autoMode == true) "Automático" else "Manual")
        }
    }
}

@Composable
private fun HistoryPair(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun actuatorLabel(on: Boolean?, power: Int?): String {
    return if (on == true) {
        power?.let { "Encendido · $it %" } ?: "Encendido"
    } else {
        "Apagado"
    }
}

private fun waterLevelLabel(value: String?): String {
    return when (value?.lowercase()) {
        "high" -> "Nivel alto"
        "medium" -> "Nivel medio"
        "low" -> "Nivel bajo"
        null -> "Sin dato"
        else -> value
    }
}

private fun formatValue(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}

private fun formatHistoryTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "Fecha no disponible"

    return try {
        val normalized = normalizeIsoTimestamp(value)
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val output = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        parser.parse(normalized)?.let(output::format) ?: value
    } catch (_: Exception) {
        value
    }
}

private fun normalizeIsoTimestamp(value: String): String {
    val noZone = value.substringBefore("+").substringBefore("Z")
    val base = noZone.substringBefore(".")
    val fraction = noZone.substringAfter(".", "0").padEnd(3, '0').take(3)
    return "$base.$fraction"
}
