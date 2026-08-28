package com.example.ecosphere.data.model

import com.google.gson.annotations.SerializedName

data class ControlAuditEntry(
    val id: Long,
    @SerializedName("actor_username") val actorUsername: String,
    @SerializedName("actor_role") val actorRole: String,
    @SerializedName("auto_mode_before") val autoModeBefore: Boolean?,
    @SerializedName("auto_mode_after") val autoModeAfter: Boolean?,
    @SerializedName("fan_power_before") val fanPowerBefore: Int?,
    @SerializedName("fan_power_after") val fanPowerAfter: Int?,
    @SerializedName("led_power_before") val ledPowerBefore: Int?,
    @SerializedName("led_power_after") val ledPowerAfter: Int?,
    @SerializedName("pump_requested") val pumpRequested: Boolean,
    @SerializedName("pump_duration_ms") val pumpDurationMs: Int?,
    @SerializedName("created_at") val createdAt: String
)
