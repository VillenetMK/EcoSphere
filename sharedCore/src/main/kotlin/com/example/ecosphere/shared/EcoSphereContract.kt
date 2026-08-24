package com.example.ecosphere.shared

import com.google.gson.annotations.SerializedName
import java.util.Calendar
import java.util.TimeZone
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

    fun isTelemetryFresh(
        createdAt: String?,
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = ONLINE_TIMEOUT_MS
    ): Boolean {
        if (createdAt.isNullOrBlank()) return false
        val createdAtMillis = parseSupabaseUtcMillis(createdAt) ?: return false
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
        val lastSeenMillis = parseSupabaseUtcMillis(lastSeenAt) ?: return false
        val ageMs = nowMillis - lastSeenMillis
        return ageMs in -CLOCK_SKEW_TOLERANCE_MS..timeoutMs
    }

    private fun parseSupabaseUtcMillis(value: String): Long? {
        return try {
            val noZone = value.substringBefore("+").substringBefore("Z")
            val dateAndTime = noZone.split("T")
            if (dateAndTime.size != 2) return null

            val date = dateAndTime[0].split("-")
            val time = dateAndTime[1].split(":")
            if (date.size != 3 || time.size != 3) return null

            val secondsPart = time[2]
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(Calendar.YEAR, date[0].toInt())
                set(Calendar.MONTH, date[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, date[2].toInt())
                set(Calendar.HOUR_OF_DAY, time[0].toInt())
                set(Calendar.MINUTE, time[1].toInt())
                set(Calendar.SECOND, secondsPart.substringBefore(".").toInt())
                set(
                    Calendar.MILLISECOND,
                    secondsPart.substringAfter(".", "0").padEnd(3, '0').take(3).toInt()
                )
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }
}
