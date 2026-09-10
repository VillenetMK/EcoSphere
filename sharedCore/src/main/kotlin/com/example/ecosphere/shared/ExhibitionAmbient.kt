/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

package com.example.ecosphere.shared

/** Display-only references: deliberately not a SensorRecord or control input. */
data class ExhibitionAmbientReadings(
    val temperature: Double,
    val airHumidity: Double,
    val lightLux: Double?
)

object ExhibitionAmbient {
    private const val HEVER_USER_ID = "367e842b-fd47-4c38-a3fc-c54c47732a9e"
    const val NOTICE = "Valores de referencia: temperatura y humedad del aire. " +
        "Luz estimada según la salida LED reportada por el ESP32; no es una medición del BH1750. " +
        "Suelo, agua, conexión y controles conservan sus datos reales."
    const val AMBIENT_LABEL = "Valor de referencia"
    const val LIGHT_LABEL = "Estimación según LED"

    // The authenticated session ID, never a display name or email, selects this view.
    // Nothing is persisted, uploaded or used by ControlPolicy.
    fun forViewer(
        authenticatedUserId: String?,
        profileStatus: String?,
        profileRole: String?
    ): ExhibitionAmbientReadings? = if (
        authenticatedUserId == HEVER_USER_ID && profileStatus == "approved" && profileRole == "operator"
    ) {
        ExhibitionAmbientReadings(temperature = 25.4, airHumidity = 62.0, lightLux = null)
    } else {
        null
    }

    /** Recompute at render time so a previous view never retains a stale LED estimate. */
    fun withCurrentTelemetry(
        readings: ExhibitionAmbientReadings?,
        record: SensorRecord?,
        control: DeviceControl?,
        nowMillis: Long = System.currentTimeMillis()
    ): ExhibitionAmbientReadings? {
        if (readings == null) return null
        val currentRecord = ControlPolicy.currentTelemetry(record, control, nowMillis)
        return readings.copy(lightLux = estimateLightLux(currentRecord?.ledPower?.toDouble(), currentRecord?.ledOn))
    }

    // A visual reference at 850 lx for 100% reported PWM, not an optical calibration.
    // Desired control power is never used; physical illumination still needs a sensor.
    internal fun estimateLightLux(reportedPower: Double?, reportedOn: Boolean?): Double? {
        if (reportedPower == null || !reportedPower.isFinite() || reportedPower !in 0.0..100.0) return null
        if (reportedOn == null || reportedOn != (reportedPower > 0.0)) return null
        return 850.0 * reportedPower / 100.0
    }
}
