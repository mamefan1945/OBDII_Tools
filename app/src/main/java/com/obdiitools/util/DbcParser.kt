package com.obdiitools.util

import com.obdiitools.obd.DbcMessage
import com.obdiitools.obd.DbcSignal

object DbcParser {

    private val msgRegex = Regex("""^BO_\s+(\d+)\s+(\w+)\s*:\s*(\d+)""")

    // Matches: SG_ NAME [mux] : startBit|len@byteOrder sign (scale,offset) [min|max] "unit" nodes
    private val sigRegex = Regex(
        """^\s+SG_\s+(\w+)\s+(?:\w+\s+)?:\s*(\d+)\|(\d+)@([01])([+-])\s+\(([^,]+),([^)]+)\)\s+\[([^|]+)\|([^\]]+)\]\s+"([^"]*)"""
    )

    fun parse(text: String): Map<Long, Pair<DbcMessage, List<DbcSignal>>> {
        val messages = mutableMapOf<Long, DbcMessage>()
        val signals  = mutableMapOf<Long, MutableList<DbcSignal>>()
        var currentId = -1L

        for (line in text.lines()) {
            val msgMatch = msgRegex.find(line)
            if (msgMatch != null) {
                val id   = msgMatch.groupValues[1].toLong()
                val name = msgMatch.groupValues[2]
                val len  = msgMatch.groupValues[3].toInt()
                messages[id] = DbcMessage(id, name, len)
                signals[id]  = mutableListOf()
                currentId = id
                continue
            }
            if (currentId >= 0) {
                val sigMatch = sigRegex.find(line) ?: continue
                val g = sigMatch.groupValues
                signals[currentId]?.add(
                    DbcSignal(
                        messageId   = currentId,
                        messageName = messages[currentId]?.name ?: "",
                        name        = g[1],
                        startBit    = g[2].toInt(),
                        lengthBits  = g[3].toInt(),
                        isBigEndian = g[4] == "0",
                        isSigned    = g[5] == "-",
                        scale       = g[6].toDouble(),
                        offset      = g[7].toDouble(),
                        unit        = g[10],
                    )
                )
            }
        }

        return messages.mapValues { (id, msg) -> msg to (signals[id] ?: emptyList<DbcSignal>()) }
    }
}
