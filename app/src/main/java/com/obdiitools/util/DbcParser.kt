package com.obdiitools.util

import com.obdiitools.obd.DbcMessage
import com.obdiitools.obd.DbcSignal

object DbcParser {

    private val msgRegex = Regex("""^BO_\s+(\d+)\s+(\w+)\s*:\s*(\d+)""")
    private val sigRegex = Regex(
        """^\s+SG_\s+(\w+)\s+(?:\w+\s+)?:\s*(\d+)\|(\d+)@([01])([+-])\s+\(([^,]+),([^)]+)\)\s+\[([^|]+)\|([^\]]+)\]\s+"([^"]*)"""
    )

    fun parse(text: String): Map<Long, Pair<DbcMessage, List<DbcSignal>>> {
        val messages = linkedMapOf<Long, DbcMessage>()
        val signals = mutableMapOf<Long, MutableList<DbcSignal>>()
        var currentId: Long? = null

        for (line in text.lineSequence()) {
            val messageMatch = msgRegex.find(line)
            if (messageMatch != null) {
                val id = messageMatch.groupValues[1].toLongOrNull()
                val name = messageMatch.groupValues[2]
                val length = messageMatch.groupValues[3].toIntOrNull()
                if (id == null || length == null || id !in 0..0x1FFFFFFF || length !in 1..8) {
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
                val startBit = groups[2].toInt()
                val lengthBits = groups[3].toInt()
                val scale = groups[6].toDouble()
                val offset = groups[7].toDouble()
                require(startBit >= 0 && lengthBits in 1..64)
                require(scale.isFinite() && offset.isFinite())
                require(startBit < message.lengthBytes * 8)
                val bigEndian = groups[4] == "0"
                if (!bigEndian) require(startBit + lengthBits <= message.lengthBytes * 8)
                else require(motorolaSignalFits(startBit, lengthBits, message.lengthBytes))
                DbcSignal(
                    messageId = id,
                    messageName = message.name,
                    name = groups[1],
                    startBit = startBit,
                    lengthBits = lengthBits,
                    isBigEndian = bigEndian,
                    isSigned = groups[5] == "-",
                    scale = scale,
                    offset = offset,
                    unit = groups[10],
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
