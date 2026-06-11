package com.example.ecosphere.data.model

import com.google.gson.annotations.SerializedName
import java.util.Calendar
import java.util.TimeZone

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
) {
    fun isOnlineNow(timeoutMs: Long = 30_000L): Boolean {
        if (!esp32Online || lastSeenAt.isNullOrBlank()) return false

        val lastSeenMillis = parseSupabaseUtcMillis(lastSeenAt) ?: return false
        val ageMs = System.currentTimeMillis() - lastSeenMillis

        return ageMs in -60_000L..timeoutMs
    }

    private fun parseSupabaseUtcMillis(value: String): Long? {
        return try {
            val noZone = value
                .substringBefore("+")
                .substringBefore("Z")

            val dateAndTime = noZone.split("T")
            if (dateAndTime.size != 2) return null

            val date = dateAndTime[0].split("-")
            val time = dateAndTime[1].split(":")
            if (date.size != 3 || time.size != 3) return null

            val year = date[0].toInt()
            val month = date[1].toInt()
            val day = date[2].toInt()
            val hour = time[0].toInt()
            val minute = time[1].toInt()

            val secondsPart = time[2]
            val second = secondsPart.substringBefore(".").toInt()
            val millis = secondsPart
                .substringAfter(".", "0")
                .padEnd(3, '0')
                .take(3)
                .toInt()

            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, second)
                set(Calendar.MILLISECOND, millis)
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }
}
