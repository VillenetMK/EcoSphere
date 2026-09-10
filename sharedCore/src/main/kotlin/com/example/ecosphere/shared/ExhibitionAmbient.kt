/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

package com.example.ecosphere.shared

/** Display-only examples: deliberately not a SensorRecord or control input. */
data class ExhibitionAmbientReadings(
    val temperature: Double,
    val airHumidity: Double,
    val lightLux: Double
)

object ExhibitionAmbient {
    private const val HEVER_USER_ID = "367e842b-fd47-4c38-a3fc-c54c47732a9e"
    const val NOTICE = "DEMOSTRACIÓN: temperatura, humedad del aire y luz simuladas. " +
        "Suelo, agua, conexión y controles conservan sus datos reales."

    // The authenticated session ID, never a display name or email, selects this view.
    // Nothing is persisted, uploaded or used by ControlPolicy.
    fun forViewer(
        authenticatedUserId: String?,
        profileStatus: String?,
        profileRole: String?
    ): ExhibitionAmbientReadings? = if (
        authenticatedUserId == HEVER_USER_ID && profileStatus == "approved" && profileRole == "operator"
    ) {
        ExhibitionAmbientReadings(temperature = 25.4, airHumidity = 62.0, lightLux = 850.0)
    } else {
        null
    }
}
