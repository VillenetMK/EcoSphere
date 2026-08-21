package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.model.SensorRecord
import com.example.ecosphere.ui.icons.EcoSphereIcons
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private enum class DiagnosticLevel(val label: String) {
    OK("OK"),
    WARNING("REVISAR"),
    ERROR("FALLA"),
    INFO("SIN CONFIRMAR")
}

private data class DiagnosticEntry(
    val title: String,
    val level: DiagnosticLevel,
    val detail: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    record: SensorRecord?,
    control: DeviceControl?,
    onRefresh: () -> Unit
) {
    val entries = buildDiagnostics(record, control)
    val errors = entries.count { it.level == DiagnosticLevel.ERROR }
    val warnings = entries.count { it.level == DiagnosticLevel.WARNING }
    val online = control?.isOnlineNow() == true
    val telemetryFresh = telemetryAgeMs(record?.createdAt)?.let { it in 0..15_000L } == true

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
                            imageVector = EcoSphereIcons.Diagnostics,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text("Diagnóstico del sistema", fontWeight = FontWeight.Bold)
                            Text(
                                "Estado técnico de sensores y actuadores",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Resumen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    SummaryRow(EcoSphereIcons.Esp32, "ESP32", if (online) "ONLINE" else "OFFLINE")
                    SummaryRow(EcoSphereIcons.Cloud, "Telemetría", if (telemetryFresh) "ACTUAL" else "NO ACTUAL")
                    SummaryRow(EcoSphereIcons.Error, "Fallas detectadas", errors.toString())
                    SummaryRow(EcoSphereIcons.Warning, "Elementos a revisar", warnings.toString())
                }
            }

            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = EcoSphereIcons.Refresh,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp)
                )
                Text("Actualizar diagnóstico", modifier = Modifier.padding(start = 8.dp))
            }

            entries.forEach { DiagnosticCard(it) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = EcoSphereIcons.Info,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Límite del diagnóstico actual", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "La app puede detectar pérdida de comunicación, lecturas inválidas y diferencias entre una orden y el reporte del ESP32. Para confirmar un componente quemado, un cable de potencia abierto o energía insuficiente se necesita medir tensión y corriente físicamente en esa rama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String) {
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
            Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun DiagnosticCard(entry: DiagnosticEntry) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        imageVector = diagnosticIcon(entry.title, entry.level),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = when (entry.level) {
                        DiagnosticLevel.OK -> MaterialTheme.colorScheme.primaryContainer
                        DiagnosticLevel.WARNING -> MaterialTheme.colorScheme.secondaryContainer
                        DiagnosticLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
                        DiagnosticLevel.INFO -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = levelIcon(entry.level),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            entry.level.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                entry.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun diagnosticIcon(title: String, level: DiagnosticLevel): ImageVector {
    return when {
        title.startsWith("ESP32") -> EcoSphereIcons.Esp32
        title.startsWith("Telemetría") -> EcoSphereIcons.Cloud
        title.startsWith("BME280") -> EcoSphereIcons.Temperature
        title.startsWith("BH1750") -> EcoSphereIcons.Light
        title.startsWith("Sensor de humedad") -> EcoSphereIcons.SoilHumidity
        title.startsWith("Sensor de nivel") -> EcoSphereIcons.WaterLevel
        title.startsWith("Ventilador") -> EcoSphereIcons.Fan
        title.startsWith("LED grow") -> EcoSphereIcons.GrowLed
        title.startsWith("Bomba") -> EcoSphereIcons.Pump
        title.startsWith("Alimentación") -> EcoSphereIcons.Power
        else -> levelIcon(level)
    }
}

private fun levelIcon(level: DiagnosticLevel): ImageVector {
    return when (level) {
        DiagnosticLevel.OK -> EcoSphereIcons.Ok
        DiagnosticLevel.WARNING -> EcoSphereIcons.Warning
        DiagnosticLevel.ERROR -> EcoSphereIcons.Error
        DiagnosticLevel.INFO -> EcoSphereIcons.Info
    }
}

private fun buildDiagnostics(record: SensorRecord?, control: DeviceControl?): List<DiagnosticEntry> {
    val entries = mutableListOf<DiagnosticEntry>()
    val online = control?.isOnlineNow() == true
    val ageMs = telemetryAgeMs(record?.createdAt)
    val fresh = ageMs?.let { it in 0..15_000L } == true

    entries += if (online) {
        DiagnosticEntry(
            "ESP32 / comunicación",
            DiagnosticLevel.OK,
            "Heartbeat recibido dentro de la ventana de 30 segundos."
        )
    } else {
        DiagnosticEntry(
            "ESP32 / comunicación",
            DiagnosticLevel.ERROR,
            "Sin heartbeat reciente: hardware desconectado, sin red o firmware sin reporte."
        )
    }

    entries += when {
        ageMs == null -> DiagnosticEntry(
            "Telemetría",
            DiagnosticLevel.ERROR,
            "No existe una lectura con fecha válida."
        )
        fresh -> DiagnosticEntry(
            "Telemetría",
            DiagnosticLevel.OK,
            "Última lectura hace ${ageMs / 1000} s."
        )
        else -> DiagnosticEntry(
            "Telemetría",
            DiagnosticLevel.WARNING,
            "La lectura tiene ${ageMs / 1000} s de antigüedad y no se considera estado físico actual."
        )
    }

    val temperature = record?.temperature
    val airHumidity = record?.airHumidity
    entries += sensorStatus(
        "BME280 · temperatura / humedad",
        fresh,
        temperature != null && airHumidity != null,
        temperature?.let { it in -10.0..60.0 } == true && airHumidity?.let { it in 0.0..100.0 } == true,
        "Revisar 3.3 V, GND, SDA GPIO21 y SCL GPIO22."
    )

    val lightLux = record?.lightLux
    entries += sensorStatus(
        "BH1750 · iluminación",
        fresh,
        lightLux != null,
        lightLux?.let { it in 0.0..120000.0 } == true,
        "Revisar alimentación e I2C compartido."
    )

    val soilHumidity = record?.soilHumidity
    entries += sensorStatus(
        "Sensor de humedad del suelo",
        fresh,
        soilHumidity != null,
        soilHumidity?.let { it in 0.0..100.0 } == true,
        "Revisar AO, GPIO34, 3.3 V, GND y calibración."
    )

    val waterLevel = record?.waterLevel?.lowercase()
    entries += sensorStatus(
        "Sensor de nivel de agua horizontal",
        fresh,
        waterLevel != null,
        waterLevel != null && waterLevel in setOf("low", "medium", "high"),
        "Revisar GPIO32, GND y cableado del sensor horizontal."
    )

    entries += actuatorStatus(
        "Ventilador",
        fresh && online,
        control?.fanPower ?: 0,
        record?.fanOn,
        record?.fanPower,
        "Revisar GPIO25, MOSFET, alimentación, cableado y ventilador."
    )

    entries += actuatorStatus(
        "LED grow",
        fresh && online,
        control?.ledPower ?: 0,
        record?.ledOn,
        record?.ledPower,
        "Revisar GPIO33, MOSFET, alimentación configurada para el LED instalado, cableado y LED grow."
    )

    entries += if (fresh && online) {
        DiagnosticEntry(
            "Bomba de riego",
            DiagnosticLevel.OK,
            if (record?.pumpOn == true) {
                "El ESP32 reporta la bomba encendida."
            } else {
                "El ESP32 reporta la bomba apagada; la lógica de humedad y nivel sigue activa."
            }
        )
    } else {
        DiagnosticEntry(
            "Bomba de riego",
            DiagnosticLevel.INFO,
            "Sin telemetría actual no se confirma el estado físico de la bomba."
        )
    }

    entries += DiagnosticEntry(
        "Alimentación eléctrica",
        DiagnosticLevel.INFO,
        "Sin sensor de tensión/corriente todavía. La app no fija aquí el voltaje del LED grow porque puede cambiarse por otro modelo; debe usarse la alimentación correspondiente al hardware instalado."
    )

    return entries
}

private fun sensorStatus(
    title: String,
    fresh: Boolean,
    present: Boolean,
    plausible: Boolean,
    hint: String
): DiagnosticEntry {
    if (!fresh) {
        return DiagnosticEntry(title, DiagnosticLevel.INFO, "Sin telemetría actual; no se marca como falla.")
    }
    if (!present) {
        return DiagnosticEntry(title, DiagnosticLevel.ERROR, "No hay lectura. $hint")
    }
    if (!plausible) {
        return DiagnosticEntry(title, DiagnosticLevel.WARNING, "El sensor responde con un valor fuera del rango esperado. $hint")
    }
    return DiagnosticEntry(title, DiagnosticLevel.OK, "Lectura presente y físicamente plausible.")
}

private fun actuatorStatus(
    title: String,
    fresh: Boolean,
    requestedPower: Int,
    reportedOn: Boolean?,
    reportedPower: Int?,
    hint: String
): DiagnosticEntry {
    if (!fresh) {
        return DiagnosticEntry(title, DiagnosticLevel.INFO, "Sin estado actual del ESP32; no se confirma el actuador.")
    }

    if (requestedPower > 0 && reportedOn != true) {
        return DiagnosticEntry(
            title,
            DiagnosticLevel.ERROR,
            "Orden $requestedPower %, pero el ESP32 reporta apagado. $hint"
        )
    }

    if (requestedPower == 0 && reportedOn == true) {
        return DiagnosticEntry(
            title,
            DiagnosticLevel.WARNING,
            "Orden 0 %, pero el ESP32 reporta encendido. Revisar MOSFET y firmware."
        )
    }

    if (requestedPower > 0 && reportedPower != null && abs(reportedPower - requestedPower) > 10) {
        return DiagnosticEntry(
            title,
            DiagnosticLevel.WARNING,
            "Orden $requestedPower %, reporte $reportedPower %. Revisar PWM y sincronización."
        )
    }

    return DiagnosticEntry(
        title,
        DiagnosticLevel.OK,
        if (requestedPower > 0) {
            "Orden y reporte coinciden aproximadamente en $requestedPower %."
        } else {
            "Ordenado y reportado apagado."
        }
    )
}

private fun telemetryAgeMs(value: String?): Long? {
    if (value.isNullOrBlank()) return null

    return try {
        val noZone = value.substringBefore("+").substringBefore("Z")
        val base = noZone.substringBefore(".")
        val fraction = noZone.substringAfter(".", "0").padEnd(3, '0').take(3)
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val millis = parser.parse("$base.$fraction")?.time ?: return null
        System.currentTimeMillis() - millis
    } catch (_: Exception) {
        null
    }
}
