package com.example.ecosphere.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.ecosphere.shared.ControlPolicy
import com.example.ecosphere.shared.ControllerAdminStatus
import com.example.ecosphere.shared.DeviceControl
import com.example.ecosphere.shared.EcoSphereConfig
import com.example.ecosphere.shared.SensorRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val EcoGreen = Color(0xFF66FF7A)
private val AppBackground = Color(0xFF0B0F0D)
private val AppSurface = Color(0xFF121915)
private val AppSurface2 = Color(0xFF17221C)
private val Muted = Color(0xFFAAB8AF)

private enum class Destination(val label: String) {
    DASHBOARD("Panel principal"),
    HISTORY("Registros históricos"),
    DIAGNOSTICS("Diagnóstico del sistema")
}

private class EcoSphereApi(
    private val accessToken: () -> String?
) {
    private val gson = Gson()
    private val client = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(12))
        .build()

    private fun requestBuilder(path: String): HttpRequest.Builder {
        val token = accessToken()?.takeIf(String::isNotBlank)
            ?: error("Tu sesión expiró. Inicia sesión nuevamente.")
        return HttpRequest.newBuilder()
            .uri(URI.create("${EcoSphereConfig.SUPABASE_URL}/$path"))
            .timeout(java.time.Duration.ofSeconds(15))
            .header("apikey", EcoSphereConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
    }

    private inline fun <reified T> parseList(json: String): List<T> {
        val type = object : TypeToken<List<T>>() {}.type
        return gson.fromJson(json, type)
    }

    suspend fun latestRecord(): SensorRecord? = withContext(Dispatchers.IO) {
        val req = requestBuilder("rest/v1/sensor_records?select=*&order=created_at.desc&limit=1").GET().build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        require(res.statusCode() in 200..299) { "HTTP ${res.statusCode()}: ${res.body()}" }
        parseList<SensorRecord>(res.body()).firstOrNull()
    }

    suspend fun deviceControl(): DeviceControl? = withContext(Dispatchers.IO) {
        val req = requestBuilder("rest/v1/device_control?id=eq.1&select=*").GET().build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        require(res.statusCode() in 200..299) { "HTTP ${res.statusCode()}: ${res.body()}" }
        parseList<DeviceControl>(res.body()).firstOrNull()
    }

    suspend fun history(limit: Int = 200): List<SensorRecord> = withContext(Dispatchers.IO) {
        val req = requestBuilder("rest/v1/sensor_records?select=*&order=created_at.desc&limit=$limit").GET().build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        require(res.statusCode() in 200..299) { "HTTP ${res.statusCode()}: ${res.body()}" }
        parseList<SensorRecord>(res.body())
    }

    suspend fun patchControl(body: Map<String, Any>): DeviceControl? = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val req = requestBuilder("rest/v1/device_control?id=eq.1")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        require(res.statusCode() in 200..299) { "HTTP ${res.statusCode()}: ${res.body()}" }
        parseList<DeviceControl>(res.body()).firstOrNull()
    }

    suspend fun controllerAdminStatus(): ControllerAdminStatus? = withContext(Dispatchers.IO) {
        val req = requestBuilder("rest/v1/rpc/controller_admin_status")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        require(res.statusCode() in 200..299) { "HTTP ${res.statusCode()}: ${res.body()}" }
        parseList<ControllerAdminStatus>(res.body()).firstOrNull()
    }

    suspend fun replaceActiveController(pairingCode: String): ControllerAdminStatus? = withContext(Dispatchers.IO) {
        val json = gson.toJson(mapOf("p_pairing_code" to pairingCode.trim()))
        val req = requestBuilder("rest/v1/rpc/replace_active_controller")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        require(res.statusCode() in 200..299) { "HTTP ${res.statusCode()}: ${res.body()}" }
        parseList<ControllerAdminStatus>(res.body()).firstOrNull()
    }

    suspend fun setAutoMode(enabled: Boolean) = patchControl(mapOf("auto_mode" to enabled))

    suspend fun setFanPower(power: Int): DeviceControl? {
        val safePower = ControlPolicy.clampPower(power)
        return patchControl(mapOf("fan_power" to safePower, "fan_target" to (safePower > 0)))
    }

    suspend fun setLedPower(power: Int): DeviceControl? {
        val safePower = ControlPolicy.clampPower(power)
        return patchControl(mapOf("led_power" to safePower, "led_target" to (safePower > 0)))
    }

    suspend fun requestPump(
        currentRequest: Long,
        durationMs: Int = ControlPolicy.PUMP_DURATION_MS
    ) = patchControl(
        mapOf("pump_request" to currentRequest + 1, "pump_duration_ms" to durationMs)
    )
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "EcoSphere",
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = EcoGreen,
                onPrimary = Color(0xFF061108),
                background = AppBackground,
                onBackground = Color.White,
                surface = AppSurface,
                onSurface = Color.White,
                surfaceVariant = AppSurface2,
                onSurfaceVariant = Muted
            )
        ) {
            val authController = remember { DesktopAuthController() }
            val authScope = rememberCoroutineScope()
            val authState = authController.state

            LaunchedEffect(Unit) {
                authController.initialize()
            }

            if (authState.page == DesktopAuthPage.APP) {
                EcoSphereDesktopApp(
                    accessToken = authController::accessToken,
                    profileName = authState.profile?.fullName.orEmpty(),
                    profileRole = authState.profile?.role.orEmpty(),
                    onSignOut = { authScope.launch { authController.signOut() } }
                )
            } else {
                DesktopAuthScreen(
                    state = authState,
                    onShowLogin = authController::showLogin,
                    onShowRegister = authController::showRegister,
                    onSignIn = { identifier, password ->
                        authScope.launch { authController.signIn(identifier, password) }
                    },
                    onRegister = { username, firstName, lastName, dni, phone, email, password, confirmation ->
                        authScope.launch {
                            authController.registerWithEmail(
                                username,
                                firstName,
                                lastName,
                                dni,
                                phone,
                                email,
                                password,
                                confirmation
                            )
                        }
                    },
                    onOAuth = { provider, registration, username, firstName, lastName, dni, phone, email ->
                        authScope.launch {
                            authController.startOAuth(
                                provider,
                                registration,
                                username,
                                firstName,
                                lastName,
                                dni,
                                phone,
                                email
                            )
                        }
                    },
                    onVerifyMfa = { code -> authScope.launch { authController.verifyMfa(code) } },
                    onSignOut = { authScope.launch { authController.signOut() } }
                )
            }
        }
    }
}

@Composable
private fun EcoSphereDesktopApp(
    accessToken: () -> String?,
    profileName: String,
    profileRole: String,
    onSignOut: () -> Unit
) {
    val api = remember { EcoSphereApi(accessToken) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var destination by remember { mutableStateOf(Destination.DASHBOARD) }
    var record by remember { mutableStateOf<SensorRecord?>(null) }
    var control by remember { mutableStateOf<DeviceControl?>(null) }
    var history by remember { mutableStateOf<List<SensorRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var actionBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var controllerStatus by remember { mutableStateOf<ControllerAdminStatus?>(null) }
    var controllerBusy by remember { mutableStateOf(false) }
    var controllerMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh(loadHistory: Boolean = false) {
        try {
            val latest = api.latestRecord()
            val device = api.deviceControl()
            record = latest
            control = device
            if (loadHistory) history = api.history()
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Error de conexión"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(2_000)
            refresh(destination == Destination.HISTORY)
        }
    }

    LaunchedEffect(destination) {
        if (destination == Destination.HISTORY) refresh(loadHistory = true)
        if (destination == Destination.DIAGNOSTICS && profileRole == "admin") {
            try {
                controllerStatus = api.controllerAdminStatus()
                controllerMessage = null
            } catch (e: Exception) {
                controllerMessage = e.message ?: "No se pudo consultar el controlador"
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppBackground
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            NavigationPane(
                destination = destination,
                profileName = profileName,
                profileRole = profileRole,
                onSignOut = onSignOut,
                onDestination = { destination = it }
            )

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (destination) {
                    Destination.DASHBOARD -> Dashboard(
                        record = record,
                        control = control,
                        loading = loading,
                        actionBusy = actionBusy,
                        error = error,
                        onRefresh = { scope.launch { loading = true; refresh() } },
                        onAutoMode = { enabled ->
                            scope.launch {
                                actionBusy = true
                                try { api.setAutoMode(enabled); refresh() }
                                catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Error") }
                                finally { actionBusy = false }
                            }
                        },
                        onFanPower = { power ->
                            scope.launch {
                                actionBusy = true
                                try { api.setFanPower(power); refresh() }
                                catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Error") }
                                finally { actionBusy = false }
                            }
                        },
                        onLedPower = { power ->
                            scope.launch {
                                actionBusy = true
                                try { api.setLedPower(power); refresh() }
                                catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Error") }
                                finally { actionBusy = false }
                            }
                        },
                        onPump = {
                            scope.launch {
                                val decision = ControlPolicy.irrigationDecision(
                                    record?.soilHumidity,
                                    record?.waterLevel
                                )
                                if (!decision.allowed) {
                                    snackbar.showSnackbar(decision.message)
                                } else {
                                    actionBusy = true
                                    try {
                                        api.requestPump(
                                            control?.pumpRequest ?: 0L,
                                            ControlPolicy.PUMP_DURATION_MS
                                        )
                                        refresh()
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar(e.message ?: "Error solicitando riego")
                                    } finally {
                                        actionBusy = false
                                    }
                                }
                            }
                        }
                    )

                    Destination.HISTORY -> HistoryScreen(history, loading, error)
                    Destination.DIAGNOSTICS -> DiagnosticsScreen(
                        record = record,
                        control = control,
                        error = error,
                        isAdmin = profileRole == "admin",
                        controllerStatus = controllerStatus,
                        controllerBusy = controllerBusy,
                        controllerMessage = controllerMessage,
                        onReplaceController = { pairingCode ->
                            scope.launch {
                                if (pairingCode.replace("-", "").length != 12) {
                                    controllerMessage = "Ingresa el código completo del ESP32."
                                } else {
                                    controllerBusy = true
                                    controllerMessage = null
                                    try {
                                        controllerStatus = api.replaceActiveController(pairingCode)
                                            ?: api.controllerAdminStatus()
                                        control = api.deviceControl()
                                        controllerMessage = "Controlador reemplazado sin perder historial ni configuración."
                                    } catch (e: Exception) {
                                        controllerMessage = e.message ?: "No se pudo reemplazar el controlador"
                                    } finally {
                                        controllerBusy = false
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationPane(
    destination: Destination,
    profileName: String,
    profileRole: String,
    onSignOut: () -> Unit,
    onDestination: (Destination) -> Unit
) {
    Surface(
        modifier = Modifier.width(260.dp).fillMaxHeight(),
        color = Color(0xFF0F1713),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("EcoSphere", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Microclima inteligente", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))

            Destination.entries.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.label) },
                    selected = destination == item,
                    onClick = { onDestination(item) },
                    modifier = Modifier.padding(vertical = 3.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFF15482A),
                        selectedTextColor = Color.White,
                        unselectedTextColor = Muted
                    )
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                profileName.ifBlank { "Cuenta verificada" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                when (profileRole) {
                    "admin" -> "Administrador"
                    "operator" -> "Operador"
                    else -> "Visualizador"
                },
                color = Muted,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión")
            }
            Spacer(Modifier.height(12.dp))
            Text("EcoSphere Desktop 1.4.2", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Dashboard(
    record: SensorRecord?,
    control: DeviceControl?,
    loading: Boolean,
    actionBusy: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onAutoMode: (Boolean) -> Unit,
    onFanPower: (Int) -> Unit,
    onLedPower: (Int) -> Unit,
    onPump: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text("EcoSphere", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Sistema inteligente de microclima", color = Muted)
        }

        if (error != null) {
            Surface(color = Color(0xFF4A1717), shape = RoundedCornerShape(14.dp)) {
                Text(error, Modifier.padding(14.dp), color = Color(0xFFFFB4AB))
            }
        }

        StatusPanel(
            record = record,
            control = control,
            loading = loading,
            onRefresh = onRefresh
        )

        DashboardSectionTitle("Lecturas ambientales", "Toca cualquier tarjeta para abrir el detalle")
        if (record == null) {
            EmptyTelemetryCard()
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricCard("Temperatura", format(record.temperature, "°C"), "BME280", Modifier.weight(1f))
                MetricCard("Humedad aire", format(record.airHumidity, "%"), "BME280", Modifier.weight(1f))
                MetricCard("Humedad suelo", format(record.soilHumidity, "%"), "Sensor capacitivo", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricCard("Iluminación", format(record.lightLux, "lux"), "BH1750", Modifier.weight(1f))
                MetricCard("Nivel de agua", waterLabel(record.waterLevel), "Sensor horizontal GPIO32", Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }

        DashboardSectionTitle("Estado del sistema", "Señales de salida del ESP32; no confirman conexión física")
        ActuatorStatusPanel(record, control)

        DashboardSectionTitle("Control remoto", "Órdenes enviadas a través de Supabase")
        ControlPanel(
            control = control,
            record = record,
            actionBusy = actionBusy,
            onAutoMode = onAutoMode,
            onFanPower = onFanPower,
            onLedPower = onLedPower,
            onPump = onPump
        )
    }
}

@Composable
private fun StatusPanel(
    record: SensorRecord?,
    control: DeviceControl?,
    loading: Boolean,
    onRefresh: () -> Unit
) {
    val online = control?.isOnlineNow() == true
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0F5A31),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Estado general", color = Color(0xFFD6FFE0), fontSize = 13.sp)
                    Text(
                        if (online) "Sistema conectado" else "Sistema sin conexión",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(color = Color(0xFF103D25), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        if (online) "ONLINE" else "OFFLINE",
                        Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        color = EcoGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStatus("Modo", if (control?.autoMode == true) "Automático" else "Manual", Modifier.weight(1f))
                SmallStatus("Último ESP32", formatDate(control?.lastSeenAt), Modifier.weight(1f))
            }
            SmallStatus("Última telemetría", formatDate(record?.createdAt), Modifier.fillMaxWidth())
            OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Actualizando..." else "Actualizar datos")
            }
        }
    }
}

@Composable
private fun DashboardSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyTelemetryCard() {
    Surface(Modifier.fillMaxWidth(), color = AppSurface2, shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Esperando telemetría", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Cuando el ESP32 vuelva a enviar datos, las lecturas aparecerán aquí automáticamente.",
                color = Muted,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun ActuatorStatusPanel(record: SensorRecord?, control: DeviceControl?) {
    val telemetryCurrent = control?.isOnlineNow() == true && ControlPolicy.isTelemetryFresh(record?.createdAt)
    Surface(Modifier.fillMaxWidth(), color = AppSurface2, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
            ActuatorStatusRow(
                "Ventilador",
                if (telemetryCurrent) ControlPolicy.actuatorPwmLabel(record?.fanOn, record?.fanPower) else "SIN CONFIRMAR"
            )
            HorizontalDivider(color = Color(0xFF354039))
            ActuatorStatusRow(
                "Bomba de riego",
                if (telemetryCurrent) ControlPolicy.actuatorSwitchLabel(record?.pumpOn) else "SIN CONFIRMAR"
            )
            HorizontalDivider(color = Color(0xFF354039))
            ActuatorStatusRow(
                "LED grow",
                if (telemetryCurrent) ControlPolicy.actuatorPwmLabel(record?.ledOn, record?.ledPower) else "SIN CONFIRMAR"
            )
            HorizontalDivider(color = Color(0xFF354039))
            ActuatorStatusRow(
                "Control",
                if (!telemetryCurrent) "SIN CONFIRMAR" else when (record?.autoMode ?: control?.autoMode) {
                    true -> "AUTOMÁTICO"
                    false -> "MANUAL"
                    null -> "SIN REGISTRO"
                }
            )
        }
    }
}

@Composable
private fun ActuatorStatusRow(title: String, status: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Surface(color = Color(0xFF34463B), shape = RoundedCornerShape(999.dp)) {
            Text(
                status,
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SmallStatus(title: String, value: String, modifier: Modifier) {
    Surface(modifier, color = Color(0xFF103D25), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Color(0xFFAFDDBB), fontSize = 11.sp)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, source: String, modifier: Modifier) {
    Surface(modifier.height(140.dp), color = AppSurface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Muted)
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(source, color = EcoGreen, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ControlPanel(
    control: DeviceControl?,
    record: SensorRecord?,
    actionBusy: Boolean,
    onAutoMode: (Boolean) -> Unit,
    onFanPower: (Int) -> Unit,
    onLedPower: (Int) -> Unit,
    onPump: () -> Unit
) {
    var fanLocal by remember(control?.fanPower) { mutableFloatStateOf((control?.fanPower ?: 0).toFloat()) }
    var ledLocal by remember(control?.ledPower) { mutableFloatStateOf((control?.ledPower ?: 0).toFloat()) }
    val manual = control?.autoMode != true

    Surface(Modifier.fillMaxWidth(), color = AppSurface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Modo de operación", fontWeight = FontWeight.SemiBold)
                    Text(if (manual) "Control manual habilitado" else "El ESP32 decide según las lecturas", color = Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = control?.autoMode == true,
                    onCheckedChange = onAutoMode,
                    enabled = !actionBusy && control != null
                )
            }

            ControlSlider(
                title = "Potencia del ventilador",
                value = fanLocal,
                enabled = manual && !actionBusy,
                onValue = { fanLocal = it },
                onCommit = { onFanPower(fanLocal.roundToInt()) }
            )

            ControlSlider(
                title = "Intensidad LED grow",
                value = ledLocal,
                enabled = manual && !actionBusy,
                onValue = { ledLocal = it },
                onCommit = { onLedPower(ledLocal.roundToInt()) }
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Riego manual", fontWeight = FontWeight.SemiBold)
                    Text(
                        ControlPolicy.irrigationStatus(record?.soilHumidity, record?.waterLevel),
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = onPump,
                    enabled = !actionBusy && control != null &&
                        ControlPolicy.irrigationDecision(record?.soilHumidity, record?.waterLevel).allowed
                ) {
                    Text("Regar ahora · 3 s")
                }
            }
        }
    }
}

@Composable
private fun ControlSlider(
    title: String,
    value: Float,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    onCommit: () -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("${value.roundToInt()} %", color = EcoGreen)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            onValueChangeFinished = onCommit,
            enabled = enabled,
            valueRange = 0f..100f,
            steps = 19
        )
    }
}

@Composable
private fun HistoryScreen(history: List<SensorRecord>, loading: Boolean, error: String?) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text("Registros históricos", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Últimos ${history.size} registros", color = Muted)
        Spacer(Modifier.height(18.dp))
        if (error != null) Text(error, color = Color(0xFFFFB4AB))
        if (loading && history.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            history.forEach { item ->
                Surface(color = AppSurface, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(formatDate(item.createdAt), modifier = Modifier.width(190.dp), color = Muted)
                        Text("T ${format(item.temperature, "°C")}", modifier = Modifier.width(110.dp))
                        Text("Aire ${format(item.airHumidity, "%")}", modifier = Modifier.width(120.dp))
                        Text("Suelo ${format(item.soilHumidity, "%")}", modifier = Modifier.width(120.dp))
                        Text("Luz ${format(item.lightLux, "lx")}", modifier = Modifier.width(130.dp))
                        Text("Agua ${waterLabel(item.waterLevel)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(
    record: SensorRecord?,
    control: DeviceControl?,
    error: String?,
    isAdmin: Boolean,
    controllerStatus: ControllerAdminStatus?,
    controllerBusy: Boolean,
    controllerMessage: String?,
    onReplaceController: (String) -> Unit
) {
    val online = control?.isOnlineNow() == true
    val telemetryCurrent = online && ControlPolicy.isTelemetryFresh(record?.createdAt)
    var pairingCode by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Diagnóstico del sistema", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Comprobación lógica a partir de la telemetría disponible", color = Muted)
        if (error != null) Text(error, color = Color(0xFFFFB4AB))
        Spacer(Modifier.height(8.dp))

        DiagnosticRow("ESP32", if (online) "OK" else "SIN CONEXIÓN", control?.lastSeenAt ?: "Sin heartbeat")
        DiagnosticRow("BME280", if (record?.temperature != null && record.airHumidity != null) "OK" else "SIN CONFIRMAR", "Temperatura y humedad del aire")
        DiagnosticRow("BH1750", if (record?.lightLux != null) "OK" else "SIN CONFIRMAR", "Sensor de iluminación")
        DiagnosticRow("Humedad de suelo", if (record?.soilHumidity != null) "OK" else "SIN CONFIRMAR", record?.soilHumidity?.let { "${it.roundToInt()} %" } ?: "Sin lectura")
        DiagnosticRow("Nivel de agua", if (record?.waterLevel != null) "OK" else "SIN CONFIRMAR", waterLabel(record?.waterLevel))
        DiagnosticRow(
            "Ventilador",
            if (telemetryCurrent) "SALIDA ESP32" else "SIN CONFIRMAR",
            if (telemetryCurrent) "${ControlPolicy.actuatorPwmLabel(record?.fanOn, record?.fanPower)} · conexión física no verificable" else "Sin telemetría actual"
        )
        DiagnosticRow(
            "LED Grow",
            if (telemetryCurrent) "SALIDA ESP32" else "SIN CONFIRMAR",
            if (telemetryCurrent) "${ControlPolicy.actuatorPwmLabel(record?.ledOn, record?.ledPower)} · conexión física no verificable" else "Sin telemetría actual"
        )
        DiagnosticRow(
            "Bomba",
            if (telemetryCurrent) "SALIDA ESP32" else "SIN CONFIRMAR",
            if (telemetryCurrent) "${ControlPolicy.actuatorSwitchLabel(record?.pumpOn)} · conexión física no verificable" else "Sin telemetría actual"
        )

        if (isAdmin) {
            Surface(Modifier.fillMaxWidth(), color = AppSurface, shape = RoundedCornerShape(14.dp)) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Controlador ESP32 reemplazable", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        if (controllerStatus?.controllerStatus == "active") {
                            "Activo ${controllerStatus.hardwareUidMasked.orEmpty()}${controllerStatus.firmwareVersion?.let { " · firmware $it" }.orEmpty()}"
                        } else {
                            "Aún no hay un controlador seguro vinculado."
                        },
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Text(
                        if (controllerStatus?.secureMode == true) "Modo seguro habilitado" else "Modo de transición activo",
                        color = EcoGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it.uppercase().take(14) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Código mostrado por el ESP32") },
                        placeholder = { Text("ABCD-EF12-3456") },
                        singleLine = true,
                        enabled = !controllerBusy
                    )
                    Button(
                        onClick = {
                            onReplaceController(pairingCode)
                            pairingCode = ""
                        },
                        enabled = !controllerBusy && pairingCode.isNotBlank()
                    ) {
                        Text(if (controllerBusy) "Vinculando…" else "Usar como reemplazo")
                    }
                    if (!controllerMessage.isNullOrBlank()) {
                        Text(controllerMessage, color = Muted, fontSize = 12.sp)
                    }
                    Text(
                        "El controlador anterior pasa a reserva. El panel, las órdenes y el historial no cambian.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(name: String, status: String, detail: String) {
    Surface(Modifier.fillMaxWidth(), color = AppSurface, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Muted, fontSize = 12.sp)
            }
            Text(status, color = EcoGreen, fontWeight = FontWeight.Bold)
        }
    }
}

private fun format(value: Double?, unit: String): String =
    value?.let { String.format("%.1f %s", it, unit) } ?: "--"

private fun waterLabel(value: String?): String = ControlPolicy.waterLevelLabel(value)

private fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return "Sin registro"
    return try {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    } catch (_: Exception) {
        value
    }
}
