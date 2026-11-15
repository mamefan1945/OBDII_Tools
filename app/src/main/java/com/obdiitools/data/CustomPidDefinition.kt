package com.obdiitools.data

enum class FormulaType(val label: String) {
    RAW_A("Byte A (raw)"),
    A_MINUS_40("Byte A − 40 (temperature)"),
    A_PERCENT("Byte A × 100/255 (percent)"),
    A_DIV2_MINUS64("Byte A ÷ 2 − 64 (timing)"),
    TWO_BYTE_DIV4("(A×256+B) ÷ 4 (RPM-style)"),
    TWO_BYTE_DIV100("(A×256+B) ÷ 100 (MAF-style)"),
}

data class CustomPidDefinition(
    val id: String,
    val name: String,
    val command: String,
    val unit: String,
    val formula: FormulaType,
) {
    fun evaluate(rawResponse: String): Float? {
        val normalizedCommand = command.trim().uppercase()
        if (!isValidCommand(normalizedCommand)) return null
        val cleaned = rawResponse.replace(Regex("\\s+"), "").uppercase()
        if (cleaned.isEmpty() || cleaned.contains("NODATA") ||
            cleaned.contains("ERROR") || cleaned.contains("?")) return null
        if (cleaned.length % 2 != 0 || !cleaned.all { it in '0'..'9' || it in 'A'..'F' }) return null
        val expectedPrefix = "41" + normalizedCommand.substring(2)
        if (!cleaned.startsWith(expectedPrefix)) return null
        val data = cleaned.substring(expectedPrefix.length)
        val needsTwoBytes = formula == FormulaType.TWO_BYTE_DIV4 || formula == FormulaType.TWO_BYTE_DIV100
        if (needsTwoBytes && data.length < 4) return null
        return try {
            val a = data.substring(0, 2).toInt(16)
            val b = if (data.length >= 4) data.substring(2, 4).toInt(16) else 0
            when (formula) {
                FormulaType.RAW_A           -> a.toFloat()
                FormulaType.A_MINUS_40      -> (a - 40).toFloat()
                FormulaType.A_PERCENT       -> a * 100f / 255f
                FormulaType.A_DIV2_MINUS64  -> a / 2f - 64f
                FormulaType.TWO_BYTE_DIV4   -> (a * 256 + b) / 4f
                FormulaType.TWO_BYTE_DIV100 -> (a * 256 + b) / 100f
            }
        } catch (e: Exception) { null }
    }

    companion object {
        /** Custom formulas currently support standard Mode 01 single-PID requests only. */
        fun isValidCommand(command: String): Boolean =
            command.trim().matches(Regex("(?i)^01[0-9A-F]{2}$"))
    }
}
