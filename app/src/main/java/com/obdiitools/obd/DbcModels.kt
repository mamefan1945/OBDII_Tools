package com.obdiitools.obd

data class DbcMessage(
    val id: Long,
    val name: String,
    val lengthBytes: Int,
)

data class DbcSignal(
    val messageId: Long,
    val messageName: String,
    val name: String,
    val startBit: Int,
    val lengthBits: Int,
    val isBigEndian: Boolean,
    val isSigned: Boolean,
    val scale: Double,
    val offset: Double,
    val unit: String,
)

data class DecodedSignal(
    val signalName: String,
    val messageName: String,
    val messageId: Long,
    val value: Double,
    val unit: String,
    val scale: Double,
) {
    val displayValue: String get() {
        val decimals = when {
            scale >= 0.5 -> 0
            scale >= 0.1 -> 1
            else         -> 2
        }
        val num = "%.${decimals}f".format(value)
        return if (unit.isNotEmpty()) "$num $unit" else num
    }

    val isUnknown: Boolean get() = signalName.startsWith("NEW_SIGNAL")
}
