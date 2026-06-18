package com.obdiitools.util

import com.obdiitools.obd.DbcMessage
import com.obdiitools.obd.DbcSignal
import com.obdiitools.obd.DecodedSignal

object CanDecoder {

    fun decode(
        frameIdHex: String,
        data: List<Int>,
        signalMap: Map<Long, Pair<DbcMessage, List<DbcSignal>>>,
    ): List<DecodedSignal> {
        val frameId = frameIdHex.toLongOrNull(16) ?: return emptyList()
        val (_, signals) = signalMap[frameId] ?: return emptyList()
        return signals.mapNotNull { sig ->
            try {
                val raw = if (sig.isBigEndian)
                    extractMotorola(data, sig.startBit, sig.lengthBits)
                else
                    extractIntel(data, sig.startBit, sig.lengthBits)
                val signed = if (sig.isSigned) applySign(raw, sig.lengthBits) else raw
                val value  = signed.toDouble() * sig.scale + sig.offset
                DecodedSignal(sig.name, sig.messageName, frameId, value, sig.unit, sig.scale)
            } catch (_: Exception) { null }
        }
    }

    // Intel (little-endian, @1): startBit is the LSB position.
    // Bits are numbered sequentially across bytes (byte 0 = bits 0-7, byte 1 = bits 8-15, …).
    private fun extractIntel(data: List<Int>, startBit: Int, length: Int): Long {
        var result = 0L
        for (i in 0 until length) {
            val bitPos  = startBit + i
            val byteIdx = bitPos / 8
            val bitIdx  = bitPos % 8
            if (byteIdx < data.size) {
                val bit = (data[byteIdx] and 0xFF ushr bitIdx) and 1
                result = result or (bit.toLong() shl i)
            }
        }
        return result
    }

    // Motorola (big-endian, @0): startBit is the MSB position in DBC bit numbering
    // (byte N occupies DBC bits 8N+7 down to 8N).
    // Traversal: descend within byte, then wrap to MSB of the next byte.
    private fun extractMotorola(data: List<Int>, startBit: Int, length: Int): Long {
        var result = 0L
        var bitPos = startBit
        for (i in 0 until length) {
            val byteIdx = bitPos / 8
            val bitIdx  = bitPos % 8
            if (byteIdx < data.size) {
                val bit = (data[byteIdx] and 0xFF ushr bitIdx) and 1
                result = result or (bit.toLong() shl (length - 1 - i))
            }
            // At the byte's LSB (bit 0 of byte), wrap to the MSB of the next byte.
            bitPos = if (bitPos % 8 == 0) bitPos + 15 else bitPos - 1
        }
        return result
    }

    private fun applySign(raw: Long, length: Int): Long {
        val msb = 1L shl (length - 1)
        return if (raw and msb != 0L) raw - (1L shl length) else raw
    }
}
