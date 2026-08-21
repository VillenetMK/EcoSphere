package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ecosphere.ui.viewmodel.EcoSphereUiState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

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
    val record = uiState.record
    val control = uiState.deviceControl
    val online = control?.isOnlineNow() ?: false

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column {
                        Text(
                            text = "EcoSphere",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sistema inteligente de microclima",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SystemOverviewCard(
                online = online,
                autoMode = control?.autoMode ?: record?.autoMode ?: false,
                lastSeenAt = formatTimestamp(control?.lastSeenAt),
                lastReadingAt = formatTimestamp(record?.createdAt),
                isLoading = uiState.isLoading,
                onRefresh = onRefresh
            )

            uiState.error?.let { message ->
                ErrorBanner(message)
            }

            uiState.controlMessage?.let { message ->
                InfoBanner(message)
            }

            SectionTitle(
                title = "Lecturas ambientales",
                subtitle = "Telemetría recibida desde el ESP32"
            )

            if (record == null) {
                EmptyTelemetryCard()
            } else {
                SensorMetrics(
                    temperature = record.temperature,
                    airHumidity = record.airHumidity,
                    soilHumidity = record.soilHumidity,
                    lightLux = record.lightLux,
                    waterLevel = record.waterLevel
                )
            }

            SectionTitle(
                title = "Estado del sistema",
                subtitle = "Estado real reportado por los actuadores"
            )

            ActuatorStatusCard(
                fanOn = record?.fanOn ?: false,
                pumpOn = record?.pumpOn ?: false,
                ledOn = record?.ledOn ?: false,
                autoMode = record?.autoMode ?: control?.autoMode ?: false
            )

            SectionTitle(
                title = "Control remoto",
                subtitle = "Órdenes enviadas a través de Supabase"
            )

            ControlCard(
                autoMode = control?.autoMode ?: false,
                fanTarget = control?.fanTarget ?: false,
                ledTarget = control?.ledTarget ?: false,
                pumpRequest = control?.pumpRequest ?: 0L,
                pumpDurationMs = control?.pumpDurationMs ?: 3000,
                soilHumidity = record?.soilHumidity,
                waterLevel = record?.waterLevel,
                isUpdating = uiState.isUpdatingControl,
                onAutoModeChange = onAutoModeChange,
                onFanTargetChange = onFanTargetChange,
                onLedTargetChange = onLedTargetChange,
                onPumpRequest = onPumpRequest
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SystemOverviewCard(
    online: Boolean,
    autoMode: Boolean,
    lastSeenAt: String,
    lastReadingAt: String,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Estado general",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (online) "Sistema conectado" else "Sistema sin conexión",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                StatusPill(
                    label = if (online) "ONLINE" else "OFFLINE",
                    emphasized = online
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OverviewValue(
                    modifier = Modifier.weight(1f),
                    title = "Modo",
                    value = if (autoMode) "Automático" else "Manual"
                )
                OverviewValue(
                    modifier = Modifier.weight(1f),
                    title = "Último ESP32",
                    value = lastSeenAt
                )
            }

            OverviewValue(
                modifier = Modifier.fillMaxWidth(),
                title = "Última telemetría",
                value = lastReadingAt
            )

            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(18.dp)
                            .height(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Actualizando...")
                } else {
                    Text("Actualizar datos")
                }
            }
        }
    }
}

@Composable
private fun OverviewValue(
    modifier: Modifier,
    title: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SensorMetrics(
    temperature: Double?,
    airHumidity: Double?,
    soilHumidity: Double?,
    lightLux: Double?,
    waterLevel: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Temperatura",
                value = temperature?.let { "${formatNumber(it)} °C" } ?: "--",
                detail = "Ambiente",
                progress = temperature?.let { (it / 50.0).toFloat() }
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Humedad aire",
                value = airHumidity?.let { "${formatNumber(it)} %" } ?: "--",
                detail = "Humedad relativa",
                progress = airHumidity?.let { (it / 100.0).toFloat() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Humedad suelo",
                value = soilHumidity?.let { "${formatNumber(it)} %" } ?: "--",
                detail = "Sustrato",
                progress = soilHumidity?.let { (it / 100.0).toFloat() }
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Iluminación",
                value = lightLux?.let { "${formatNumber(it)} lx" } ?: "--",
                detail = "Luz ambiental",
                progress = lightLux?.let { (it / 20000.0).toFloat() }
            )
        }

        WaterLevelCard(waterLevel)
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    detail: String,
    progress: Float?
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { (progress ?: 0f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WaterLevelCard(waterLevel: String?) {
    val normalized = waterLevel?.lowercase()
    val display = when (normalized) {
        "high" -> "Nivel alto"
        "medium" -> "Nivel medio"
        "low" -> "Nivel bajo"
        null -> "Sin lectura"
        else -> waterLevel
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Depósito de agua",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = display ?: "Sin lectura",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = when (normalized) {
                    "low" -> "Revisar depósito"
                    null -> "Esperando sensor"
                    else -> "Disponible"
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ActuatorStatusCard(
    fanOn: Boolean,
    pumpOn: Boolean,
    ledOn: Boolean,
    autoMode: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            StatusLine("Ventilador", if (fanOn) "ENCENDIDO" else "APAGADO")
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            StatusLine("Bomba de riego", if (pumpOn) "ENCENDIDA" else "APAGADA")
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            StatusLine("LED grow", if (ledOn) "ENCENDIDO" else "APAGADO")
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            StatusLine("Control", if (autoMode) "AUTOMÁTICO" else "MANUAL")
        }
    }
}

@Composable
private fun StatusLine(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
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
    soilHumidity: Double?,
    waterLevel: String?,
    isUpdating: Boolean,
    onAutoModeChange: (Boolean) -> Unit,
    onFanTargetChange: (Boolean) -> Unit,
    onLedTargetChange: (Boolean) -> Unit,
    onPumpRequest: () -> Unit
) {
    val irrigationStatus = irrigationSafetyText(soilHumidity, waterLevel)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Modo de operación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            ControlSwitchRow(
                title = "Control automático",
                subtitle = if (autoMode) {
                    "El ESP32 decide según las lecturas"
                } else {
                    "Los actuadores pueden controlarse manualmente"
                },
                checked = autoMode,
                enabled = !isUpdating,
                onCheckedChange = onAutoModeChange
            )

            HorizontalDivider()

            Text(
                text = "Actuadores",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            ControlSwitchRow(
                title = "Ventilador",
                subtitle = if (autoMode) "Gestionado automáticamente" else "Control manual",
                checked = fanTarget,
                enabled = !autoMode && !isUpdating,
                onCheckedChange = onFanTargetChange
            )

            ControlSwitchRow(
                title = "Iluminación grow",
                subtitle = if (autoMode) "Gestionada automáticamente" else "Control manual",
                checked = ledTarget,
                enabled = !autoMode && !isUpdating,
                onCheckedChange = onLedTargetChange
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Riego manual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = irrigationStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onPumpRequest,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating
                ) {
                    Text("Regar ahora · ${formatDuration(pumpDurationMs)}")
                }
                Text(
                    text = "Solicitudes enviadas: $pumpRequest",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isUpdating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(20.dp)
                            .height(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Enviando orden...")
                }
            }
        }
    }
}

@Composable
private fun ControlSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun StatusPill(label: String, emphasized: Boolean) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun EmptyTelemetryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Esperando telemetría",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Cuando el ESP32 vuelva a enviar datos, las lecturas aparecerán aquí automáticamente.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Se detectó un problema",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun InfoBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun irrigationSafetyText(soilHumidity: Double?, waterLevel: String?): String {
    return when {
        soilHumidity == null -> "Riego protegido: falta lectura válida de humedad del suelo."
        soilHumidity >= 60.0 -> "Riego protegido: el suelo ya tiene ${soilHumidity.roundToInt()}% de humedad."
        waterLevel?.lowercase() == "low" -> "Riego protegido: el depósito reporta nivel bajo."
        else -> "Condiciones válidas para solicitar un riego manual."
    }
}

private fun formatDuration(durationMs: Int): String {
    val seconds = durationMs / 1000.0
    return if (seconds % 1.0 == 0.0) {
        "${seconds.toInt()} s"
    } else {
        String.format(Locale.getDefault(), "%.1f s", seconds)
    }
}

private fun formatNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
}

private fun formatTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "Sin registro"

    return try {
        val normalized = normalizeSupabaseTimestamp(value)
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(normalized) ?: return value

        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(date)
    } catch (_: Exception) {
        value
            .replace("T", " ")
            .substringBefore("+")
            .substringBefore("Z")
    }
}

private fun normalizeSupabaseTimestamp(value: String): String {
    val zone = when {
        value.endsWith("Z") -> "Z"
        value.contains("+") -> "+" + value.substringAfter("+")
        value.drop(10).contains("-") -> "-" + value.substringAfterLast("-")
        else -> "Z"
    }

    val withoutZone = value
        .removeSuffix("Z")
        .substringBefore("+")
        .let { text ->
            if (text.drop(10).contains("-")) text.substringBeforeLast("-") else text
        }

    val base = withoutZone.substringBefore(".")
    val millis = withoutZone
        .substringAfter(".", "0")
        .padEnd(3, '0')
        .take(3)

    return "$base.$millis$zone"
}
