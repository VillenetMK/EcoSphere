package com.example.ecosphere.data.network

import com.example.ecosphere.data.model.SensorRecord
import retrofit2.http.GET
import retrofit2.http.Header

interface SupabaseApi {

    @GET("rest/v1/sensor_records?select=*&order=created_at.desc&limit=1")
    suspend fun getLatestRecord(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): List<SensorRecord>
}