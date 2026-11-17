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
}
