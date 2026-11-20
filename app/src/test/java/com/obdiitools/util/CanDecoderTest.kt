package com.obdiitools.util

import com.obdiitools.obd.DbcMessage
import com.obdiitools.obd.DbcSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanDecoderTest {

    private fun signal(start: Int, length: Int, bigEndian: Boolean) = DbcSignal(
        messageId = 0x123,
        messageName = "TEST",
        name = "VALUE",
        startBit = start,
        lengthBits = length,
        isBigEndian = bigEndian,
        isSigned = false,
        scale = 1.0,
        offset = 0.0,
        unit = "",
    )

    @Test
    fun decodesIntelByteWithCorrectBitOrder() {
        val message = DbcMessage(0x123, "TEST", 1)
        val result = CanDecoder.decode("123", listOf(0xA5), mapOf(0x123L to (message to listOf(signal(0, 8, false)))))
        assertEquals(165.0, result.single().value, 0.0)
    }

    @Test
    fun decodesMotorolaByteAndCrossByteSignal() {
        val oneByte = DbcMessage(0x123, "TEST", 1)
        val byteResult = CanDecoder.decode("123", listOf(0xA5), mapOf(0x123L to (oneByte to listOf(signal(7, 8, true)))))
        assertEquals(165.0, byteResult.single().value, 0.0)

        val twoBytes = DbcMessage(0x123, "TEST", 2)
        val wordResult = CanDecoder.decode("123", listOf(0x12, 0x34), mapOf(0x123L to (twoBytes to listOf(signal(7, 16, true)))))
        assertEquals(0x1234.toDouble(), wordResult.single().value, 0.0)
    }

    @Test
    fun rejectsInvalidFrameLengthAndSignalDefinitions() {
        val message = DbcMessage(0x123, "TEST", 2)
        val invalid = signal(12, 16, false)
        assertTrue(CanDecoder.decode("123", listOf(0x00), mapOf(0x123L to (message to listOf(invalid)))).isEmpty())
        assertTrue(CanDecoder.decode("123", listOf(0x00, 0x00), mapOf(0x123L to (message to listOf(invalid)))).isEmpty())
    }
    @Test
    fun decodesUnsignedAndSignedSixtyFourBitSignals() {
        val message = DbcMessage(0x123, "TEST", 8)
        val unsigned = signal(0, 64, false)
        val unsignedResult = CanDecoder.decode(
            "123",
            List(8) { 0xFF },
            mapOf(0x123L to (message to listOf(unsigned))),
        )
        assertEquals(18446744073709551615.0, unsignedResult.single().value, 4096.0)

        val signed = signal(0, 64, false).copy(isSigned = true)
        val signedResult = CanDecoder.decode(
            "123",
            listOf(0x80, 0, 0, 0, 0, 0, 0, 0),
            mapOf(0x123L to (message to listOf(signed))),
        )
        assertEquals(-9223372036854775808.0, signedResult.single().value, 0.0)
    }

    @Test
    fun signExtendsSignedSixtyThreeBitSignalWithoutOverflow() {
        val message = DbcMessage(0x123, "TEST", 8)
        val signed63 = signal(0, 63, false).copy(isSigned = true)
        val result = CanDecoder.decode(
            "123",
            listOf(0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF),
            mapOf(0x123L to (message to listOf(signed63))),
        )
        assertEquals(-1.0, result.single().value, 0.0)
    }

}
