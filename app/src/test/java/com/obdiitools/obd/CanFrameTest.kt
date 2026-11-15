package com.obdiitools.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanFrameTest {

    @Test
    fun parsesValidCanFrame() {
        val frame = CanFrame.parse("7E8 41 0C 1A F8")!!
        assertEquals("7E8", frame.id)
        assertEquals(listOf(0x41, 0x0C, 0x1A, 0xF8), frame.data)
    }

    @Test
    fun rejectsMalformedOrTruncatedFrames() {
        assertNull(CanFrame.parse("7E8 41 ZZ F8"))
        assertNull(CanFrame.parse("7E8"))
        assertNull(CanFrame.parse("7E8 41 0C 1"))
        assertNull(CanFrame.parse("7E8 00 01 02 03 04 05 06 07 08"))
        assertNull(CanFrame.parse("NOTCAN 41 0C"))
    }
}
