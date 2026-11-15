package com.obdiitools.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun recognizesPositiveResponseInsideIndexedIsoTpOutput() {
        val response = listOf("07A", "0:610100000000", "1:00190500182E8A")
        assertTrue(NissanCvt7Protocol.hasPositiveResponse(response, "6101"))
        assertFalse(NissanCvt7Protocol.hasPositiveResponse(response, "6103"))
    }

    @Test
    fun refusesToDecodeCapturedResponseWithUnverifiedFieldOffsets() {
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
        assertTrue(NissanCvt7Protocol.hasPositiveResponse(response, "6101"))
        assertNull(NissanCvt7Protocol.parseLiveData(response))
    }

    @Test
    fun rejectsNegativeOrTruncatedResponses() {
        assertFalse(NissanCvt7Protocol.hasPositiveResponse(listOf("7F2131"), "6101"))
        assertNull(NissanCvt7Protocol.parseLiveData(listOf("7F2131")))
        assertNull(NissanCvt7Protocol.parseLiveData(listOf("6101" + "00".repeat(20))))
    }
}
