package com.obdiitools.obd

/**
 * Read-only decoder for documented Mazda CX-5 automatic-transmission data.
 *
 * Requests use UDS ReadDataByIdentifier (service 0x22). The 2016 CX-5
 * parameter catalogue identifies the transmission controller at 0x7E1
 * (response 0x7E9) for these identifiers.
 */
data class MazdaCx5TransmissionData(
    val gear: String?,
    val transmissionTemperatureC: Double?,
    val rawGearResponse: String = "",
    val rawTemperatureResponse: String = "",
)

object MazdaCx5Protocol {
    const val TCM_REQUEST_ID = "7E1"
    const val GEAR_DID = "1E12"
    const val TRANSMISSION_TEMPERATURE_DID = "1E1C"

    fun parseGear(response: UdsResponse): String? {
        if (!response.isSuccess || response.did.uppercase() != GEAR_DID) return null
        val raw = response.dataBytes.firstOrNull() ?: return null
        return when (raw) {
            in 1..6 -> raw.toString()
            0x50 -> "N"
            0x60 -> "R"
            0x70 -> "P"
            else -> null
        }
    }

    fun parseTransmissionTemperatureC(response: UdsResponse): Double? {
        if (!response.isSuccess ||
            response.did.uppercase() != TRANSMISSION_TEMPERATURE_DID) return null
        val bytes = response.dataBytes
        if (bytes.size < 2) return null
        val unsigned = (bytes[0] shl 8) or bytes[1]
        val signed = if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
        val temperature = signed / 16.0
        return temperature.takeIf { it in -500.0..500.0 }
    }

    fun parseTransmissionData(
        gearResponse: UdsResponse,
        temperatureResponse: UdsResponse,
        rawGearResponse: String = "",
        rawTemperatureResponse: String = "",
    ): MazdaCx5TransmissionData = MazdaCx5TransmissionData(
        gear = parseGear(gearResponse),
        transmissionTemperatureC = parseTransmissionTemperatureC(temperatureResponse),
        rawGearResponse = rawGearResponse,
        rawTemperatureResponse = rawTemperatureResponse,
    )
}
