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
}
