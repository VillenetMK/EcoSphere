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
}
