package com.obdiitools.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NissanCvt7ProtocolTest {
    @Test
    fun recognizesKnownTransmissionPartNumberFromAsciiDid() {
        val partNumber = "31036-4AT0C"
        val encoded = partNumber.toByteArray(Charsets.US_ASCII)
            .joinToString("") { "%02X".format(it) }
        assertEquals(true, NissanCvt7Protocol.isExpectedTcmIdentity(listOf("62F187$encoded")))
        assertEquals(true, NissanCvt7Protocol.isExpectedTcmIdentity(listOf("04", "62 F1 87 $encoded")))
    }

    @Test
    fun doesNotTreatOtherModuleIdentifiersAsTransmission() {
        val otherPart = "23710-4AT0C".toByteArray(Charsets.US_ASCII)
            .joinToString("") { "%02X".format(it) }
        assertEquals(false, NissanCvt7Protocol.isExpectedTcmIdentity(listOf("62F187$otherPart")))
        assertEquals(false, NissanCvt7Protocol.isExpectedTcmIdentity(listOf("7F2231")))
    }

    @Test
    fun decodesLiveDataFieldsFromPositiveResponse() {
        val bytes = IntArray(76)
        bytes[7] = 22
        bytes[12] = 3
        bytes[18] = 123
        bytes[29] = 20
        bytes[30] = 12
        bytes[31] = 174
        bytes[37] = 20
        bytes[38] = 20
        val payload = bytes.joinToString("") { "%02X".format(it) }

        val decoded = NissanCvt7Protocol.parseLiveData(listOf("6101$payload"))

        requireNotNull(decoded)
        assertEquals(3, decoded.vehicleSpeedKph)
        assertEquals(704, decoded.engineRpm)
        assertEquals(90, decoded.fluidTemperatureC)
        assertEquals(1.23f, decoded.gearRatio, 0.001f)
        assertEquals(0.30f, decoded.primaryPressureMpa, 0.001f)
        assertEquals(0.50f, decoded.secondaryPressureMpa, 0.001f)
        assertEquals(0.50f, decoded.targetSecondaryPressureMpa, 0.001f)
        assertEquals(0.50f, decoded.linePressureMpa, 0.001f)
    }

    @Test
    fun toleratesTransportLengthPrefixBeforeServiceResponse() {
        val payload = "00".repeat(76)
        val decoded = NissanCvt7Protocol.parseLiveData(listOf("0466101$payload"))
        requireNotNull(decoded)
        assertEquals(0, decoded.engineRpm)
    }

    @Test
    fun rejectsNegativeOrTruncatedResponses() {
        assertNull(NissanCvt7Protocol.parseLiveData(listOf("7F2131")))
        assertNull(NissanCvt7Protocol.parseLiveData(listOf("6101" + "00".repeat(20))))
    }
}
