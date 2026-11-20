package com.obdiitools.util

import com.obdiitools.obd.DbcMessage
import com.obdiitools.obd.DbcSignal

object DbcParser {

    private val msgRegex = Regex("""^BO_\s+(\d+)\s+(\w+)\s*:\s*(\d+)""")
    private val sigRegex = Regex(
        """^\s+SG_\s+(\w+)\s+(?:(M|m(\d+))\s+)?:\s*(\d+)\|(\d+)@([01])([+-])\s+\(([^,]+),([^)]+)\)\s+\[([^|]+)\|([^\]]+)\]\s+"([^"]*)"""
    )

    fun parse(text: String): Map<Long, Pair<DbcMessage, List<DbcSignal>>> {
        val messages = linkedMapOf<Long, DbcMessage>()
        val signals = mutableMapOf<Long, MutableList<DbcSignal>>()
        var currentId: Long? = null

        for (line in text.lineSequence()) {
            val messageMatch = msgRegex.find(line)
            if (messageMatch != null) {
                val encodedId = messageMatch.groupValues[1].toLongOrNull()
                val name = messageMatch.groupValues[2]
                val length = messageMatch.groupValues[3].toIntOrNull()
                if (encodedId == null || length == null || encodedId !in 0..0xFFFFFFFFL || length !in 1..8) {
                    currentId = null
                    continue
                }
                // DBC marks extended CAN identifiers with bit 31. The actual
                // 29-bit arbitration ID is stored in the lower 29 bits.
                val id = if ((encodedId and 0x80000000L) != 0L) encodedId and 0x1FFFFFFFL else encodedId
                if (id !in 0..0x1FFFFFFF) {
                    currentId = null
                    continue
                }
                messages[id] = DbcMessage(id, name, length)
                signals[id] = mutableListOf()
                currentId = id
                continue
            }

            val id = currentId ?: continue
            val message = messages[id] ?: continue
            val signalMatch = sigRegex.find(line) ?: continue
            val groups = signalMatch.groupValues
            val signal = runCatching {
                val startBit = groups[4].toInt()
                val lengthBits = groups[5].toInt()
                val scale = groups[8].toDouble()
                val offset = groups[9].toDouble()
                require(startBit >= 0 && lengthBits in 1..64)
                require(scale.isFinite() && offset.isFinite())
                require(startBit < message.lengthBytes * 8)
                val bigEndian = groups[6] == "0"
                if (!bigEndian) require(startBit + lengthBits <= message.lengthBytes * 8)
                else require(motorolaSignalFits(startBit, lengthBits, message.lengthBytes))
                DbcSignal(
                    messageId = id,
                    messageName = message.name,
                    name = groups[1],
                    startBit = startBit,
                    lengthBits = lengthBits,
                    isBigEndian = bigEndian,
                    isSigned = groups[7] == "-",
                    scale = scale,
                    offset = offset,
                    unit = groups[12],
                    isMultiplexer = groups[2] == "M",
                    multiplexerValue = groups[3].takeIf { it.isNotBlank() }?.toIntOrNull(),
                )
            }.getOrNull()
            if (signal != null) signals.getValue(id).add(signal)
        }

        return messages.mapValues { (id, message) -> message to signals.getValue(id).toList() }
    }

    private fun motorolaSignalFits(startBit: Int, lengthBits: Int, messageLengthBytes: Int): Boolean {
        var bitPos = startBit
        repeat(lengthBits) {
            if (bitPos < 0 || bitPos / 8 >= messageLengthBytes) return false
            bitPos = if (bitPos % 8 == 0) bitPos + 15 else bitPos - 1
        }
        return true
    }
}
