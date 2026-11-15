package com.obdiitools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPidDefinitionTest {

    private fun definition(command: String, formula: FormulaType) =
        CustomPidDefinition("test", "Test PID", command, "", formula)

    @Test
    fun onlyMode01SinglePidCommandsAreAccepted() {
        assertTrue(CustomPidDefinition.isValidCommand("010C"))
        assertTrue(CustomPidDefinition.isValidCommand("01ff"))
        assertFalse(CustomPidDefinition.isValidCommand("ATZ"))
        assertFalse(CustomPidDefinition.isValidCommand("010C;ATZ"))
        assertFalse(CustomPidDefinition.isValidCommand("220C"))
        assertFalse(CustomPidDefinition.isValidCommand("010"))
    }

    @Test
    fun evaluatesOnlyMatchingPositiveResponse() {
        assertEquals(123f, definition("0105", FormulaType.RAW_A).evaluate("41057B")!!, 0f)
        assertNull(definition("0105", FormulaType.RAW_A).evaluate("410C7B"))
        assertNull(definition("0105", FormulaType.RAW_A).evaluate("NODATA"))
        assertNull(definition("0105", FormulaType.RAW_A).evaluate("41057"))
    }

    @Test
    fun evaluatesTwoByteValueAfterResponseHeader() {
        assertEquals(1726f, definition("010C", FormulaType.TWO_BYTE_DIV4).evaluate("41 0C 1A F8")!!, 0f)
        assertNull(definition("010C", FormulaType.TWO_BYTE_DIV4).evaluate("410C1A"))
    }
}
