package com.example.ecosphere.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ecosphere.shared.ControlPolicy
import com.example.ecosphere.ui.icons.DashboardControlIcons
import com.example.ecosphere.ui.viewmodel.EcoSphereUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class DashboardDetail {
    CONNECTION,
    MODE,
    TEMPERATURE,
    AIR_HUMIDITY,
    SOIL_HUMIDITY,
    LIGHT,
    WATER_LEVEL,
    FAN,
    PUMP,
    GROW_LED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveDashboardScreen(
    uiState: EcoSphereUiState,
    onRefresh: () -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onFanPowerChange: (Int) -> Unit,
    onLedPowerChange: (Int) -> Unit,
    onPumpRequest: () -> Unit
) {
    val record = uiState.record
    val control = uiState.deviceControl
    val online = control?.isOnlineNow() == true
    var detail by remember { mutableStateOf<DashboardDetail?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun requestManualIrrigation(): Boolean {
        val decision = ControlPolicy.irrigationDecision(record?.soilHumidity, record?.waterLevel)
        if (!decision.allowed) {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(decision.message)
            }
            return false
        }

        onPumpRequest()
        return true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("EcoSphere", fontWeight = FontWeight.Bold)
                        Text(
                            "Sistema inteligente de microclima",
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
                lastSeenAt = prettyTimestamp(control?.lastSeenAt),
                lastReadingAt = prettyTimestamp(record?.createdAt),
                isLoading = uiState.isLoading,
                onRefresh = onRefresh,
                onConnectionClick = { detail = DashboardDetail.CONNECTION },
                onModeClick = { detail = DashboardDetail.MODE }
            )

            uiState.error?.let { MessageCard(it, true) }
            uiState.controlMessage?.let { MessageCard(it, false) }

            SectionTitle("Lecturas ambientales", "Toca cualquier tarjeta para abrir el detalle")

            if (record == null) {
                EmptyTelemetryCard()
            } else {
                SensorGrid(
                    temperature = record.temperature,
                    airHumidity = record.airHumidity,
                    soilHumidity = record.soilHumidity,
                    lightLux = record.lightLux,
                    waterLevel = record.waterLevel,
                    onOpen = { detail = it }
                )
            }

            SectionTitle("Estado del sistema", "Estado reportado por el ESP32")
            ActuatorStatus(
                fanOn = record?.fanOn == true,
                fanPower = record?.fanPower,
                pumpOn = record?.pumpOn == true,
                ledOn = record?.ledOn == true,
                ledPower = record?.ledPower,
                autoMode = record?.autoMode ?: control?.autoMode ?: false,
                onOpen = { detail = it }
            )

            SectionTitle("Control remoto", "Órdenes enviadas a través de Supabase")
            ControlCard(
                autoMode = control?.autoMode ?: false,
                fanPower = control?.fanPower ?: 0,
                ledPower = control?.ledPower ?: 0,
                pumpRequest = control?.pumpRequest ?: 0L,
                pumpDurationMs = control?.pumpDurationMs ?: ControlPolicy.PUMP_DURATION_MS,
                soilHumidity = record?.soilHumidity,
                waterLevel = record?.waterLevel,
                isUpdating = uiState.isUpdatingControl,
                onAutoModeChange = onAutoModeChange,
                onFanPowerChange = onFanPowerChange,
                onLedPowerChange = onLedPowerChange,
                onPumpRequest = { requestManualIrrigation() },
                onOpen = { detail = it }
            )

            Spacer(Modifier.height(12.dp))
        }
    }

    detail?.let { selected ->
        DashboardDetailDialog(
            detail = selected,
            uiState = uiState,
            onDismiss = { detail = null },
            onAutoModeChange = {
                onAutoModeChange(it)
                detail = null
            },
            onFanPowerChange = {
                onFanPowerChange(it)
                detail = null
            },
            onLedPowerChange = {
                onLedPowerChange(it)
                detail = null
            },
            onPumpRequest = {
                if (requestManualIrrigation()) {
                    detail = null
                }
            }
        )
    }
}

@Composable
private fun SystemOverviewCard(
    online: Boolean,
    autoMode: Boolean,
    lastSeenAt: String,
    lastReadingAt: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onConnectionClick: () -> Unit,
    onModeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Estado general", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (online) "Sistema conectado" else "Sistema sin conexión",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onConnectionClick) {
                    Icon(
                        if (online) DashboardControlIcons.Online else DashboardControlIcons.Offline,
                        contentDescription = if (online) "ONLINE" else "OFFLINE",
                        tint = DashboardControlIcons.Green
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewValue(
                    Modifier.weight(1f).clickable(onClick = onModeClick),
                    "Modo",
                    if (autoMode) "Automático" else "Manual"
                )
                OverviewValue(Modifier.weight(1f), "Último ESP32", lastSeenAt)
            }
            OverviewValue(Modifier.fillMaxWidth(), "Última telemetría", lastReadingAt)

            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
                Icon(
                    DashboardControlIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = DashboardControlIcons.Green
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isLoading) "Actualizando..." else "Actualizar datos")
            }
        }
    }
}

@Composable
private fun OverviewValue(modifier: Modifier, title: String, value: String) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .70f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SensorGrid(
    temperature: Double?,
    airHumidity: Double?,
    soilHumidity: Double?,
    lightLux: Double?,
    waterLevel: String?,
    onOpen: (DashboardDetail) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorCard(
                Modifier.weight(1f), DashboardControlIcons.Temperature, "Temperatura",
                temperature?.let { "${formatNumber(it)} °C" } ?: "--", "BME280",
                temperature?.let { (it / 50.0).toFloat() }
            ) { onOpen(DashboardDetail.TEMPERATURE) }
            SensorCard(
                Modifier.weight(1f), DashboardControlIcons.AirHumidity, "Humedad aire",
                airHumidity?.let { "${formatNumber(it)} %" } ?: "--", "BME280",
                airHumidity?.let { (it / 100.0).toFloat() }
            ) { onOpen(DashboardDetail.AIR_HUMIDITY) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorCard(
                Modifier.weight(1f), DashboardControlIcons.SoilHumidity, "Humedad suelo",
                soilHumidity?.let { "${formatNumber(it)} %" } ?: "--", "Sensor capacitivo",
                soilHumidity?.let { (it / 100.0).toFloat() }
            ) { onOpen(DashboardDetail.SOIL_HUMIDITY) }
            SensorCard(
                Modifier.weight(1f), DashboardControlIcons.Light, "Iluminación",
                lightLux?.let { "${formatNumber(it)} lx" } ?: "--", "BH1750",
                lightLux?.let { (it / 20000.0).toFloat() }
            ) { onOpen(DashboardDetail.LIGHT) }
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpen(DashboardDetail.WATER_LEVEL) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(DashboardControlIcons.WaterLevel, "Nivel de agua", Modifier.size(30.dp), tint = DashboardControlIcons.Green)
                Column(Modifier.weight(1f)) {
                    Text("Depósito de agua", style = MaterialTheme.typography.labelLarge)
                    Text(waterLevelDisplay(waterLevel), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text("Ver sensor", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SensorCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    source: String,
    progress: Float?,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, title, Modifier.size(28.dp), tint = DashboardControlIcons.Green)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { (progress ?: 0f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ActuatorStatus(
    fanOn: Boolean,
    fanPower: Int?,
    pumpOn: Boolean,
    ledOn: Boolean,
    ledPower: Int?,
    autoMode: Boolean,
    onOpen: (DashboardDetail) -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            StatusLine(DashboardControlIcons.Fan, "Ventilador", if (fanOn) "ENCENDIDO${fanPower?.let { " · $it %" } ?: ""}" else "APAGADO") { onOpen(DashboardDetail.FAN) }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            StatusLine(DashboardControlIcons.Pump, "Bomba de riego", if (pumpOn) "ENCENDIDA" else "APAGADA") { onOpen(DashboardDetail.PUMP) }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            StatusLine(DashboardControlIcons.GrowLed, "LED grow", if (ledOn) "ENCENDIDO${ledPower?.let { " · $it %" } ?: ""}" else "APAGADO") { onOpen(DashboardDetail.GROW_LED) }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            StatusLine(if (autoMode) DashboardControlIcons.AutoMode else DashboardControlIcons.ManualMode, "Control", if (autoMode) "AUTOMÁTICO" else "MANUAL") { onOpen(DashboardDetail.MODE) }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, title, Modifier.size(26.dp), tint = DashboardControlIcons.Green)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(value, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ControlCard(
    autoMode: Boolean,
    fanPower: Int,
    ledPower: Int,
    pumpRequest: Long,
    pumpDurationMs: Int,
    soilHumidity: Double?,
    waterLevel: String?,
    isUpdating: Boolean,
    onAutoModeChange: (Boolean) -> Unit,
    onFanPowerChange: (Int) -> Unit,
    onLedPowerChange: (Int) -> Unit,
    onPumpRequest: () -> Unit,
    onOpen: (DashboardDetail) -> Unit
) {
    var fanSlider by remember(fanPower) { mutableFloatStateOf(fanPower.toFloat()) }
    var ledSlider by remember(ledPower) { mutableFloatStateOf(ledPower.toFloat()) }
    val irrigationDecision = ControlPolicy.irrigationDecision(soilHumidity, waterLevel)
    val irrigation = irrigationSafety(soilHumidity, waterLevel)

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ControlHeader(
                if (autoMode) DashboardControlIcons.AutoMode else DashboardControlIcons.ManualMode,
                "Modo de operación"
            ) { onOpen(DashboardDetail.MODE) }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Control automático", fontWeight = FontWeight.Medium)
                    Text(
                        if (autoMode) "El ESP32 decide según las lecturas" else "Control manual habilitado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoMode, onCheckedChange = onAutoModeChange, enabled = !isUpdating)
            }

            HorizontalDivider()
            ControlHeader(DashboardControlIcons.Fan, "Ventilación") { onOpen(DashboardDetail.FAN) }
            PowerSlider("Potencia del ventilador", fanSlider, !autoMode && !isUpdating, { fanSlider = it }) {
                onFanPowerChange(fanSlider.roundToInt())
            }

            HorizontalDivider()
            ControlHeader(DashboardControlIcons.GrowLed, "Iluminación") { onOpen(DashboardDetail.GROW_LED) }
            PowerSlider("Intensidad LED grow", ledSlider, !autoMode && !isUpdating, { ledSlider = it }) {
                onLedPowerChange(ledSlider.roundToInt())
            }

            HorizontalDivider()
            ControlHeader(DashboardControlIcons.Pump, "Riego manual") { onOpen(DashboardDetail.PUMP) }
            Text(irrigation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onPumpRequest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUpdating && irrigationDecision.allowed
            ) {
                Icon(DashboardControlIcons.Pump, null, Modifier.size(20.dp), tint = DashboardControlIcons.Green)
                Spacer(Modifier.width(8.dp))
                Text("Regar ahora · ${durationLabel(pumpDurationMs)}")
            }
            Text("Solicitudes enviadas: $pumpRequest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (isUpdating) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Enviando orden...")
                }
            }
        }
    }
}

@Composable
private fun ControlHeader(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, title, Modifier.size(26.dp), tint = DashboardControlIcons.Green)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PowerSlider(
    title: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.Medium)
            Text("${value.roundToInt()} %", fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(0f, 100f),
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            steps = 19,
            enabled = enabled,
            onValueChangeFinished = onFinished
        )
        Text(
            if (enabled) "Ajuste manual PWM de 0 a 100 %" else "Gestionado por el modo automático",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardDetailDialog(
    detail: DashboardDetail,
    uiState: EcoSphereUiState,
    onDismiss: () -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onFanPowerChange: (Int) -> Unit,
    onLedPowerChange: (Int) -> Unit,
    onPumpRequest: () -> Unit
) {
    val record = uiState.record
    val control = uiState.deviceControl
    val online = control?.isOnlineNow() == true
    val autoMode = control?.autoMode == true
    var fanPower by remember(detail, control?.fanPower) { mutableFloatStateOf((control?.fanPower ?: 0).toFloat()) }
    var ledPower by remember(detail, control?.ledPower) { mutableFloatStateOf((control?.ledPower ?: 0).toFloat()) }

    val icon = when (detail) {
        DashboardDetail.CONNECTION -> if (online) DashboardControlIcons.Online else DashboardControlIcons.Offline
        DashboardDetail.MODE -> if (autoMode) DashboardControlIcons.AutoMode else DashboardControlIcons.ManualMode
        DashboardDetail.TEMPERATURE -> DashboardControlIcons.Temperature
        DashboardDetail.AIR_HUMIDITY -> DashboardControlIcons.AirHumidity
        DashboardDetail.SOIL_HUMIDITY -> DashboardControlIcons.SoilHumidity
        DashboardDetail.LIGHT -> DashboardControlIcons.Light
        DashboardDetail.WATER_LEVEL -> DashboardControlIcons.WaterLevel
        DashboardDetail.FAN -> DashboardControlIcons.Fan
        DashboardDetail.PUMP -> DashboardControlIcons.Pump
        DashboardDetail.GROW_LED -> DashboardControlIcons.GrowLed
    }

    val title = when (detail) {
        DashboardDetail.CONNECTION -> "ESP32 / conexión"
        DashboardDetail.MODE -> "Modo de operación"
        DashboardDetail.TEMPERATURE -> "Temperatura"
        DashboardDetail.AIR_HUMIDITY -> "Humedad del aire"
        DashboardDetail.SOIL_HUMIDITY -> "Humedad del suelo"
        DashboardDetail.LIGHT -> "Iluminación"
        DashboardDetail.WATER_LEVEL -> "Nivel de agua"
        DashboardDetail.FAN -> "Ventilador"
        DashboardDetail.PUMP -> "Bomba de riego"
        DashboardDetail.GROW_LED -> "LED grow"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, title, tint = DashboardControlIcons.Green) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (detail) {
                    DashboardDetail.CONNECTION -> {
                        DetailPair("Estado", if (online) "ONLINE" else "OFFLINE")
                        DetailPair("Heartbeat", control?.heartbeatSeq?.toString() ?: "Sin dato")
                        DetailPair("Última conexión", prettyTimestamp(control?.lastSeenAt))
                        Text("El estado se considera offline si no llega heartbeat reciente.", style = MaterialTheme.typography.bodySmall)
                    }
                    DashboardDetail.MODE -> {
                        DetailPair("Configurado", if (autoMode) "AUTOMÁTICO" else "MANUAL")
                        DetailPair("Reportado por ESP32", when (record?.autoMode) { true -> "AUTOMÁTICO"; false -> "MANUAL"; null -> "Sin dato" })
                        Text(if (autoMode) "Los sliders manuales quedan bloqueados." else "Puedes ajustar ventilador y LED manualmente.")
                    }
                    DashboardDetail.TEMPERATURE -> {
                        DetailPair("Valor", record?.temperature?.let { "${formatNumber(it)} °C" } ?: "Sin lectura")
                        DetailPair("Sensor", "BME280")
                        DetailPair("Última lectura", prettyTimestamp(record?.createdAt))
                    }
                    DashboardDetail.AIR_HUMIDITY -> {
                        DetailPair("Valor", record?.airHumidity?.let { "${formatNumber(it)} %" } ?: "Sin lectura")
                        DetailPair("Sensor", "BME280")
                        DetailPair("Última lectura", prettyTimestamp(record?.createdAt))
                    }
                    DashboardDetail.SOIL_HUMIDITY -> {
                        DetailPair("Valor", record?.soilHumidity?.let { "${formatNumber(it)} %" } ?: "Sin lectura")
                        DetailPair("Riego", irrigationSafety(record?.soilHumidity, record?.waterLevel))
                        DetailPair("Referencia", "35–<60 % aceptable")
                    }
                    DashboardDetail.LIGHT -> {
                        DetailPair("Valor", record?.lightLux?.let { "${formatNumber(it)} lx" } ?: "Sin lectura")
                        DetailPair("Sensor", "BH1750")
                        DetailPair("Última lectura", prettyTimestamp(record?.createdAt))
                    }
                    DashboardDetail.WATER_LEVEL -> {
                        DetailPair("Nivel", waterLevelDisplay(record?.waterLevel))
                        DetailPair("Sensor", "Nivel de agua horizontal")
                        DetailPair("Entrada", "GPIO32")
                        Text("El riego se bloquea con nivel bajo o una lectura desconocida.")
                    }
                    DashboardDetail.FAN -> {
                        DetailPair("Orden actual", "${control?.fanPower ?: 0} %")
                        DetailPair("Reportado", if (record?.fanOn == true) "ENCENDIDO · ${record.fanPower ?: 0} %" else "APAGADO")
                        Slider(fanPower, { fanPower = it }, valueRange = 0f..100f, steps = 19, enabled = !autoMode && !uiState.isUpdatingControl)
                        Text("Nueva potencia: ${fanPower.roundToInt()} %")
                    }
                    DashboardDetail.GROW_LED -> {
                        DetailPair("Orden actual", "${control?.ledPower ?: 0} %")
                        DetailPair("Reportado", if (record?.ledOn == true) "ENCENDIDO · ${record.ledPower ?: 0} %" else "APAGADO")
                        DetailPair("Alimentación", "Configurable según el LED instalado")
                        Slider(ledPower, { ledPower = it }, valueRange = 0f..100f, steps = 19, enabled = !autoMode && !uiState.isUpdatingControl)
                        Text("Nueva intensidad: ${ledPower.roundToInt()} %")
                    }
                    DashboardDetail.PUMP -> {
                        DetailPair("Estado", if (record?.pumpOn == true) "ENCENDIDA" else "APAGADA")
                        DetailPair(
                            "Duración",
                            durationLabel(control?.pumpDurationMs ?: ControlPolicy.PUMP_DURATION_MS)
                        )
                        DetailPair("Protección", irrigationSafety(record?.soilHumidity, record?.waterLevel))
                    }
                }
            }
        },
        confirmButton = {
            when (detail) {
                DashboardDetail.MODE -> TextButton(onClick = { onAutoModeChange(!autoMode) }, enabled = !uiState.isUpdatingControl) {
                    Text(if (autoMode) "Cambiar a manual" else "Cambiar a automático")
                }
                DashboardDetail.FAN -> TextButton(onClick = { onFanPowerChange(fanPower.roundToInt()) }, enabled = !autoMode && !uiState.isUpdatingControl) { Text("Aplicar") }
                DashboardDetail.GROW_LED -> TextButton(onClick = { onLedPowerChange(ledPower.roundToInt()) }, enabled = !autoMode && !uiState.isUpdatingControl) { Text("Aplicar") }
                DashboardDetail.PUMP -> TextButton(
                    onClick = onPumpRequest,
                    enabled = !uiState.isUpdatingControl && ControlPolicy.irrigationDecision(
                        record?.soilHumidity,
                        record?.waterLevel
                    ).allowed
                ) { Text("Regar ahora") }
                else -> TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        dismissButton = {
            if (detail == DashboardDetail.MODE || detail == DashboardDetail.FAN || detail == DashboardDetail.GROW_LED || detail == DashboardDetail.PUMP) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun DetailPair(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun MessageCard(message: String, error: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(message, Modifier.padding(16.dp))
    }
}

@Composable
private fun EmptyTelemetryCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Esperando telemetría", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Cuando el ESP32 vuelva a enviar datos, las lecturas aparecerán aquí automáticamente.",
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun irrigationSafety(soilHumidity: Double?, waterLevel: String?): String =
    ControlPolicy.irrigationStatus(soilHumidity, waterLevel)

private fun waterLevelDisplay(value: String?): String = ControlPolicy.waterLevelLabel(value)

private fun durationLabel(ms: Int): String = if (ms % 1000 == 0) "${ms / 1000} s" else "$ms ms"

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else "%.1f".format(value)

private fun prettyTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "Sin registro"
    return value.replace("T", " ").substringBefore("+").substringBefore("Z").take(19)
}
