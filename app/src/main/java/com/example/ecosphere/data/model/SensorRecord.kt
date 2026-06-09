package com.example.ecosphere.data.model

import com.google.gson.annotations.SerializedName

data class SensorRecord(
    @SerializedName("id")
    val id: Long,

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("temperature")
    val temperature: Double?,

    @SerializedName("air_humidity")
    val airHumidity: Double?,

    @SerializedName("soil_humidity")
    val soilHumidity: Double?,

    @SerializedName("light_lux")
    val lightLux: Double?,

    @SerializedName("water_level")
    val waterLevel: String?,

    @SerializedName("fan_on")
    val fanOn: Boolean?,

    @SerializedName("pump_on")
    val pumpOn: Boolean?,

    @SerializedName("led_on")
    val ledOn: Boolean?,

    @SerializedName("auto_mode")
    val autoMode: Boolean?
)