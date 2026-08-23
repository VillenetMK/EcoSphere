package com.example.ecosphere.desktop

import com.google.gson.Gson
import java.time.OffsetDateTime

internal data class ManualWateringPolicy(
    val soilDenyAtOrAbovePercent: Double,
    val pumpDurationMs: Int,
    val blockedWaterLevels: List<String>
)

internal data class AutomaticWateringPolicy(
    val soilStartAtOrBelowPercent: Double,
    val implementation: String
)

internal data class ControlPolicyConfig(
    val schemaVersion: Int,
    val pollIntervalMs: Long,
    val onlineTimeoutMs: Long,
    val futureClockSkewMs: Long,
    val manualWatering: ManualWateringPolicy,
    val automaticWatering: AutomaticWateringPolicy
)

internal data class ManualWateringDecision(
    val allowed: Boolean,
    val message: String? = null
)

internal object ControlPolicy {
    val config: ControlPolicyConfig by lazy { loadAndValidate() }

    fun isDeviceOnline(
        esp32Online: Boolean,
        lastSeenAt: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!esp32Online || lastSeenAt.isNullOrBlank()) return false

        return try {
            val lastSeenMs = OffsetDateTime.parse(lastSeenAt).toInstant().toEpochMilli()
            val ageMs = nowMs - lastSeenMs
            ageMs in -config.futureClockSkewMs..config.onlineTimeoutMs
        } catch (_: Exception) {
            false
        }
    }

    fun evaluateManualWatering(
        soilHumidity: Double?,
        waterLevel: String?
    ): ManualWateringDecision {
        if (soilHumidity == null || !soilHumidity.isFinite()) {
            return ManualWateringDecision(
                allowed = false,
                message = "Riego manual denegado. No hay lectura válida de humedad del suelo."
            )
        }

        if (soilHumidity >= config.manualWatering.soilDenyAtOrAbovePercent) {
            return ManualWateringDecision(
                allowed = false,
                message = "Suelo húmedo. Riego manual denegado. Humedad actual: ${soilHumidity.toInt()} %."
            )
        }

        val normalizedWater = waterLevel?.trim()?.lowercase().orEmpty()
        if (normalizedWater in config.manualWatering.blockedWaterLevels) {
            return ManualWateringDecision(
                allowed = false,
                message = "Riego manual denegado. Nivel de agua bajo."
            )
        }

        return ManualWateringDecision(allowed = true)
    }

    private fun loadAndValidate(): ControlPolicyConfig {
        val stream = ControlPolicy::class.java.classLoader
            .getResourceAsStream("control-policy.json")
            ?: error("No se encontró control-policy.json en los recursos de Desktop.")

        val policy = stream.bufferedReader().use {
            Gson().fromJson(it, ControlPolicyConfig::class.java)
        }

        require(policy.schemaVersion == 1) {
            "Contrato de control incompatible: schemaVersion debe ser 1."
        }
        require(policy.pollIntervalMs > 0) { "pollIntervalMs debe ser positivo." }
        require(policy.onlineTimeoutMs > 0) { "onlineTimeoutMs debe ser positivo." }
        require(policy.futureClockSkewMs > 0) { "futureClockSkewMs debe ser positivo." }
        require(policy.manualWatering.pumpDurationMs > 0) {
            "pumpDurationMs debe ser positivo."
        }
        require(policy.manualWatering.soilDenyAtOrAbovePercent in 0.0..100.0) {
            "El umbral manual debe estar entre 0 y 100."
        }
        require(policy.manualWatering.blockedWaterLevels.isNotEmpty()) {
            "Debe existir al menos un nivel de agua bloqueado."
        }

        return policy.copy(
            manualWatering = policy.manualWatering.copy(
                blockedWaterLevels = policy.manualWatering.blockedWaterLevels.map {
                    it.trim().lowercase()
                }
            )
        )
    }
}
