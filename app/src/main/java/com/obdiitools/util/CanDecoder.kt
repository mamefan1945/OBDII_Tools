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
        if (frameIdHex.isEmpty() || !frameIdHex.all { it in '0'..'9' || it.uppercaseChar() in 'A'..'F' }) return emptyList()
        val frameId = frameIdHex.toLongOrNull(16) ?: return emptyList()
        val (message, signals) = signalMap[frameId] ?: return emptyList()
        if (message.lengthBytes !in 1..8 || data.size != message.lengthBytes ||
            data.any { it !in 0..255 }) return emptyList()

        return signals.mapNotNull { signal ->
            if (signal.lengthBits !in 1..64 || signal.startBit < 0 ||
                !signal.scale.isFinite() || !signal.offset.isFinite()) return@mapNotNull null
            try {
                val raw = if (signal.isBigEndian) {
                    extractMotorola(data, signal.startBit, signal.lengthBits)
                } else {
                    if (signal.startBit + signal.lengthBits > data.size * 8) return@mapNotNull null
                    extractIntel(data, signal.startBit, signal.lengthBits)
                }
                val numericRaw = when {
                    signal.isSigned -> applySign(raw, signal.lengthBits).toDouble()
                    signal.lengthBits == 64 -> unsignedLongToDouble(raw)
                    else -> raw.toDouble()
                }
                val value = numericRaw * signal.scale + signal.offset
                if (!value.isFinite()) null
                else DecodedSignal(signal.name, signal.messageName, frameId, value, signal.unit, signal.scale)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IndexOutOfBoundsException) {
                null
            }
        }
    }

    // Intel (little-endian, @1): startBit is the LSB position.
    private fun extractIntel(data: List<Int>, startBit: Int, length: Int): Long {
        var result = 0L
        for (i in 0 until length) {
            val bitPos = startBit + i
            val byteIdx = bitPos / 8
            val bitIdx = bitPos % 8
            require(byteIdx in data.indices)
            val bit = ((data[byteIdx] and 0xFF) ushr bitIdx) and 1
            result = result or (bit.toLong() shl i)
        }
        return result
    }

    // Motorola (big-endian, @0): startBit is the MSB position in DBC bit numbering.
    private fun extractMotorola(data: List<Int>, startBit: Int, length: Int): Long {
        var result = 0L
        var bitPos = startBit
        for (i in 0 until length) {
            val byteIdx = bitPos / 8
            val bitIdx = bitPos % 8
            require(byteIdx in data.indices && bitPos >= 0)
            val bit = ((data[byteIdx] and 0xFF) ushr bitIdx) and 1
            result = result or (bit.toLong() shl (length - 1 - i))
            bitPos = if (bitPos % 8 == 0) bitPos + 15 else bitPos - 1
        }
        return result
    }

    private fun applySign(raw: Long, length: Int): Long {
        if (length == 64) return raw // already represented as a signed two's-complement Long
        val msb = 1L shl (length - 1)
        return if (raw and msb != 0L) raw or (-1L shl length) else raw
    }

    private fun unsignedLongToDouble(raw: Long): Double {
        val bytes = ByteArray(8) { index ->
            (raw ushr (56 - index * 8)).toByte()
        }
        return java.math.BigInteger(1, bytes).toDouble()
    }
}
