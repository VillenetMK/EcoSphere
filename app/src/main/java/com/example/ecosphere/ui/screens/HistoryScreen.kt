package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecosphere.data.model.HistoryMonthSummary
import com.example.ecosphere.data.model.SensorRecord
import com.example.ecosphere.shared.ControlPolicy
import com.example.ecosphere.ui.icons.EcoSphereIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<SensorRecord>,
    months: List<HistoryMonthSummary>,
    selectedMonth: String?,
    hasMore: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onSelectMonth: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    val selectedSummary = months.firstOrNull { it.monthKey == selectedMonth }
    val firstHistoricalRecord = months.lastOrNull()?.firstRecord
    val groupedByDay = records.groupBy { formatHistoryDayHeading(it.createdAt) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = EcoSphereIcons.History,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text("Registros históricos", fontWeight = FontWeight.Bold)
                            Text(
                                firstHistoricalRecord?.let { "Datos almacenados desde ${formatHistoryDate(it)}" }
                                    ?: "Historial mensual de EcoSphere",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
            if (months.isNotEmpty()) {
                Text(
                    text = "Mes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(months, key = { it.monthKey }) { month ->
                        FilterChip(
                            selected = month.monthKey == selectedMonth,
                            onClick = { onSelectMonth(month.monthKey) },
                            label = {
                                Text("${monthLabel(month.monthKey)} · ${month.recordCount}")
                            },
                            enabled = !isLoading && !isLoadingMore
                        )
                    }
                }
            }

            selectedSummary?.let { summary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = monthLabel(summary.monthKey),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${summary.recordCount} registros almacenados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Mostrando ${records.size} de ${summary.recordCount}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Primero: ${formatHistoryTimestamp(summary.firstRecord)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Último: ${formatHistoryTimestamp(summary.lastRecord)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && !isLoadingMore
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 10.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = EcoSphereIcons.Refresh,
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    if (isLoading) "Cargando..." else "Actualizar historial",
                    modifier = Modifier.padding(start = if (isLoading) 0.dp else 8.dp)
                )
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!isLoading && months.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "Todavía no hay registros históricos disponibles.",
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else if (!isLoading && records.isEmpty() && selectedMonth != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "No hay lecturas disponibles para ${monthLabel(selectedMonth)}.",
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedByDay.forEach { (dayLabel, dayRecords) ->
                        item(key = "day-$dayLabel") {
                            Text(
                                text = dayLabel,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(dayRecords, key = { it.id }) { record ->
                            HistoryRecordCard(record)
                        }
                    }

                    if (hasMore || isLoadingMore) {
                        item(key = "load-more") {
                            Button(
                                onClick = onLoadMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                enabled = !isLoadingMore
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(end = 10.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                Text(if (isLoadingMore) "Cargando más..." else "Cargar 200 registros más")
                            }
                        }
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
                text = formatHistoryTime(record.createdAt),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HistoryPair(EcoSphereIcons.Temperature, "Temperatura", record.temperature?.let { "${formatValue(it)} °C" } ?: "Sin dato")
            HistoryPair(EcoSphereIcons.AirHumidity, "Humedad aire", record.airHumidity?.let { "${formatValue(it)} %" } ?: "Sin dato")
            HistoryPair(EcoSphereIcons.SoilHumidity, "Humedad suelo", record.soilHumidity?.let { "${formatValue(it)} %" } ?: "Sin dato")
            HistoryPair(EcoSphereIcons.Light, "Iluminación", record.lightLux?.let { "${formatValue(it)} lx" } ?: "Sin dato")
            HistoryPair(EcoSphereIcons.WaterLevel, "Depósito", waterLevelLabel(record.waterLevel))
            HistoryPair(EcoSphereIcons.Fan, "Ventilador", ControlPolicy.actuatorPwmLabel(record.fanOn, record.fanPower))
            HistoryPair(EcoSphereIcons.Pump, "Bomba", ControlPolicy.actuatorSwitchLabel(record.pumpOn))
            HistoryPair(EcoSphereIcons.GrowLed, "LED grow", ControlPolicy.actuatorPwmLabel(record.ledOn, record.ledPower))
            HistoryPair(
                when (record.autoMode) {
                    true -> EcoSphereIcons.AutoMode
                    false -> EcoSphereIcons.ManualMode
                    null -> EcoSphereIcons.Offline
                },
                "Modo",
                when (record.autoMode) {
                    true -> "Automático"
                    false -> "Manual"
                    null -> "Sin registro"
                }
            )
        }
    }
}

@Composable
private fun HistoryPair(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun waterLevelLabel(value: String?): String {
    return ControlPolicy.waterLevelLabel(value)
}

private fun formatValue(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}

private fun monthLabel(monthKey: String): String {
    val parts = monthKey.split("-")
    if (parts.size != 2) return monthKey

    val year = parts[0]
    val month = parts[1].toIntOrNull() ?: return monthKey
    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    return monthNames.getOrNull(month - 1)?.let { "$it $year" } ?: monthKey
}

private fun formatHistoryDate(value: String?): String {
    return formatHistory(value, "dd/MM/yyyy", "Fecha no disponible")
}

private fun formatHistoryTimestamp(value: String?): String {
    return formatHistory(value, "dd/MM/yyyy HH:mm:ss", "Fecha no disponible")
}

private fun formatHistoryDayHeading(value: String?): String {
    return formatHistory(value, "dd/MM/yyyy", "Fecha no disponible")
}

private fun formatHistoryTime(value: String?): String {
    return formatHistory(value, "HH:mm:ss", "Hora no disponible")
}

private fun formatHistory(value: String?, pattern: String, fallback: String): String {
    if (value.isNullOrBlank()) return fallback

    return try {
        val millis = ControlPolicy.timestampMillis(value) ?: return value
        val output = SimpleDateFormat(pattern, Locale.getDefault())
        output.format(Date(millis))
    } catch (_: Exception) {
        value
    }
}
