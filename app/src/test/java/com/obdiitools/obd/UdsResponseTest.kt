package com.obdiitools.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdsResponseTest {

    @Test
    fun positiveResponseMustMatchRequestedDid() {
        val valid = UdsResponse.parse("7E0", "F190", "62F190313233")
        assertTrue(valid.isSuccess)
        assertEquals("313233", valid.dataHex)

        val wrongDid = UdsResponse.parse("7E0", "F190", "62F187313233")
        assertFalse(wrongDid.isSuccess)
        assertEquals("UNK", wrongDid.errorCode)
    }

    @Test
    fun malformedResponseIsNotAccepted() {
        assertFalse(UdsResponse.parse("7E0", "F190", "62F1901").isSuccess)
    }

    @Test
    fun assemblesIndexedElmContinuationLinesForLongDidResponses() {
        val raw = "SEARCHING...\n0:62F1903132333435\n1:3637383941424344\n2:45464748"
        val response = UdsResponse.parse("7E0", "F190", raw)

        assertTrue(response.isSuccess)
        assertEquals("3132333435363738394142434445464748", response.dataHex)
    }

    @Test
    fun assemblesIndexedNegativeResponseAndPreservesNrc() {
        val response = UdsResponse.parse("7E1", "F190", "0:7F\n1:2211")

        assertFalse(response.isSuccess)
        assertEquals("11", response.errorCode)
    }
    @Test
    fun parsesOnlyValidSeventeenCharacterVinFromF190() {
        val vin = "1HGCM82633A004352"
        val hex = vin.toByteArray(Charsets.US_ASCII).joinToString("") { "%02X".format(it) }
        val response = UdsResponse.parse("7E0", "F190", "62F190$hex")

        assertEquals(vin, parseUdsVin(response))
    }

    @Test
    fun rejectsInvalidVinCharactersAndShortPayloads() {
        val invalid = "1HGCM82633I004352"
        val invalidHex = invalid.toByteArray(Charsets.US_ASCII).joinToString("") { "%02X".format(it) }
        val shortHex = "1HGCM82633A".toByteArray(Charsets.US_ASCII)
            .joinToString("") { "%02X".format(it) }

        assertEquals(null, parseUdsVin(UdsResponse.parse("7E0", "F190", "62F190$invalidHex")))
        assertEquals(null, parseUdsVin(UdsResponse.parse("7E0", "F190", "62F190$shortHex")))
        assertEquals(null, parseUdsVin(UdsResponse.parse("7E0", "F190", "7F2211")))
    }
    @Test
    fun ecuDiscoveryAcceptsStandardObdPidSupportResponse() {
        val result = parseEcuScanResult("7E0", "0:4100BE3EA813")
        assertTrue(result?.hasPositiveResponse == true)
        assertEquals("7E0", result?.address)
    }

    @Test
    fun ecuDiscoveryAcceptsUdsPositiveAndServiceSpecificNegativeResponses() {
        assertTrue(parseEcuScanResult("7E1", "5001")?.hasPositiveResponse == true)
        val negative = parseEcuScanResult("7E1", "7F1011")
        assertFalse(negative?.hasPositiveResponse ?: true)
        assertEquals("11", negative?.nrc)
    }

    @Test
    fun ecuDiscoveryDoesNotTreatUnrelatedNegativeResponseAsPresenceEvidence() {
        assertEquals(null, parseEcuScanResult("7E0", "7F2211"))
    }


}
