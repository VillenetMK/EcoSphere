package com.example.ecosphere.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlPolicyTest {

    @Test
    fun `manual irrigation requires valid soil and water readings`() {
        assertEquals(
            IrrigationBlockReason.MISSING_SOIL_READING,
            ControlPolicy.irrigationDecision(null, "high").reason
        )
        assertEquals(
            IrrigationBlockReason.MISSING_WATER_READING,
            ControlPolicy.irrigationDecision(30.0, null).reason
        )
        assertEquals(
            IrrigationBlockReason.MISSING_WATER_READING,
            ControlPolicy.irrigationDecision(30.0, "medium").reason
        )
    }

    @Test
    fun `manual irrigation is denied at sixty percent soil humidity`() {
        val decision = ControlPolicy.irrigationDecision(60.0, "high")
        assertFalse(decision.allowed)
        assertEquals(IrrigationBlockReason.SOIL_TOO_WET, decision.reason)
    }

    @Test
    fun `manual irrigation is denied when the single float reports low`() {
        val decision = ControlPolicy.irrigationDecision(25.0, "low")
        assertFalse(decision.allowed)
        assertEquals(IrrigationBlockReason.LOW_WATER, decision.reason)
    }

    @Test
    fun `manual irrigation is allowed only with available water and safe soil humidity`() {
        assertTrue(ControlPolicy.irrigationDecision(35.0, "high").allowed)
        assertTrue(ControlPolicy.irrigationDecision(59.9, "HIGH").allowed)
    }

    @Test
    fun `power is always constrained to zero through one hundred`() {
        assertEquals(0, ControlPolicy.clampPower(-20))
        assertEquals(55, ControlPolicy.clampPower(55))
        assertEquals(100, ControlPolicy.clampPower(120))
    }

    @Test
    fun `device online state respects heartbeat timeout`() {
        val lastSeenMillis = 1_787_515_200_000L
        val control = DeviceControl(
            esp32Online = true,
            lastSeenAt = "2026-08-23T20:00:00.000Z"
        )

        assertTrue(control.isOnlineNow(nowMillis = lastSeenMillis + 29_999L))
        assertFalse(control.isOnlineNow(nowMillis = lastSeenMillis + 30_001L))
        assertFalse(control.copy(esp32Online = false).isOnlineNow(nowMillis = lastSeenMillis))
    }

    @Test
    fun `telemetry freshness respects the same timeout`() {
        val createdAt = "2026-08-23T20:00:00.000Z"
        val timestamp = 1_787_515_200_000L

        assertTrue(ControlPolicy.isTelemetryFresh(createdAt, timestamp + 29_999L))
        assertFalse(ControlPolicy.isTelemetryFresh(createdAt, timestamp + 30_001L))
        assertFalse(ControlPolicy.isTelemetryFresh(null, timestamp))
    }

    @Test
    fun `current telemetry requires a fresh reading and a live controller`() {
        val timestamp = 1_787_515_200_000L
        val record = SensorRecord(
            createdAt = "2026-08-23T20:00:00.000Z",
            soilHumidity = 7.0,
            waterLevel = "low"
        )
        val control = DeviceControl(
            esp32Online = true,
            lastSeenAt = "2026-08-23T20:00:00.000Z"
        )

        assertEquals(record, ControlPolicy.currentTelemetry(record, control, timestamp + 29_999L))
        assertEquals(
            null,
            ControlPolicy.currentTelemetry(record, control.copy(esp32Online = false), timestamp)
        )
        assertEquals(
            null,
            ControlPolicy.currentTelemetry(record, control, timestamp + 30_001L)
        )
    }

    @Test
    fun `actuator labels describe controller outputs without claiming physical presence`() {
        assertEquals("SALIDA PWM 100 %", ControlPolicy.actuatorPwmLabel(true, 100))
        assertEquals("SALIDA PWM 0 %", ControlPolicy.actuatorPwmLabel(false, 0))
        assertEquals("SIN REGISTRO", ControlPolicy.actuatorPwmLabel(null, null))
        assertEquals("SALIDA ACTIVA", ControlPolicy.actuatorSwitchLabel(true))
        assertEquals("SALIDA INACTIVA", ControlPolicy.actuatorSwitchLabel(false))
    }
}
