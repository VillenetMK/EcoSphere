package com.example.ecosphere.data.repository

import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.model.SensorRecord
import com.example.ecosphere.data.network.SupabaseApi
import com.example.ecosphere.data.network.SupabaseConfig

class SensorRepository(
    private val api: SupabaseApi
) {
    private val apiKey = SupabaseConfig.API_KEY
    private val authorization = "Bearer ${SupabaseConfig.API_KEY}"

    suspend fun getLatestRecord(): SensorRecord? {
        return api.getLatestRecord(
            apiKey = apiKey,
            authorization = authorization
        ).firstOrNull()
    }

    suspend fun getHistory(): List<SensorRecord> {
        return api.getHistory(
            apiKey = apiKey,
            authorization = authorization
        )
    }

    suspend fun getDeviceControl(): DeviceControl? {
        return api.getDeviceControl(
            apiKey = apiKey,
            authorization = authorization
        ).firstOrNull()
    }

    suspend fun updateAutoMode(enabled: Boolean): DeviceControl? {
        return updateDeviceControl(mapOf("auto_mode" to enabled))
    }

    suspend fun updateFanPower(power: Int): DeviceControl? {
        val safePower = power.coerceIn(0, 100)
        return updateDeviceControl(
            mapOf(
                "fan_power" to safePower,
                "fan_target" to (safePower > 0)
            )
        )
    }

    suspend fun updateLedPower(power: Int): DeviceControl? {
        val safePower = power.coerceIn(0, 100)
        return updateDeviceControl(
            mapOf(
                "led_power" to safePower,
                "led_target" to (safePower > 0)
            )
        )
    }

    suspend fun requestPump(currentRequest: Long, durationMs: Int = 3000): DeviceControl? {
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
            authorization = authorization,
            body = body
        ).firstOrNull()
    }
}
