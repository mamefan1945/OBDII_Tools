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
    fun decodesVerifiedCvt7FieldsFromCapturedResponse() {
        val live = NissanCvt7Protocol.parseLiveData(capturedResponse())

        assertEquals(0, live?.vehicleSpeedKph)
        assertEquals(768, live?.engineRpm)
        assertEquals(42, live?.slipRpm)
        assertEquals(56, live?.fluidTemperatureC)
        assertEquals(2.475f, live?.gearRatio ?: 0f, 0.0001f)
        assertEquals(0.25f, live?.primaryPressureMpa ?: 0f, 0.0001f)
        assertEquals(0.575f, live?.secondaryPressureMpa ?: 0f, 0.0001f)
        assertEquals(0.5f, live?.targetSecondaryPressureMpa ?: 0f, 0.0001f)
        assertEquals(0.5f, live?.linePressureMpa ?: 0f, 0.0001f)
    }

    @Test
    fun rejectsIncompleteOrUnframedCvt7Responses() {
        val capture = capturedResponse()
        assertNull(NissanCvt7Protocol.parseLiveData(capture.take(10)))
        assertNull(NissanCvt7Protocol.parseLiveData(capture.drop(1)))
        assertNull(NissanCvt7Protocol.parseLiveData(listOf("07A", "0:7F2131")))
    }

    @Test
    fun auxiliaryByte86DoesNotInvalidateCoreTelemetry() {
        val payload = capturedResponse().drop(1)
            .joinToString("") { it.substringAfter(':') }
            .chunked(2)
            .toMutableList()
        payload[86] = "05"
        val invalid = listOf("07A") + payload.chunked(7).mapIndexed { index, bytes ->
            "${index.toString(16).uppercase()}:${bytes.joinToString("")}"
        }

        val live = NissanCvt7Protocol.parseLiveData(invalid)
        assertTrue(live != null)
        assertEquals(768, live?.engineRpm)
    }

    @Test
    fun decodesNegativeSignedSlipRpm() {
        val payload = capturedResponse().drop(1)
            .joinToString("") { it.substringAfter(':') }
            .chunked(2)
            .toMutableList()
        payload[22] = "FE"
        val response = listOf("07A") + payload.chunked(7).mapIndexed { index, bytes ->
            "${index.toString(16).uppercase()}:${bytes.joinToString("")}"
        }

        assertEquals(-2, NissanCvt7Protocol.parseLiveData(response)?.slipRpm)
    }

    @Test
    fun parsesNissanCvt7DtcRecordsFromIndexedElmResponse() {
        val codes = NissanCvt7Protocol.parseCvtDtcResponse(
            listOf("07A0:5902FF06150000", "1:07400000")
        )
        assertEquals(listOf("P0615", "P0740"), codes)
    }

    @Test
    fun decodesAllStandardDtcFamiliesFromCvtResponse() {
        val codes = NissanCvt7Protocol.parseCvtDtcResponse(
            listOf("5902FFC00000008000000040000000")
        )
        assertEquals(listOf("U1000", "B0000", "C0000"), codes)
    }

    @Test
    fun distinguishesNoCodesFromInvalidCvtDtcResponse() {
        assertEquals(emptyList<String>(), NissanCvt7Protocol.parseCvtDtcResponse(listOf("5902FF")))
        assertNull(NissanCvt7Protocol.parseCvtDtcResponse(listOf("7F1912")))
        assertNull(NissanCvt7Protocol.parseCvtDtcResponse(listOf("5902FF061500")))
    }

    @Test
    fun rejectsNegativeOrTruncatedResponses() {
        assertFalse(NissanCvt7Protocol.hasPositiveResponse(listOf("7F2131"), "6101"))
        assertNull(NissanCvt7Protocol.parseLiveData(listOf("7F2131")))
        assertNull(NissanCvt7Protocol.parseLiveData(listOf("07A", "0:6101" + "00".repeat(20))))
    }

    private fun capturedResponse(): List<String> = listOf(
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
}
