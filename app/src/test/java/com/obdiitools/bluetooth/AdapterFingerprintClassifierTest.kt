package com.obdiitools.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class AdapterFingerprintClassifierTest {
    @Test fun recognizesVeepeakObdCheckBlePlusAsProbableFromAdvertisedName() {
        val result = AdapterFingerprintClassifier.identify("OBDCheck BLE+", "ELM327 v1.5", "")
        assertEquals("VEEPEAK", result.manufacturer)
        assertEquals("OBDCheck BLE+", result.model)
        assertEquals("probable", result.confidence)
    }

    @Test fun explicitAtOneProductStringRaisesConfidence() {
        val result = AdapterFingerprintClassifier.identify("OBDII", "ELM327", "OBDLink MX+")
        assertEquals("OBDLink", result.manufacturer)
        assertEquals("MX+", result.model)
        assertEquals("confirmed", result.confidence)
    }

    @Test fun genericElm327DoesNotInventManufacturerOrModel() {
        val result = AdapterFingerprintClassifier.identify("OBDII", "ELM327 v2.1", "ELM327")
        assertEquals("", result.manufacturer)
        assertEquals("", result.model)
        assertEquals("unknown", result.confidence)
    }
}
