package com.obdiitools.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MazdaCx5ProtocolTest {
    private fun response(did: String, payload: String) =
        UdsResponse.parse("7E1", did, "62$did$payload")

    @Test
    fun decodesForwardAndNonForwardGearStates() {
        assertEquals("1", MazdaCx5Protocol.parseGear(response("1E12", "01")))
        assertEquals("6", MazdaCx5Protocol.parseGear(response("1E12", "06")))
        assertEquals("N", MazdaCx5Protocol.parseGear(response("1E12", "50")))
        assertEquals("R", MazdaCx5Protocol.parseGear(response("1E12", "60")))
        assertEquals("P", MazdaCx5Protocol.parseGear(response("1E12", "70")))
        assertNull(MazdaCx5Protocol.parseGear(response("1E12", "00")))
        assertNull(MazdaCx5Protocol.parseGear(response("1E12", "08")))
    }

    @Test
    fun decodesSignedSixteenthDegreeTemperature() {
        assertEquals(20.9375, MazdaCx5Protocol.parseTransmissionTemperatureC(response("1E1C", "014F"))!!, 0.00001)
        assertEquals(-1.0, MazdaCx5Protocol.parseTransmissionTemperatureC(response("1E1C", "FFF0"))!!, 0.00001)
    }

    @Test
    fun rejectsWrongDidNegativeAndShortResponses() {
        assertNull(MazdaCx5Protocol.parseGear(response("1E1C", "01")))
        assertNull(MazdaCx5Protocol.parseGear(UdsResponse.parse("7E1", "1E12", "7F2211")))
        assertNull(MazdaCx5Protocol.parseTransmissionTemperatureC(response("1E1C", "01")))
    }
}
