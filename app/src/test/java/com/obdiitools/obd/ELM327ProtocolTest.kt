package com.obdiitools.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class ELM327ProtocolTest {

    @Test
    fun parsesCompactMultiPidLines() {
        val values = ELM327Protocol.parseMultiPidResponse(
            listOf("410C1AF8", "410D32", "41111A")
        )
        assertEquals(1726, values["010C"])
        assertEquals(50, values["010D"])
        assertEquals(10.196078f, values["0111"] as Float, 0.0001f)
    }

    @Test
    fun parsesSpacedAndIndexedMultiPidLines() {
        val values = ELM327Protocol.parseMultiPidResponse(
            listOf("0: 41 0C 1A F8", "1:41 0D 32", "SEARCHING...")
        )
        assertEquals(1726, values["010C"])
        assertEquals(50, values["010D"])
    }

    @Test
    fun preservesNullForMalformedKnownPidSoCallerCanClearStaleValue() {
        val values = ELM327Protocol.parseMultiPidResponse(listOf("410C"))
        assertEquals(true, values.containsKey("010C"))
        assertEquals(null, values["010C"])
    }

    @Test
    fun ignoresUnknownPidsAndAdapterNoise() {
        val values = ELM327Protocol.parseMultiPidResponse(
            listOf("NO DATA", "7E8 04 41 0C 1A F8", "41FF00")
        )
        assertEquals(1726, values["010C"])
        assertEquals(false, values.containsKey("01FF"))
    }

    @Test
    fun fuelEconomyPidsAreStillPolledWhenOmittedFromSupportBitmap() {
        val requested = listOf(
            ELM327Protocol.PID.RPM,
            ELM327Protocol.PID.MAF,
            ELM327Protocol.PID.FUEL_LEVEL,
            ELM327Protocol.PID.OIL_TEMP,
        )
        val supported = setOf(ELM327Protocol.PID.RPM, ELM327Protocol.PID.OIL_TEMP)

        assertEquals(
            listOf(ELM327Protocol.PID.RPM, ELM327Protocol.PID.MAF, ELM327Protocol.PID.FUEL_LEVEL, ELM327Protocol.PID.OIL_TEMP),
            ELM327Protocol.filterSupportedPids(requested, supported),
        )
    }

    @Test
    fun supportedPidFilterPreservesAllRequestsWhenBitmapIsEmpty() {
        val requested = listOf(ELM327Protocol.PID.RPM, ELM327Protocol.PID.MAF)
        assertEquals(requested, ELM327Protocol.filterSupportedPids(requested, emptySet()))
    }

    @Test
    fun parsesConcatenatedMultiPidResponsesOnOneLine() {
        val values = ELM327Protocol.parseMultiPidResponse(
            listOf("410C1AF8410D3241111A41054A41042B41101234")
        )
        assertEquals(1726, values["010C"])
        assertEquals(50, values["010D"])
        assertEquals(10.196078f, values["0111"] as Float, 0.0001f)
        assertEquals(34, values["0105"])
        assertEquals(46.6f, values["0110"] as Float, 0.001f)
    }

    @Test
    fun parsesConcatenatedResponsesWithSeparatorsAndIndexes() {
        val values = ELM327Protocol.parseMultiPidResponse(
            listOf("0: 41 0C 1A F8 41 0D 32", "1: 41 10 12 34")
        )
        assertEquals(1726, values["010C"])
        assertEquals(50, values["010D"])
        assertEquals(46.6f, values["0110"] as Float, 0.001f)
    }

}
