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

    suspend fun getDeviceControl(): DeviceControl? {
        return api.getDeviceControl(
            apiKey = apiKey,
            authorization = authorization
        ).firstOrNull()
    }

    suspend fun updateAutoMode(enabled: Boolean): DeviceControl? {
        return updateDeviceControl(
            mapOf("auto_mode" to enabled)
        )
    }

    suspend fun updateFanTarget(enabled: Boolean): DeviceControl? {
        return updateDeviceControl(
            mapOf("fan_target" to enabled)
        )
    }

    suspend fun updateLedTarget(enabled: Boolean): DeviceControl? {
        return updateDeviceControl(
            mapOf("led_target" to enabled)
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
