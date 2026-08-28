package com.example.ecosphere.data.repository

import com.example.ecosphere.data.model.ControlAuditEntry
import com.example.ecosphere.data.model.ControllerAdminStatus
import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.model.HistoryMonthSummary
import com.example.ecosphere.data.model.SensorRecord
import com.example.ecosphere.data.network.SupabaseApi
import com.example.ecosphere.data.network.SupabaseConfig
import com.example.ecosphere.shared.ControlPolicy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class SensorRepository(
    private val api: SupabaseApi,
    private val accessToken: () -> String?
) {
    private val apiKey = SupabaseConfig.API_KEY

    private fun authorization(): String {
        val token = accessToken()?.takeIf(String::isNotBlank)
            ?: error("Tu sesión expiró. Inicia sesión nuevamente.")
        return "Bearer $token"
    }

    suspend fun getLatestRecord(): SensorRecord? {
        return api.getLatestRecord(
            apiKey = apiKey,
            authorization = authorization()
        ).firstOrNull()
    }

    suspend fun getHistoryMonths(): List<HistoryMonthSummary> {
        return api.getHistoryMonths(
            apiKey = apiKey,
            authorization = authorization()
        )
    }

    suspend fun getHistoryByMonth(
        monthKey: String,
        offset: Int = 0,
        limit: Int = HISTORY_PAGE_SIZE
    ): List<SensorRecord> {
        val (fromUtc, toUtc) = monthBoundsUtc(monthKey)
        return api.getHistoryByMonth(
            apiKey = apiKey,
            authorization = authorization(),
            fromFilter = "gte.$fromUtc",
            toFilter = "lt.$toUtc",
            limit = limit,
            offset = offset
        )
    }

    suspend fun getDeviceControl(): DeviceControl? {
        return api.getDeviceControl(
            apiKey = apiKey,
            authorization = authorization()
        ).firstOrNull()
    }

    suspend fun getControlAudit(limit: Int = AUDIT_PAGE_SIZE): List<ControlAuditEntry> {
        return api.getControlAudit(
            apiKey = apiKey,
            authorization = authorization(),
            body = mapOf("p_limit" to limit.coerceIn(1, MAX_AUDIT_PAGE_SIZE))
        )
    }

    suspend fun getControllerAdminStatus(): ControllerAdminStatus? {
        return api.getControllerAdminStatus(
            apiKey = apiKey,
            authorization = authorization()
        ).firstOrNull()
    }

    suspend fun replaceActiveController(pairingCode: String): ControllerAdminStatus? {
        return api.replaceActiveController(
            apiKey = apiKey,
            authorization = authorization(),
            body = mapOf("p_pairing_code" to pairingCode.trim())
        ).firstOrNull()
    }

    suspend fun updateAutoMode(enabled: Boolean): DeviceControl? {
        return updateDeviceControl(mapOf("auto_mode" to enabled))
    }

    suspend fun updateFanPower(power: Int): DeviceControl? {
        val safePower = ControlPolicy.clampPower(power)
        return updateDeviceControl(
            mapOf(
                "fan_power" to safePower,
                "fan_target" to (safePower > 0)
            )
        )
    }

    suspend fun updateLedPower(power: Int): DeviceControl? {
        val safePower = ControlPolicy.clampPower(power)
        return updateDeviceControl(
            mapOf(
                "led_power" to safePower,
                "led_target" to (safePower > 0)
            )
        )
    }

    suspend fun requestPump(
        currentRequest: Long,
        durationMs: Int = ControlPolicy.PUMP_DURATION_MS
    ): DeviceControl? {
        return updateDeviceControl(
            mapOf(
                "pump_request" to currentRequest + 1,
                "pump_duration_ms" to durationMs
            )
        )
    }

    private suspend fun updateDeviceControl(body: Map<String, Any>): DeviceControl? {
        return api.updateDeviceControl(
            apiKey = apiKey,
            authorization = authorization(),
            body = body
        ).firstOrNull()
    }

    private fun monthBoundsUtc(monthKey: String): Pair<String, String> {
        val parts = monthKey.split("-")
        require(parts.size == 2) { "Mes histórico inválido: $monthKey" }

        val year = parts[0].toInt()
        val month = parts[1].toInt()
        require(month in 1..12) { "Mes histórico inválido: $monthKey" }

        val localZone = TimeZone.getTimeZone("America/Lima")
        val start = Calendar.getInstance(localZone).apply {
            clear()
            set(year, month - 1, 1, 0, 0, 0)
        }
        val end = start.clone() as Calendar
        end.add(Calendar.MONTH, 1)

        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        return formatter.format(start.time) to formatter.format(end.time)
    }

    companion object {
        const val HISTORY_PAGE_SIZE = 200
        const val AUDIT_PAGE_SIZE = 200
        const val MAX_AUDIT_PAGE_SIZE = 500
    }
}
