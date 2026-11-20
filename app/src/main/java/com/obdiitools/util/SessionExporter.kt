package com.obdiitools.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.obdiitools.data.SessionDataPoint
import com.obdiitools.data.SessionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionShareOptions(
    val includeVehicleDetails: Boolean = true,
    val includeTripStatistics: Boolean = true,
    val includeObdTelemetry: Boolean = true,
    val includeCvtTelemetry: Boolean = true,
    val includeGps: Boolean = true,
    val includeMazdaTcmTelemetry: Boolean = true,
)

object SessionExporter {

    private val filenameFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val timestampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun buildShareIntent(
        context: Context,
        session: SessionEntity,
        points: List<SessionDataPoint>,
        options: SessionShareOptions = SessionShareOptions(),
    ): Intent {
        val csv = buildCsv(session, points, options)
        val filename = "session_${filenameFmt.format(Date(session.startTimeMs))}.csv"
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(dir, filename).also { it.writeText(csv) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "OBD Session – ${timestampFmt.format(Date(session.startTimeMs))}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildCsv(
        session: SessionEntity,
        points: List<SessionDataPoint>,
        options: SessionShareOptions,
    ): String {
        val slips = if (options.includeCvtTelemetry) {
            points.mapNotNull { point -> point.cvtSlipRpm?.let { point.timestampMs to it } }.sortedBy { it.first }
        } else emptyList()
        val endpointSamples = slips.filter { isSlipEndpoint(it.second) }
        val endpointPercent = if (slips.isEmpty()) 0.0 else endpointSamples.size * 100.0 / slips.size
        val longestEndpointRunMs = longestEndpointRunMs(slips)

        val sb = StringBuilder()
        sb.appendLine("# OBDII Tools Session Export")
        if (options.includeVehicleDetails) {
            sb.appendLine("# Device,${csvSafeCell(session.deviceName)}")
            session.vin?.takeIf { it.isNotBlank() }?.let { sb.appendLine("# VIN,${csvSafeCell(it)}") }
            if (session.make.isNotBlank() || session.model.isNotBlank()) {
                val vehicle = listOf(session.make, session.model).filter { it.isNotBlank() }.joinToString(" ")
                sb.appendLine("# Vehicle,${csvSafeCell(vehicle)}")
            }
        }
        sb.appendLine("# Start,${timestampFmt.format(Date(session.startTimeMs))}")
        session.endTimeMs?.let { sb.appendLine("# End,${timestampFmt.format(Date(it))}") }
        if (options.includeTripStatistics) {
            session.endTimeMs?.let {
                val durMin = (it - session.startTimeMs) / 60_000
                sb.appendLine("# Duration,${durMin} min")
            }
            session.distanceKm?.let { sb.appendLine("# Distance,${"%.2f".format(Locale.US, it)} km") }
            session.maxRpm?.let { sb.appendLine("# Max RPM,$it") }
            session.maxSpeedKph?.let { sb.appendLine("# Max Speed,$it kph") }
            session.maxCoolantTempC?.let { sb.appendLine("# Max Coolant,$it C") }
        }
        if (options.includeCvtTelemetry) {
            sb.appendLine("# CVT slip endpoint candidates,${endpointSamples.size} of ${slips.size} samples (${"%.1f".format(Locale.US, endpointPercent)}%)")
            sb.appendLine("# Longest endpoint run,${"%.1f".format(Locale.US, longestEndpointRunMs / 1000.0)} sec")
            sb.appendLine("# Endpoint means signed-byte limit (+127 or -128); it is not proof of physical sensor saturation.")
            sb.appendLine("# Raw slip byte is reconstructed losslessly from the signed decoded byte.")
        }

        val columns = mutableListOf("timestamp_ms", "elapsed_sec")
        if (options.includeObdTelemetry) columns += listOf(
            "rpm", "speed_kph", "coolant_c", "throttle_pct", "engine_load_pct",
            "maf_g_s", "battery_v",
        )
        if (options.includeCvtTelemetry) columns += listOf(
            "cvt_speed_kph", "cvt_engine_rpm", "cvt_slip_rpm", "cvt_slip_raw_hex",
            "cvt_slip_endpoint_candidate", "cvt_temp_c", "cvt_ratio",
            "cvt_primary_pressure_mpa", "cvt_secondary_pressure_mpa",
            "cvt_target_secondary_pressure_mpa",
        )
        if (options.includeMazdaTcmTelemetry) columns += listOf("mazda_selected_gear", "mazda_transmission_temp_c")
        if (options.includeGps) columns += listOf("latitude", "longitude", "gps_accuracy_m")
        sb.appendLine(columns.joinToString(","))

        for (pt in points.sortedBy { it.timestampMs }) {
            val row = mutableListOf<String>()
            row += pt.timestampMs.toString()
            row += "%.1f".format(Locale.US, (pt.timestampMs - session.startTimeMs) / 1000f)
            if (options.includeObdTelemetry) row += listOf(
                pt.rpm?.toString().orEmpty(),
                pt.speedKph?.toString().orEmpty(),
                pt.coolantTempC?.toString().orEmpty(),
                pt.throttlePercent?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                pt.engineLoadPercent?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                pt.mafGramsPerSec?.let { "%.2f".format(Locale.US, it) }.orEmpty(),
                pt.batteryVoltage?.let { "%.2f".format(Locale.US, it) }.orEmpty(),
            )
            if (options.includeCvtTelemetry) row += listOf(
                pt.cvtSpeedKph?.toString().orEmpty(),
                pt.cvtEngineRpm?.toString().orEmpty(),
                pt.cvtSlipRpm?.toString().orEmpty(),
                pt.cvtSlipRpm?.let(::rawSlipHex).orEmpty(),
                pt.cvtSlipRpm?.let { isSlipEndpoint(it).toString() }.orEmpty(),
                pt.cvtTempC?.toString().orEmpty(),
                pt.cvtRatio?.let { "%.3f".format(Locale.US, it) }.orEmpty(),
                pt.cvtPrimaryPressureMpa?.let { "%.3f".format(Locale.US, it) }.orEmpty(),
                pt.cvtSecondaryPressureMpa?.let { "%.3f".format(Locale.US, it) }.orEmpty(),
                pt.cvtTargetSecondaryPressureMpa?.let { "%.3f".format(Locale.US, it) }.orEmpty(),
            )
            if (options.includeMazdaTcmTelemetry) row += listOf(
                pt.mazdaSelectedGear.orEmpty(),
                pt.mazdaTransmissionTempC?.let { "%.2f".format(Locale.US, it) }.orEmpty(),
            )
            if (options.includeGps) row += listOf(
                pt.latitude?.let { "%.7f".format(Locale.US, it) }.orEmpty(),
                pt.longitude?.let { "%.7f".format(Locale.US, it) }.orEmpty(),
                pt.accuracy?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
            )
            sb.appendLine(row.joinToString(","))
        }
        return sb.toString()
    }

    private fun isSlipEndpoint(slipRpm: Int): Boolean = slipRpm == 127 || slipRpm == -128

    private fun rawSlipHex(slipRpm: Int): String =
        (slipRpm and 0xFF).toString(16).uppercase(Locale.US).padStart(2, '0')

    private fun longestEndpointRunMs(samples: List<Pair<Long, Int>>): Long {
        var runStart: Long? = null
        var previousTime: Long? = null
        var longest = 0L
        for ((time, slip) in samples) {
            if (!isSlipEndpoint(slip)) {
                runStart = null
                previousTime = null
                continue
            }
            if (previousTime == null || time - previousTime > 2500L) runStart = time
            previousTime = time
            val duration = time - (runStart ?: time)
            if (duration > longest) longest = duration
        }
        return longest
    }
}

/** Quote CSV text and neutralize spreadsheet formula prefixes in untrusted fields. */
internal fun csvSafeCell(value: String): String {
    val firstMeaningful = value.dropWhile { it.isWhitespace() }.firstOrNull()
    val safeValue = if (firstMeaningful in listOf('=', '+', '-', '@')) "'$value" else value
    return "\"" + safeValue.replace("\"", "\"\"") + "\""
}
