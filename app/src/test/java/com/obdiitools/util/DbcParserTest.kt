package com.obdiitools.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DbcParserTest {

    @Test
    fun parsesValidMessageAndSignal() {
        val dbc = """
BO_ 291 TEST: 2 ECU
 SG_ SPEED : 0|16@1+ (0.1,0) [0|6553] "km/h" ECU
""".trimIndent()
        val parsed = DbcParser.parse(dbc)
        assertEquals(1, parsed.size)
        val message = parsed.getValue(291L).first
        assertEquals(2, message.lengthBytes)
        assertEquals("SPEED", parsed.getValue(291L).second.single().name)
    }

    @Test
    fun skipsMalformedMessageAndOutOfBoundsSignal() {
        val dbc = """
BO_ 1 INVALID: 9 ECU
 SG_ TOO_LONG : 0|64@1+ (1,0) [0|1] "" ECU
BO_ 2 VALID: 1 ECU
 SG_ OUTSIDE : 7|2@1+ (1,0) [0|3] "" ECU
""".trimIndent()
        val parsed = DbcParser.parse(dbc)
        assertEquals(setOf(2L), parsed.keys)
        assertTrue(parsed.getValue(2L).second.isEmpty())
    }
}
