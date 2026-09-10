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
        assertEquals(850.0, firstView.lightLux, 0.0)
        assertNull(ExhibitionAmbient.forViewer(null, null, null))
        assertNull(ExhibitionAmbient.forViewer(gabriel, "approved", "admin"))
    }
}
