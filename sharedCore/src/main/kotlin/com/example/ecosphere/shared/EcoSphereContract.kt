package com.example.ecosphere.shared

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

object EcoSphereConfig {
    const val SUPABASE_URL = "https://kslzmrddrhfyyrxyfmbw.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_oHQqSvres8b5l0qgcpXJ2w_9A33lfg3"
}

data class SensorRecord(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("temperature") val temperature: Double? = null,
    @SerializedName("air_humidity") val airHumidity: Double? = null,
    @SerializedName("soil_humidity") val soilHumidity: Double? = null,
    @SerializedName("light_lux") val lightLux: Double? = null,
    @SerializedName("water_level") val waterLevel: String? = null,
    @SerializedName("fan_on") val fanOn: Boolean? = null,
    @SerializedName("fan_power") val fanPower: Int? = null,
    @SerializedName("pump_on") val pumpOn: Boolean? = null,
    @SerializedName("led_on") val ledOn: Boolean? = null,
    @SerializedName("led_power") val ledPower: Int? = null,
    @SerializedName("auto_mode") val autoMode: Boolean? = null
)

data class DeviceControl(
    @SerializedName("id") val id: Int = 1,
    @SerializedName("fan_target") val fanTarget: Boolean = false,
    @SerializedName("fan_power") val fanPower: Int = 0,
    @SerializedName("led_target") val ledTarget: Boolean = false,
    @SerializedName("led_power") val ledPower: Int = 0,
    @SerializedName("auto_mode") val autoMode: Boolean = false,
    @SerializedName("pump_request") val pumpRequest: Long = 0,
    @SerializedName("pump_duration_ms") val pumpDurationMs: Int = ControlPolicy.PUMP_DURATION_MS,
    @SerializedName("esp32_online") val esp32Online: Boolean = false,
    @SerializedName("heartbeat_seq") val heartbeatSeq: Long = 0,
    @SerializedName("last_seen_at") val lastSeenAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    fun isOnlineNow(
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = ControlPolicy.ONLINE_TIMEOUT_MS
    ): Boolean = ControlPolicy.isDeviceOnline(
        esp32Online = esp32Online,
        lastSeenAt = lastSeenAt,
        nowMillis = nowMillis,
        timeoutMs = timeoutMs
    )
}

data class ControllerAdminStatus(
    @SerializedName("controller_id") val controllerId: Long? = null,
    @SerializedName("hardware_uid_masked") val hardwareUidMasked: String? = null,
    @SerializedName("controller_status") val controllerStatus: String = "not_paired",
    @SerializedName("firmware_version") val firmwareVersion: String? = null,
    @SerializedName("last_seen_at") val lastSeenAt: String? = null,
    @SerializedName("secure_mode") val secureMode: Boolean = false
)

data class PairingWindowStatus(
    @SerializedName("pairing_open_until") val pairingOpenUntil: String? = null
)

object ControllerPairing {
    private val separators = Regex("[\\s-]")

    fun hardwareUidOrNull(value: String): String? = normalizedHexOrNull(value, 12)

    fun claimProofOrNull(value: String): String? = normalizedHexOrNull(value, 24)

    fun pairingCodeOrNull(value: String): String? = normalizedHexOrNull(value, 12)

    private fun normalizedHexOrNull(value: String, length: Int): String? {
        val normalized = value.replace(separators, "").uppercase()
        return normalized.takeIf {
            it.length == length && it.all { character -> character in '0'..'9' || character in 'A'..'F' }
        }
    }
}

object ClientErrorMessages {
    private val safeMessages = setOf(
        "Tu sesión expiró. Inicia sesión nuevamente.",
        "Tu cuenta no tiene permiso para realizar esta acción.",
        "Desactiva el modo automático antes de ajustar el ventilador.",
        "Desactiva el modo automático antes de ajustar la iluminación.",
        "Desactiva el modo automático antes de solicitar riego manual.",
        "El código del controlador es inválido o expiró.",
        "El riego fue bloqueado porque las condiciones actuales no son seguras.",
        "Confirma de nuevo tu autenticador para realizar esta acción administrativa.",
        "Actualiza el firmware del ESP32 antes de usarlo como reemplazo.",
        "Se enviaron demasiadas órdenes. Espera un momento e inténtalo nuevamente."
    )

    fun safe(unsafeMessage: String?, fallback: String): String {
        val message = unsafeMessage.orEmpty().trim()
        val normalized = message.lowercase()
        return when {
            message in safeMessages -> message
            Regex("^No se pudo completar la solicitud \\(HTTP [0-9]{3}\\)\\.$").matches(message) -> message
            "http 401" in normalized || "unauthorized" in normalized ->
                "Tu sesión expiró. Inicia sesión nuevamente."
            "http 403" in normalized || "forbidden" in normalized ->
                "Tu cuenta no tiene permiso para realizar esta acción."
            "http 429" in normalized || "rate limit" in normalized || "cooldown" in normalized ->
                "Se enviaron demasiadas órdenes. Espera un momento e inténtalo nuevamente."
            "timeout" in normalized || "failed to connect" in normalized
                || "unable to resolve host" in normalized || "network" in normalized ->
                "No se pudo conectar con EcoSphere. Revisa tu conexión e inténtalo nuevamente."
            else -> fallback
        }
    }
}

data class HistoryMonthSummary(
    @SerializedName("month_key") val monthKey: String,
    @SerializedName("first_record") val firstRecord: String? = null,
    @SerializedName("last_record") val lastRecord: String? = null,
    @SerializedName("record_count") val recordCount: Long = 0
)

enum class IrrigationBlockReason {
    NONE,
    MISSING_SOIL_READING,
    SOIL_TOO_WET,
    MISSING_WATER_READING,
    LOW_WATER
}

data class IrrigationDecision(
    val allowed: Boolean,
    val message: String,
    val reason: IrrigationBlockReason
)

object ControlPolicy {
    const val SOIL_MANUAL_DENY_THRESHOLD = 60.0
    const val SOIL_DRY_THRESHOLD = 35.0
    const val PUMP_DURATION_MS = 3_000
    const val ONLINE_TIMEOUT_MS = 30_000L
    const val CLOCK_SKEW_TOLERANCE_MS = 60_000L

    fun clampPower(power: Int): Int = power.coerceIn(0, 100)

    fun irrigationDecision(
        soilHumidity: Double?,
        waterLevel: String?
    ): IrrigationDecision = when {
        soilHumidity == null || !soilHumidity.isFinite() -> IrrigationDecision(
            allowed = false,
            message = "Riego manual denegado. No hay lectura válida de humedad del suelo.",
            reason = IrrigationBlockReason.MISSING_SOIL_READING
        )

        soilHumidity >= SOIL_MANUAL_DENY_THRESHOLD -> IrrigationDecision(
            allowed = false,
            message = "Suelo húmedo. Riego manual denegado. Humedad actual: ${soilHumidity.roundToInt()} %.",
            reason = IrrigationBlockReason.SOIL_TOO_WET
        )

        waterLevel.isNullOrBlank() || waterLevel.lowercase() !in setOf("high", "low") -> IrrigationDecision(
            allowed = false,
            message = "Riego manual denegado. No hay lectura válida del nivel de agua.",
            reason = IrrigationBlockReason.MISSING_WATER_READING
        )

        waterLevel.equals("low", ignoreCase = true) -> IrrigationDecision(
            allowed = false,
            message = "Riego manual denegado. Nivel de agua bajo.",
            reason = IrrigationBlockReason.LOW_WATER
        )

        else -> IrrigationDecision(
            allowed = true,
            message = "Riego manual disponible.",
            reason = IrrigationBlockReason.NONE
        )
    }

    fun irrigationStatus(soilHumidity: Double?, waterLevel: String?): String {
        val decision = irrigationDecision(soilHumidity, waterLevel)
        if (!decision.allowed) return decision.message

        return if (soilHumidity != null && soilHumidity <= SOIL_DRY_THRESHOLD) {
            "Suelo seco: riego permitido."
        } else {
            "Rango aceptable: riego manual disponible."
        }
    }

    fun waterLevelLabel(value: String?): String = when (value?.lowercase()) {
        "high" -> "Disponible"
        "low" -> "Bajo"
        else -> "Sin lectura válida"
    }

    fun actuatorPwmLabel(reportedOn: Boolean?, reportedPower: Int?): String {
        if (reportedOn == null && reportedPower == null) return "SIN REGISTRO"
        val power = (reportedPower ?: if (reportedOn == true) 100 else 0).coerceIn(0, 100)
        return "SALIDA PWM $power %"
    }

    fun actuatorSwitchLabel(reportedOn: Boolean?): String = when (reportedOn) {
        true -> "SALIDA ACTIVA"
        false -> "SALIDA INACTIVA"
        null -> "SIN REGISTRO"
    }

    fun currentTelemetry(
        record: SensorRecord?,
        control: DeviceControl?,
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = ONLINE_TIMEOUT_MS
    ): SensorRecord? {
        if (record == null || control == null) return null

        val telemetryFresh = isTelemetryFresh(
            createdAt = record.createdAt,
            nowMillis = nowMillis,
            timeoutMs = timeoutMs
        )
        val deviceOnline = isDeviceOnline(
            esp32Online = control.esp32Online,
            lastSeenAt = control.lastSeenAt,
            nowMillis = nowMillis,
            timeoutMs = timeoutMs
        )
        return record.takeIf { telemetryFresh && deviceOnline }
    }

    fun isTelemetryFresh(
        createdAt: String?,
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = ONLINE_TIMEOUT_MS
    ): Boolean {
        if (createdAt.isNullOrBlank()) return false
        val createdAtMillis = timestampMillis(createdAt) ?: return false
        val ageMs = nowMillis - createdAtMillis
        return ageMs in -CLOCK_SKEW_TOLERANCE_MS..timeoutMs
    }

    fun isDeviceOnline(
        esp32Online: Boolean,
        lastSeenAt: String?,
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = ONLINE_TIMEOUT_MS
    ): Boolean {
        if (!esp32Online || lastSeenAt.isNullOrBlank()) return false
        val lastSeenMillis = timestampMillis(lastSeenAt) ?: return false
        val ageMs = nowMillis - lastSeenMillis
        return ageMs in -CLOCK_SKEW_TOLERANCE_MS..timeoutMs
    }

    fun timestampMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val match = Regex(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})?$"
        ).matchEntire(value.trim()) ?: return null
        val base = match.groupValues[1]
        val millis = match.groupValues[2].ifEmpty { "0" }.padEnd(3, '0').take(3)
        val zone = match.groupValues[3].ifEmpty { "Z" }
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                isLenient = false
            }.parse("$base.$millis$zone")?.time
        }.getOrNull()
    }
}
