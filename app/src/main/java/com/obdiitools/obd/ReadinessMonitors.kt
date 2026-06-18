package com.obdiitools.obd

data class ReadinessMonitor(
    val name: String,
    val isSupported: Boolean,
    val isComplete: Boolean,
)

data class ReadinessStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    val monitors: List<ReadinessMonitor>,
) {
    val allComplete: Boolean get() = monitors.filter { it.isSupported }.all { it.isComplete }
}

fun parseReadinessStatus(response: String): ReadinessStatus? {
    val cleaned = response.replace(" ", "").uppercase().trim()
    // Response to PID 0101: "4101AABBCCDD" where AA=MIL+count, BB-DD=monitor bits
    if (cleaned.length < 12 || !cleaned.startsWith("41")) return null
    val data = cleaned.substring(4, 12) // 4 data bytes
    val bytes = (0 until 4).map { i -> data.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null }

    val a = bytes[0]; val b = bytes[1]; val c = bytes[2]; val d = bytes[3]
    val milOn = (a and 0x80) != 0
    val dtcCount = a and 0x7F

    // Per SAE J1979: byte C = support bits, byte D = readiness bits (0 = complete)
    fun monitor(name: String, bit: Int): ReadinessMonitor {
        val supported = (bytes[2] and (1 shl bit)) != 0
        val complete  = (bytes[3] and (1 shl bit)) == 0
        return ReadinessMonitor(name, supported, complete)
    }

    // Byte B bits 0-2: continuous monitors (always supported, complete = bit not set)
    val monitors = mutableListOf(
        ReadinessMonitor("Misfire",      true, (b and 0x01) == 0),
        ReadinessMonitor("Fuel System",  true, (b and 0x02) == 0),
        ReadinessMonitor("Components",   true, (b and 0x04) == 0),
        // Non-continuous: support in byte C (index 2), readiness in byte D (index 3)
        monitor("Catalyst",          0),
        monitor("Heated Catalyst",   1),
        monitor("Evap System",       2),
        monitor("Secondary Air",     3),
        monitor("A/C Refrigerant",   4),
        monitor("O2 Sensor",         5),
        monitor("O2 Sensor Heater",  6),
        monitor("EGR / VVT",         7),
    )

    return ReadinessStatus(milOn, dtcCount, monitors)
}
