package com.obdiitools.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvSafeCellTest {

    @Test
    fun neutralizesSpreadsheetFormulaPrefixes() {
        assertEquals("\"'=1+1\"", csvSafeCell("=1+1"))
        assertEquals("\"'  @SUM(A1:A2)\"", csvSafeCell("  @SUM(A1:A2)"))
    }

    @Test
    fun quotesAndEscapesCsvText() {
        assertEquals("\"O\"\"Brien\"", csvSafeCell("O\"Brien"))
    }
}
