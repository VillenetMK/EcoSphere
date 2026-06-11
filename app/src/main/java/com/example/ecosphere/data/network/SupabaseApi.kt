package com.example.ecosphere.data.network

import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.model.SensorRecord
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH

interface SupabaseApi {

    @GET("rest/v1/sensor_records?select=*&order=created_at.desc&limit=1")
    suspend fun getLatestRecord(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): List<SensorRecord>

    @GET("rest/v1/device_control?id=eq.1&select=*")
    suspend fun getDeviceControl(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): List<DeviceControl>

    @PATCH("rest/v1/device_control?id=eq.1")
    suspend fun updateDeviceControl(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<DeviceControl>
}
