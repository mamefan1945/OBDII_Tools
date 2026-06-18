package com.obdiitools.obd

enum class PidCategory(val label: String) {
    ENGINE("Engine"),
    SPEED_DISTANCE("Speed & Distance"),
    TEMPERATURE("Temperature"),
    FUEL("Fuel"),
    AIR_PRESSURE("Air & Pressure"),
    THROTTLE_LOAD("Throttle & Load"),
    OXYGEN("Oxygen Sensors"),
    ELECTRICAL("Electrical"),
    EMISSIONS("Emissions"),
    TIME_COUNTERS("Time & Counters"),
}

data class PidDefinition(
    val command: String,
    val name: String,
    val shortName: String,
    val category: PidCategory,
    val unit: String,
    val minValue: Float,
    val maxValue: Float,
    val parse: (String) -> Float?,
)

data class LivePidValue(
    val definition: PidDefinition,
    val value: Float? = null,
    val supported: Boolean? = null,
) {
    val displayValue: String
        get() = when {
            supported == false -> "N/A"
            value == null      -> "—"
            else -> when (definition.unit) {
                "RPM"  -> "${value.toInt()} RPM"
                "km/h" -> "${value.toInt()} km/h"
                "min"  -> "${value.toInt()} min"
                "s"    -> "${value.toInt()} s"
                "km"   -> "${value.toInt()} km"
                "V"    -> "${"%.2f".format(value)} V"
                "g/s"  -> "${"%.2f".format(value)} g/s"
                "L/h"  -> "${"%.1f".format(value)} L/h"
                "%"    -> "${"%.1f".format(value)} %"
                "kPa"  -> "${value.toInt()} kPa"
                "Pa"   -> "${value.toInt()} Pa"
                "λ"    -> "${"%.3f".format(value)} λ"
                else   -> "${"%.1f".format(value)} ${definition.unit}"
            }
        }
}

object AllPidDefinitions {

    private fun bytes(hex: String, count: Int): List<Int> {
        // Strip response prefix (mode byte 41 + PID byte = 4 chars), parse hex pairs
        val data = hex.replace(" ", "").replace("\r", "").replace("\n", "").trim()
        val stripped = if (data.length >= 4) data.substring(4) else data
        val result = mutableListOf<Int>()
        var i = 0
        while (i + 1 < stripped.length && result.size < count) {
            runCatching { result.add(stripped.substring(i, i + 2).toInt(16)) }
            i += 2
        }
        return result
    }

    private fun noData(hex: String) =
        hex.contains("NODATA", ignoreCase = true) ||
        hex.contains("NO DATA", ignoreCase = true) ||
        hex.contains("ERROR", ignoreCase = true) ||
        hex.contains("?") ||
        hex.isBlank()

    private fun pid(
        command: String,
        name: String,
        shortName: String,
        category: PidCategory,
        unit: String,
        min: Float,
        max: Float,
        parse: (String) -> Float?,
    ) = PidDefinition(command, name, shortName, category, unit, min, max) { raw ->
        if (noData(raw)) null else parse(raw)
    }

    val ALL: List<PidDefinition> = listOf(

        // ── Engine ────────────────────────────────────────────────────────
        pid("010C", "Engine RPM", "RPM", PidCategory.ENGINE, "RPM", 0f, 8000f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { ((it[0] * 256) + it[1]) / 4f }
        },
        pid("0104", "Calculated Engine Load", "Load", PidCategory.ENGINE, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("010E", "Ignition Timing Advance", "Timing", PidCategory.ENGINE, "°", -64f, 64f) { r ->
            bytes(r, 1).firstOrNull()?.let { it / 2f - 64f }
        },
        pid("011F", "Run Time Since Engine Start", "Run Time", PidCategory.ENGINE, "s", 0f, 65535f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]).toFloat() }
        },
        pid("015C", "Engine Oil Temperature", "Oil Temp", PidCategory.ENGINE, "°C", -40f, 210f) { r ->
            bytes(r, 1).firstOrNull()?.let { it - 40f }
        },
        pid("015E", "Engine Fuel Rate", "Fuel Rate", PidCategory.ENGINE, "L/h", 0f, 3276.75f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 20f }
        },

        // ── Speed & Distance ──────────────────────────────────────────────
        pid("010D", "Vehicle Speed", "Speed", PidCategory.SPEED_DISTANCE, "km/h", 0f, 255f) { r ->
            bytes(r, 1).firstOrNull()?.toFloat()
        },
        pid("0121", "Distance Traveled — MIL On", "MIL Dist", PidCategory.SPEED_DISTANCE, "km", 0f, 65535f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]).toFloat() }
        },
        pid("0131", "Distance Since Codes Cleared", "Dist Clear", PidCategory.SPEED_DISTANCE, "km", 0f, 65535f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]).toFloat() }
        },

        // ── Temperature ───────────────────────────────────────────────────
        pid("0105", "Engine Coolant Temperature", "Coolant", PidCategory.TEMPERATURE, "°C", -40f, 215f) { r ->
            bytes(r, 1).firstOrNull()?.let { it - 40f }
        },
        pid("010F", "Intake Air Temperature", "IAT", PidCategory.TEMPERATURE, "°C", -40f, 215f) { r ->
            bytes(r, 1).firstOrNull()?.let { it - 40f }
        },
        pid("0146", "Ambient Air Temperature", "Ambient", PidCategory.TEMPERATURE, "°C", -40f, 215f) { r ->
            bytes(r, 1).firstOrNull()?.let { it - 40f }
        },
        pid("013C", "Catalyst Temperature — B1S1", "Cat B1S1", PidCategory.TEMPERATURE, "°C", -40f, 6513.5f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 10f - 40f }
        },
        pid("013D", "Catalyst Temperature — B2S1", "Cat B2S1", PidCategory.TEMPERATURE, "°C", -40f, 6513.5f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 10f - 40f }
        },
        pid("013E", "Catalyst Temperature — B1S2", "Cat B1S2", PidCategory.TEMPERATURE, "°C", -40f, 6513.5f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 10f - 40f }
        },

        // ── Fuel ──────────────────────────────────────────────────────────
        pid("012F", "Fuel Tank Level", "Fuel Level", PidCategory.FUEL, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("010A", "Fuel Rail Pressure (gauge)", "Fuel Press", PidCategory.FUEL, "kPa", 0f, 765f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 3f }
        },
        pid("0123", "Fuel Rail Gauge Pressure", "Rail Press", PidCategory.FUEL, "kPa", 0f, 655350f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) * 10f }
        },
        pid("0159", "Fuel Rail Absolute Pressure", "Rail Abs", PidCategory.FUEL, "kPa", 0f, 655350f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) * 10f }
        },
        pid("0106", "Short Term Fuel Trim — Bank 1", "STFT B1", PidCategory.FUEL, "%", -100f, 99.2f) { r ->
            bytes(r, 1).firstOrNull()?.let { (it - 128) * 100f / 128f }
        },
        pid("0107", "Long Term Fuel Trim — Bank 1", "LTFT B1", PidCategory.FUEL, "%", -100f, 99.2f) { r ->
            bytes(r, 1).firstOrNull()?.let { (it - 128) * 100f / 128f }
        },
        pid("0108", "Short Term Fuel Trim — Bank 2", "STFT B2", PidCategory.FUEL, "%", -100f, 99.2f) { r ->
            bytes(r, 1).firstOrNull()?.let { (it - 128) * 100f / 128f }
        },
        pid("0109", "Long Term Fuel Trim — Bank 2", "LTFT B2", PidCategory.FUEL, "%", -100f, 99.2f) { r ->
            bytes(r, 1).firstOrNull()?.let { (it - 128) * 100f / 128f }
        },
        pid("0152", "Ethanol Fuel Percentage", "Ethanol", PidCategory.FUEL, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },

        // ── Air & Pressure ────────────────────────────────────────────────
        pid("0110", "MAF Air Flow Rate", "MAF", PidCategory.AIR_PRESSURE, "g/s", 0f, 655.35f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 100f }
        },
        pid("010B", "Intake Manifold Pressure", "MAP", PidCategory.AIR_PRESSURE, "kPa", 0f, 255f) { r ->
            bytes(r, 1).firstOrNull()?.toFloat()
        },
        pid("0133", "Barometric Pressure", "Baro", PidCategory.AIR_PRESSURE, "kPa", 0f, 255f) { r ->
            bytes(r, 1).firstOrNull()?.toFloat()
        },
        pid("0122", "Fuel Rail Pressure (vacuum ref)", "Rail Vac", PidCategory.AIR_PRESSURE, "kPa", 0f, 5177.265f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) * 0.079f }
        },

        // ── Throttle & Load ───────────────────────────────────────────────
        pid("0111", "Throttle Position", "Throttle", PidCategory.THROTTLE_LOAD, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("0145", "Relative Throttle Position", "Rel Throttle", PidCategory.THROTTLE_LOAD, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("0147", "Absolute Throttle Position B", "Throttle B", PidCategory.THROTTLE_LOAD, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("014C", "Commanded Throttle Actuator", "Cmd Throttle", PidCategory.THROTTLE_LOAD, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("0149", "Accelerator Pedal Position D", "Pedal D", PidCategory.THROTTLE_LOAD, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("014A", "Accelerator Pedal Position E", "Pedal E", PidCategory.THROTTLE_LOAD, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("015A", "Relative Accelerator Pedal Position", "Rel Pedal", PidCategory.THROTTLE_LOAD, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("0143", "Absolute Load Value", "Abs Load", PidCategory.THROTTLE_LOAD, "%", 0f, 25700f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) * 100f / 255f }
        },
        pid("0144", "Commanded Air-Fuel Equivalence Ratio", "Lambda", PidCategory.THROTTLE_LOAD, "λ", 0f, 2f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 32768f }
        },

        // ── Oxygen Sensors ────────────────────────────────────────────────
        pid("0114", "O2 Sensor 1 — Voltage", "O2 S1 V", PidCategory.OXYGEN, "V", 0f, 1.275f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 0.005f }
        },
        pid("0115", "O2 Sensor 2 — Voltage", "O2 S2 V", PidCategory.OXYGEN, "V", 0f, 1.275f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 0.005f }
        },
        pid("0116", "O2 Sensor 3 — Voltage", "O2 S3 V", PidCategory.OXYGEN, "V", 0f, 1.275f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 0.005f }
        },
        pid("0117", "O2 Sensor 4 — Voltage", "O2 S4 V", PidCategory.OXYGEN, "V", 0f, 1.275f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 0.005f }
        },

        // ── Electrical ────────────────────────────────────────────────────
        pid("0142", "Control Module Voltage", "Battery", PidCategory.ELECTRICAL, "V", 0f, 65.535f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 1000f }
        },
        pid("015B", "Hybrid Battery Pack Life", "HV Battery", PidCategory.ELECTRICAL, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },

        // ── Emissions ─────────────────────────────────────────────────────
        pid("012C", "Commanded EGR", "EGR Cmd", PidCategory.EMISSIONS, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("012D", "EGR Error", "EGR Err", PidCategory.EMISSIONS, "%", -100f, 99.2f) { r ->
            bytes(r, 1).firstOrNull()?.let { (it - 128) * 100f / 128f }
        },
        pid("012E", "Commanded Evaporative Purge", "Evap Purge", PidCategory.EMISSIONS, "%", 0f, 100f) { r ->
            bytes(r, 1).firstOrNull()?.let { it * 100f / 255f }
        },
        pid("015D", "Fuel Injection Timing", "Inj Timing", PidCategory.EMISSIONS, "°", -210f, 302f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 128f - 210f }
        },

        // ── Time & Counters ───────────────────────────────────────────────
        pid("0130", "Warm-ups Since Codes Cleared", "Warm-ups", PidCategory.TIME_COUNTERS, "count", 0f, 255f) { r ->
            bytes(r, 1).firstOrNull()?.toFloat()
        },
        pid("014D", "Time Run with MIL On", "MIL Time", PidCategory.TIME_COUNTERS, "min", 0f, 65535f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]).toFloat() }
        },
        pid("014E", "Time Since Codes Cleared", "Clear Time", PidCategory.TIME_COUNTERS, "min", 0f, 65535f) { r ->
            bytes(r, 2).takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]).toFloat() }
        },
    )

    val BY_CATEGORY: Map<PidCategory, List<PidDefinition>> =
        PidCategory.entries.associateWith { cat -> ALL.filter { it.category == cat } }
            .filterValues { it.isNotEmpty() }
}
