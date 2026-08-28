package com.example.ecosphere.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.ecosphere.data.model.ControlAuditEntry
import com.example.ecosphere.ui.icons.EcoSphereIcons
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlAuditScreen(
    entries: List<ControlAuditEntry>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
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
                            Text("Registro de actividad", fontWeight = FontWeight.Bold)
                            Text(
                                "Acceso exclusivo del administrador",
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "Cambios de control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Muestra quién cambió el modo, el ventilador, el LED grow o activó la bomba.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
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
                    if (isLoading) "Cargando..." else "Actualizar actividad",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!isLoading && entries.isEmpty() && error == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "Todavía no hay cambios realizados por usuarios.",
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        ControlAuditCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlAuditCard(entry: ControlAuditEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.actorUsername,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (entry.actorRole == "admin") "Administrador" else "Operador",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = formatAuditTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entry.autoModeBefore != null && entry.autoModeAfter != null) {
                AuditChangeRow(
                    icon = if (entry.autoModeAfter) EcoSphereIcons.AutoMode else EcoSphereIcons.ManualMode,
                    label = "Modo",
                    value = "${modeLabel(entry.autoModeBefore)} → ${modeLabel(entry.autoModeAfter)}"
                )
            }

            if (entry.fanPowerBefore != null && entry.fanPowerAfter != null) {
                AuditChangeRow(
                    icon = EcoSphereIcons.Fan,
                    label = "Ventilador",
                    value = "${entry.fanPowerBefore} % → ${entry.fanPowerAfter} %"
                )
            }

            if (entry.ledPowerBefore != null && entry.ledPowerAfter != null) {
                AuditChangeRow(
                    icon = EcoSphereIcons.GrowLed,
                    label = "LED grow",
                    value = "${entry.ledPowerBefore} % → ${entry.ledPowerAfter} %"
                )
            }

            if (entry.pumpRequested) {
                val duration = entry.pumpDurationMs?.let { " · ${it / 1000.0} s" }.orEmpty()
                AuditChangeRow(
                    icon = EcoSphereIcons.Pump,
                    label = "Bomba",
                    value = "Riego activado$duration"
                )
            }
        }
    }
}

@Composable
private fun AuditChangeRow(icon: ImageVector, label: String, value: String) {
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

private fun modeLabel(enabled: Boolean): String = if (enabled) "Automático" else "Manual"

private fun formatAuditTimestamp(value: String): String {
    return try {
        val noZone = value.substringBefore("+").substringBefore("Z")
        val base = noZone.substringBefore(".")
        val fraction = noZone.substringAfter(".", "0").padEnd(3, '0').take(3)
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val output = SimpleDateFormat("dd/MM/yyyy\nHH:mm:ss", Locale.getDefault())
        parser.parse("$base.$fraction")?.let(output::format) ?: value
    } catch (_: Exception) {
        value
    }
}
