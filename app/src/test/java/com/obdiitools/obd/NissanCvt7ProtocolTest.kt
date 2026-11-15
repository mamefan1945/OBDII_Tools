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
    fun parsesIndexedMultiFrameElmResponse() {
        val response = listOf(
            "07A",
            "0:610100000000",
            "1:00190500182E8A",
            "2:82B30003161905",
            "3:00182A5A370100",
            "4:00000000000000",
            "5:FD401760205A36",
            "6:01CE140A0000FE",
            "7:FEFE0000035203",
            "8:5303E800000000",
            "9:0351035403E700",
            "A:00190081000000",
            "B:80030006000004",
            "C:006D8000B22D6F",
            "D:00000000000000",
            "E:58054E4E084E3C",
            "F:1D000000000000",
            "0:5A000006800D00",
            "1:00400000AAAAAA",
        )
        requireNotNull(NissanCvt7Protocol.parseLiveData(response))
    }

    @Test
    fun rejectsNegativeOrTruncatedResponses() {
        assertNull(NissanCvt7Protocol.parseLiveData(listOf("7F2131")))
        assertNull(NissanCvt7Protocol.parseLiveData(listOf("6101" + "00".repeat(20))))
    }
}
