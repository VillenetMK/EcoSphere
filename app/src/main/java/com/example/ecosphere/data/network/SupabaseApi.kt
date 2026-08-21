package com.example.ecosphere.data.network

import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.model.HistoryMonthSummary
import com.example.ecosphere.data.model.SensorRecord
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Query

interface SupabaseApi {

    @GET("rest/v1/sensor_records?select=*&order=created_at.desc&limit=1")
    suspend fun getLatestRecord(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): List<SensorRecord>

    @GET("rest/v1/sensor_history_months?select=*&order=month_key.desc")
    suspend fun getHistoryMonths(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): List<HistoryMonthSummary>

    @GET("rest/v1/sensor_records?select=*&order=created_at.desc")
    suspend fun getHistoryByMonth(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("created_at") fromFilter: String,
        @Query("created_at") toFilter: String,
        @Query("limit") limit: Int = 1000
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
