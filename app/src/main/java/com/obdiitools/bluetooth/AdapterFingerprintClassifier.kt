package com.obdiitools.bluetooth

/**
 * Conservative, local adapter fingerprint matcher.
 *
 * Advertised names and ELM responses are self-reported and can be spoofed.
 * "confirmed" is reserved for an explicit product identifier in AT@1;
 * an advertised-name match is only "probable".
 */
data class AdapterIdentification(
    val manufacturer: String = "",
    val model: String = "",
    val confidence: String = "unknown",
    val evidence: String = "",
)

object AdapterFingerprintClassifier {
    private data class Rule(
        val manufacturer: String,
        val model: String,
        val aliases: List<String>,
    )

    private val rules = listOf(
        Rule("VEEPEAK", "OBDCheck BLE+", listOf("OBDCHECK BLE+", "OBDCHECK BLE PLUS")),
        Rule("VEEPEAK", "OBDCheck BLE", listOf("OBDCHECK BLE")),
        Rule("OBDLink", "MX+", listOf("OBDLINK MX+", "OBDLINK MX PLUS")),
        Rule("OBDLink", "CX", listOf("OBDLINK CX")),
        Rule("OBDLink", "LX", listOf("OBDLINK LX")),
        Rule("OBDLink", "EX", listOf("OBDLINK EX")),
        Rule("Vgate", "iCar Pro BLE 4.0", listOf("VGATE ICAR PRO", "ICAR PRO BLE")),
        Rule("Vgate", "iCar Pro Wi-Fi", listOf("VGATE ICAR PRO WIFI", "ICAR PRO WI-FI")),
        Rule("vLinker", "MC+", listOf("VLINKER MC+", "VLINKER MC PLUS")),
        Rule("vLinker", "FD+", listOf("VLINKER FD+", "VLINKER FD PLUS")),
        Rule("vLinker", "BM+", listOf("VLINKER BM+", "VLINKER BM PLUS")),
        Rule("vLinker", "FS", listOf("VLINKER FS")),
        Rule("Carista", "EVO", listOf("CARISTA EVO")),
        Rule("BlueDriver", "BlueDriver", listOf("BLUEDRIVER")),
        Rule("BAFX Products", "Bluetooth OBDII", listOf("BAFX")),
    )

    fun identify(
        advertisedName: String,
        ati: String,
        description: String,
        manufacturerDataHex: String = "",
    ): AdapterIdentification {
        val name = advertisedName.trim()
        val atiText = ati.trim()
        val descriptionText = description.trim()
        val sources = listOf(
            "advertised name" to name,
            "ATI" to atiText,
            "AT@1" to descriptionText,
        )
        val normalized = sources.map { (source, value) ->
            source to value.uppercase().replace(Regex("[^A-Z0-9+]+"), " ").trim()
        }
        for (rule in rules) {
            val alias = rule.aliases.firstOrNull { candidate ->
                val token = candidate.uppercase().replace(Regex("[^A-Z0-9+]+"), " ").trim()
                normalized.any { (_, value) -> value.contains(token) }
            } ?: continue
            val explicitDescription = descriptionText.uppercase()
                .contains(alias.uppercase())
            val evidence = normalized.filter { (_, value) ->
                value.contains(alias.uppercase().replace(Regex("[^A-Z0-9+]+"), " ").trim())
            }.map { it.first }.distinct().joinToString(", ")
            return AdapterIdentification(
                manufacturer = rule.manufacturer,
                model = rule.model,
                confidence = if (explicitDescription) "confirmed" else "probable",
                evidence = evidence.ifBlank { "known product signature" },
            )
        }

        val vendor = when {
            normalized.any { it.second.contains("VEEPEAK") } -> "VEEPEAK"
            normalized.any { it.second.contains("OBDLINK") } -> "OBDLink"
            normalized.any { it.second.contains("VGATE") } -> "Vgate"
            normalized.any { it.second.contains("VLINKER") } -> "vLinker"
            normalized.any { it.second.contains("CARISTA") } -> "Carista"
            normalized.any { it.second.contains("BLUEDRIVER") } -> "BlueDriver"
            normalized.any { it.second.contains("BAFX") } -> "BAFX Products"
            else -> ""
        }
        val evidence = buildList {
            if (vendor.isNotEmpty()) add("brand string in adapter response/name")
            if (manufacturerDataHex.isNotBlank()) add("manufacturer data captured (not yet mapped)")
        }.joinToString(", ")
        return AdapterIdentification(
            manufacturer = vendor,
            model = "",
            confidence = "unknown",
            evidence = evidence,
        )
    }
}
