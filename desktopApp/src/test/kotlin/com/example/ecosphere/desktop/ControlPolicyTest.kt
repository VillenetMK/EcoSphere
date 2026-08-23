package com.example.ecosphere.desktop

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlPolicyTest {
    private val now = Instant.parse("2026-08-23T12:00:00Z").toEpochMilli()

    @Test
    fun `loads the canonical contract`() {
        val config = ControlPolicy.config

        assertEquals(2_000L, config.pollIntervalMs)
        assertEquals(30_000L, config.onlineTimeoutMs)
        assertEquals(60.0, config.manualWatering.soilDenyAtOrAbovePercent)
        assertEquals(3_000, config.manualWatering.pumpDurationMs)
        assertEquals(listOf("low"), config.manualWatering.blockedWaterLevels)
        assertEquals(35.0, config.automaticWatering.soilStartAtOrBelowPercent)
        assertEquals("firmware", config.automaticWatering.implementation)
    }

    @Test
    fun `online status obeys heartbeat and time window`() {
        assertTrue(
            ControlPolicy.isDeviceOnline(
                true,
                "2026-08-23T11:59:30Z",
                now
            )
        )
        assertFalse(
            ControlPolicy.isDeviceOnline(
                true,
                "2026-08-23T11:59:29.999Z",
                now
            )
        )
        assertFalse(ControlPolicy.isDeviceOnline(false, "2026-08-23T12:00:00Z", now))
        assertFalse(ControlPolicy.isDeviceOnline(true, "invalid", now))
    }

    @Test
    fun `manual watering requires soil data and applies inclusive threshold`() {
        assertFalse(ControlPolicy.evaluateManualWatering(null, "high").allowed)
        assertTrue(ControlPolicy.evaluateManualWatering(59.9, "high").allowed)
        assertFalse(ControlPolicy.evaluateManualWatering(60.0, "high").allowed)
        assertFalse(ControlPolicy.evaluateManualWatering(80.0, "high").allowed)
    }

    @Test
    fun `low water blocks while other values preserve current behavior`() {
        assertFalse(ControlPolicy.evaluateManualWatering(30.0, "LOW").allowed)
        assertTrue(ControlPolicy.evaluateManualWatering(30.0, "high").allowed)
        assertTrue(ControlPolicy.evaluateManualWatering(30.0, null).allowed)
    }
}
