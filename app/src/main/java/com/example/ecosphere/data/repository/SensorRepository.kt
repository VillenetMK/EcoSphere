package com.example.ecosphere.data.repository

import com.example.ecosphere.data.model.SensorRecord
import com.example.ecosphere.data.network.SupabaseApi
import com.example.ecosphere.data.network.SupabaseConfig

class SensorRepository(
    private val api: SupabaseApi
) {
    suspend fun getLatestRecord(): SensorRecord? {
        return api.getLatestRecord(
            apiKey = SupabaseConfig.API_KEY,
            authorization = "Bearer ${SupabaseConfig.API_KEY}"
        ).firstOrNull()
    }
}