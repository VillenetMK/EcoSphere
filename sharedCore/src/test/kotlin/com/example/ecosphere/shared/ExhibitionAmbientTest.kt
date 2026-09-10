package com.example.ecosphere.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExhibitionAmbientTest {
    private val hever = "367e842b-fd47-4c38-a3fc-c54c47732a9e"
    private val gabriel = "ed35214c-45df-4311-b3f4-74d859f3d142"

    @Test
    fun `only the approved operator with the exact session id sees the examples`() {
        assertNotNull(ExhibitionAmbient.forViewer(hever, "approved", "operator"))
        assertNull(ExhibitionAmbient.forViewer(gabriel, "approved", "admin"))
        assertNull(ExhibitionAmbient.forViewer(gabriel, "approved", "operator"))
        assertNull(ExhibitionAmbient.forViewer("Hever", "approved", "operator"))
        assertNull(ExhibitionAmbient.forViewer(null, "approved", "operator"))
        assertNull(ExhibitionAmbient.forViewer(hever, "pending", "operator"))
        assertNull(ExhibitionAmbient.forViewer(hever, "approved", "admin"))
    }

    @Test
    fun `changing accounts never retains the previous viewer examples`() {
        val firstView = ExhibitionAmbient.forViewer(hever, "approved", "operator")
        assertEquals(25.4, firstView!!.temperature, 0.0)
        assertEquals(62.0, firstView.airHumidity, 0.0)
        assertNull(firstView.lightLux)
        assertNull(ExhibitionAmbient.forViewer(null, null, null))
        assertNull(ExhibitionAmbient.forViewer(gabriel, "approved", "admin"))
    }

    @Test
    fun `light reference follows reported LED output including switched off`() {
        for ((power, expected) in listOf(0.0 to 0.0, 25.0 to 212.5, 50.0 to 425.0, 100.0 to 850.0)) {
            assertEquals(expected, ExhibitionAmbient.estimateLightLux(power, power > 0.0)!!, 0.0)
        }
    }

    @Test
    fun `missing invalid and contradictory LED reports do not produce an estimate`() {
        for (power in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 101.0)) {
            assertNull(ExhibitionAmbient.estimateLightLux(power, true))
            assertNull(ExhibitionAmbient.estimateLightLux(power, false))
        }
        assertNull(ExhibitionAmbient.estimateLightLux(50.0, null))
        assertNull(ExhibitionAmbient.estimateLightLux(50.0, false))
        assertNull(ExhibitionAmbient.estimateLightLux(0.0, true))
    }

    @Test
    fun `only current online telemetry changes the reference and raw light is preserved`() {
        val now = 1_787_515_200_000L
        val timestamp = "2026-08-23T20:00:00.000Z"
        val record = SensorRecord(createdAt = timestamp, lightLux = 999.0, ledPower = 25, ledOn = true)
        val control = DeviceControl(esp32Online = true, lastSeenAt = timestamp, ledPower = 100, ledTarget = true)
        val reference = ExhibitionAmbient.forViewer(hever, "approved", "operator")
        val rendered = ExhibitionAmbient.withCurrentTelemetry(reference, record, control, now)
        assertEquals(212.5, rendered!!.lightLux!!, 0.0)
        assertEquals(999.0, record.lightLux!!, 0.0)
        assertNull(reference!!.lightLux)
        assertNull(ExhibitionAmbient.withCurrentTelemetry(rendered, record, control, now + 30_001L)!!.lightLux)
        assertNull(ExhibitionAmbient.withCurrentTelemetry(rendered, record, control.copy(esp32Online = false), now)!!.lightLux)
        assertNull(ExhibitionAmbient.withCurrentTelemetry(rendered, null, control, now)!!.lightLux)
        assertNull(ExhibitionAmbient.withCurrentTelemetry(rendered, record.copy(ledPower = null), control, now)!!.lightLux)
        assertNull(ExhibitionAmbient.withCurrentTelemetry(rendered, record.copy(createdAt = null), control, now)!!.lightLux)
        assertNull(ExhibitionAmbient.withCurrentTelemetry(null, record, control, now))
        assertNull(ExhibitionAmbient.withCurrentTelemetry(
            ExhibitionAmbient.forViewer(gabriel, "approved", "admin"), record, control, now
        ))
    }
}
