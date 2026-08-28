package com.example.ecosphere.data.network

import com.example.ecosphere.data.model.ControlAuditEntry
import com.example.ecosphere.data.model.ControllerAdminStatus
import com.example.ecosphere.data.model.DeviceControl
import com.example.ecosphere.data.model.HistoryMonthSummary
import com.example.ecosphere.data.model.SensorRecord
import com.example.ecosphere.data.model.UsernameLoginResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseApi {

    @POST("functions/v1/username-login")
    suspend fun signInWithUsername(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: Map<String, String>
    ): UsernameLoginResponse

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
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
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

    @POST("rest/v1/rpc/admin_control_audit")
    suspend fun getControlAudit(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: Map<String, Int>
    ): List<ControlAuditEntry>

    @POST("rest/v1/rpc/controller_admin_status")
    suspend fun getControllerAdminStatus(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: Map<String, String> = emptyMap()
    ): List<ControllerAdminStatus>

    @POST("rest/v1/rpc/replace_active_controller")
    suspend fun replaceActiveController(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: Map<String, String>
    ): List<ControllerAdminStatus>
}
