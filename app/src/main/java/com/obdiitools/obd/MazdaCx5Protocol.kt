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
    val frontLeftWheelSpeedKph: Int? = null,
    val frontRightWheelSpeedKph: Int? = null,
    val rearLeftWheelSpeedKph: Int? = null,
    val rearRightWheelSpeedKph: Int? = null,
)

data class MazdaDidCandidate(val did: String, val description: String, val vehicleVerified: Boolean = false)

data class MazdaCx5DashboardData(
    val transmission: MazdaCx5TransmissionData,
    val capturedAtMs: Long,
    val gearCapturedAtMs: Long? = null,
    val temperatureCapturedAtMs: Long? = null,
    val frontLeftWheelSpeedCapturedAtMs: Long? = null,
    val frontRightWheelSpeedCapturedAtMs: Long? = null,
    val rearLeftWheelSpeedCapturedAtMs: Long? = null,
    val rearRightWheelSpeedCapturedAtMs: Long? = null,
)

/**
 * Bounded read-only TCM probe set. The first two identifiers are already
 * validated on the user's 2016 CX-5. Remaining entries are catalogue leads
 * only; their presence here does not imply 2016 support or a known decoder.
 */
object MazdaCx5Protocol {
    val TCM_DISCOVERY_CANDIDATES = listOf(
        MazdaDidCandidate("1E12", "Selected transmission gear", vehicleVerified = true),
        MazdaDidCandidate("1E1C", "Transmission fluid temperature", vehicleVerified = true),
        MazdaDidCandidate("1674", "Transmission temperature alternate (catalogue lead)"),
        MazdaDidCandidate("1E3F", "Transmission temperature alternate (catalogue lead)"),
        MazdaDidCandidate("11B3", "Transmission gear alternate (catalogue lead)"),
        MazdaDidCandidate("11BD", "Transmission temperature alternate (catalogue lead)"),
        MazdaDidCandidate("163A", "Torque converter input torque (catalogue lead)"),
        MazdaDidCandidate("DD01", "Transmission odometer (catalogue lead)"),
    )
    const val TCM_REQUEST_ID = "7E1"
    const val GEAR_DID = "1E12"
    const val TRANSMISSION_TEMPERATURE_DID = "1E1C"
    const val DSC_REQUEST_ID = "760"
    const val FRONT_RIGHT_WHEEL_SPEED_DID = "2B06"
    const val FRONT_LEFT_WHEEL_SPEED_DID = "2B07"
    const val REAR_RIGHT_WHEEL_SPEED_DID = "2B08"
    const val REAR_LEFT_WHEEL_SPEED_DID = "2B09"
    val WHEEL_SPEED_DIDS = listOf(
        FRONT_LEFT_WHEEL_SPEED_DID,
        FRONT_RIGHT_WHEEL_SPEED_DID,
        REAR_LEFT_WHEEL_SPEED_DID,
        REAR_RIGHT_WHEEL_SPEED_DID,
    )

    /** The DSC DIDs return one unsigned byte directly in km/h (0..200). */
    fun parseWheelSpeedKph(response: UdsResponse, did: String): Int? {
        if (!response.isSuccess || response.did.uppercase() != did.uppercase() ||
            did.uppercase() !in WHEEL_SPEED_DIDS) return null
        val value = response.dataBytes.firstOrNull() ?: return null
        return value.takeIf { it in 0..200 }
    }

    fun isFresh(capturedAtMs: Long?, nowMs: Long, maxAgeMs: Long): Boolean =
        capturedAtMs != null && nowMs >= capturedAtMs && nowMs - capturedAtMs <= maxAgeMs

    fun parseGear(response: UdsResponse): String? {
        if (!response.isSuccess || response.did.uppercase() != GEAR_DID) return null
        val raw = response.dataBytes.firstOrNull() ?: return null
        // Forward gear values 1 through 6 have been observed on this vehicle.
        // Other encodings remain unknown; do not infer P/R/N from unverified bytes.
        return raw.takeIf { it in 1..6 }?.toString()
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
