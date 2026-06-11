package com.example.ecosphere.data.model

import com.google.gson.annotations.SerializedName

data class DeviceControl(
    @SerializedName("id")
    val id: Int = 1,

    @SerializedName("fan_target")
    val fanTarget: Boolean = false,

    @SerializedName("led_target")
    val ledTarget: Boolean = false,

    @SerializedName("auto_mode")
    val autoMode: Boolean = false,

    @SerializedName("pump_request")
    val pumpRequest: Long = 0,

    @SerializedName("pump_duration_ms")
    val pumpDurationMs: Int = 3000,

    @SerializedName("esp32_online")
    val esp32Online: Boolean = false,

    @SerializedName("last_seen_at")
    val lastSeenAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
)
