package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.model.SensorRecord
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
                    Column {
                        Text("Diagnóstico del sistema", fontWeight = FontWeight.Bold)
                        Text(
                            "Estado técnico de sensores y actuadores",
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
                    DiagnosticSummaryRow("ESP32", if (online) "ONLINE" else "OFFLINE")
                    DiagnosticSummaryRow("Telemetría", if (telemetryFresh) "ACTUAL" else "NO ACTUAL")
                    DiagnosticSummaryRow("Fallas detectadas", errors.toString())
                    DiagnosticSummaryRow("Elementos a revisar", warnings.toString())
                }
            }

            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Actualizar diagnóstico")
            }

            entries.forEach { entry ->
                DiagnosticCard(entry)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Límite del diagnóstico actual", fontWeight = FontWeight.Bold)
                    Text(
                        "EcoSphere puede detectar pérdida de comunicación, datos inválidos y diferencias entre una orden y lo que reporta el ESP32. Para afirmar que un LED está quemado, que un cable de potencia está abierto o que falta energía eléctrica, se necesita medir tensión y corriente físicamente en cada rama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun DiagnosticCard(entry: DiagnosticEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = when (entry.level) {
                        DiagnosticLevel.OK -> MaterialTheme.colorScheme.primaryContainer
                        DiagnosticLevel.WARNING -> MaterialTheme.colorScheme.secondaryContainer
                        DiagnosticLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
                        DiagnosticLevel.INFO -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = entry.level.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildDiagnostics(
    record: SensorRecord?,
    control: DeviceControl?
): List<DiagnosticEntry> {
    val result = mutableListOf<DiagnosticEntry>()
    val online = control?.isOnlineNow() == true
    val ageMs = telemetryAgeMs(record?.createdAt)
    val fresh = ageMs != null && ageMs in 0..15_000L

    result += if (online) {
        DiagnosticEntry("ESP32 / comunicación", DiagnosticLevel.OK, "Heartbeat recibido dentro de la ventana de 30 segundos.")
    } else {
        DiagnosticEntry("ESP32 / comunicación", DiagnosticLevel.ERROR, "No hay heartbeat reciente. El hardware está desconectado, sin red o el firmware no está reportando.")
    }

    result += when {
        ageMs == null -> DiagnosticEntry("Telemetría", DiagnosticLevel.ERROR, "No existe una lectura con fecha válida.")
        fresh -> DiagnosticEntry("Telemetría", DiagnosticLevel.OK, "La lectura más reciente tiene ${ageMs / 1000} s de antigüedad.")
        else -> DiagnosticEntry("Telemetría", DiagnosticLevel.WARNING, "La última lectura tiene ${ageMs / 1000} s de antigüedad; no se usa como evidencia de estado físico actual.")
    }

    result += sensorPairDiagnostic(
        title = "BME280 · temperatura / humedad de aire",
        fresh = fresh,
        valuesPresent = record?.temperature != null && record.airHumidity != null,
        valuesPlausible = record?.temperature?.let { it in -10.0..60.0 } == true &&
            record.airHumidity?.let { it in 0.0..100.0 } == true,
        failureDetail = "Sin datos válidos. Revisar alimentación 3.3 V, GND, SDA GPIO21 y SCL GPIO22."
    )

    result += sensorDiagnostic(
        title = "BH1750 · iluminación",
        fresh = fresh,
        present = record?.lightLux != null,
        plausible = record?.lightLux?.let { it in 0.0..120000.0 } == true,
        failureDetail = "Sin lectura válida de lux. Revisar alimentación e I2C compartido."
    )

    result += sensorDiagnostic(
        title = "Sensor de humedad del suelo",
        fresh = fresh,
        present = record?.soilHumidity != null,
        plausible = record?.soilHumidity?.let { it in 0.0..100.0 } == true,
        failureDetail = "Lectura ausente o fuera de 0–100 %. Revisar AO, GPIO34, 3.3 V, GND y calibración."
    )

    val waterLevel = record?.waterLevel?.lowercase()
    result += sensorDiagnostic(
        title = "Sensores de nivel de agua",
        fresh = fresh,
        present = waterLevel != null,
        plausible = waterLevel in setOf("low", "medium", "high"),
        failureDetail = "Estado de nivel inválido. Revisar GPIO27, GPIO32, GND y cableado de los sensores."
    )

    result += actuatorDiagnostic(
        title = "Ventilador",
        fresh = fresh && online,
        requestedPower = control?.fanPower ?: 0,
        reportedOn = record?.fanOn,
        reportedPower = record?.fanPower,
        electricalHint = "Revisar GPIO25, MOSFET, alimentación de 12 V, cableado y ventilador."
    )

    result += actuatorDiagnostic(
        title = "LED grow",
        fresh = fresh && online,
        requestedPower = control?.ledPower ?: 0,
        reportedOn = record?.ledOn,
        reportedPower = record?.ledPower,
        electricalHint = "Revisar GPIO33, MOSFET, salida de 5 V del LM2596, cableado y LED grow."
    )

    result += if (!fresh || !online) {
        DiagnosticEntry("Bomba de riego", DiagnosticLevel.INFO, "Sin telemetría actual no se puede confirmar el estado físico de la bomba.")
    } else {
        DiagnosticEntry(
            "Bomba de riego",
            DiagnosticLevel.OK,
            if (record?.pumpOn == true) "El ESP32 reporta la bomba encendida." else "El ESP32 reporta la bomba apagada; queda protegida por humedad y nivel de agua."
        )
    }

    result += DiagnosticEntry(
        "Alimentación eléctrica",
        DiagnosticLevel.INFO,
        "Todavía no hay telemetría de voltaje/corriente. No se puede distinguir con certeza entre componente quemado, cable abierto o tensión insuficiente sin instrumentación eléctrica."
    )

    return result
}

private fun sensorDiagnostic(
    title: String,
    fresh: Boolean,
    present: Boolean,
    plausible: Boolean,
    failureDetail: String
): DiagnosticEntry {
    if (!fresh) {
        return DiagnosticEntry(title, DiagnosticLevel.INFO, "Sin telemetría actual; no se marca como falla hasta recibir una lectura nueva.")
    }
    if (!present) {
        return DiagnosticEntry(title, DiagnosticLevel.ERROR, failureDetail)
    }
    if (!plausible) {
        return DiagnosticEntry(title, DiagnosticLevel.WARNING, "El sensor responde, pero su valor está fuera del rango esperado. Revisar calibración o cableado.")
    }
    return DiagnosticEntry(title, DiagnosticLevel.OK, "Lectura presente y dentro de un rango físicamente plausible.")
}

private fun sensorPairDiagnostic(
    title: String,
    fresh: Boolean,
    valuesPresent: Boolean,
    valuesPlausible: Boolean,
    failureDetail: String
): DiagnosticEntry {
    return sensorDiagnostic(title, fresh, valuesPresent, valuesPlausible, failureDetail)
}

private fun actuatorDiagnostic(
    title: String,
    fresh: Boolean,
    requestedPower: Int,
    reportedOn: Boolean?,
    reportedPower: Int?,
    electricalHint: String
): DiagnosticEntry {
    if (!fresh) {
        return DiagnosticEntry(title, DiagnosticLevel.INFO, "ESP32 o telemetría sin estado actual; no se puede confirmar el actuador.")
    }

    if (requestedPower > 0 && reportedOn != true) {
        return DiagnosticEntry(
            title,
            DiagnosticLevel.ERROR,
            "Orden solicitada: $requestedPower %, pero el ESP32 reporta apagado. $electricalHint"
        )
    }

    if (requestedPower == 0 && reportedOn == true) {
        return DiagnosticEntry(
            title,
            DiagnosticLevel.WARNING,
            "La orden es 0 %, pero el ESP32 reporta el actuador encendido. Revisar MOSFET, lógica de control y firmware."
        )
    }

    if (requestedPower > 0 && reportedPower != null && abs(reportedPower - requestedPower) > 10) {
        return DiagnosticEntry(
            title,
            DiagnosticLevel.WARNING,
            "Orden $requestedPower %, reporte $reportedPower %. Revisar sincronización de PWM y firmware."
        )
    }

    return DiagnosticEntry(
        title,
        DiagnosticLevel.OK,
        if (requestedPower > 0) "Orden y reporte del ESP32 coinciden aproximadamente en $requestedPower %." else "Ordenado apagado y reportado apagado."
    )
}

private fun telemetryAgeMs(value: String?): Long? {
    if (value.isNullOrBlank()) return null

    return try {
        val normalized = normalizeDiagnosticTimestamp(value)
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val millis = parser.parse(normalized)?.time ?: return null
        System.currentTimeMillis() - millis
    } catch (_: Exception) {
        null
    }
}

private fun normalizeDiagnosticTimestamp(value: String): String {
    val noZone = value.substringBefore("+").substringBefore("Z")
    val base = noZone.substringBefore(".")
    val fraction = noZone.substringAfter(".", "0").padEnd(3, '0').take(3)
    return "$base.$fraction"
}
