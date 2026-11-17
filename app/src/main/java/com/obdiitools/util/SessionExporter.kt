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

object SessionExporter {

    private val filenameFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val timestampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun buildShareIntent(
        context: Context,
        session: SessionEntity,
        points: List<SessionDataPoint>,
    ): Intent {
        val csv = buildCsv(session, points)
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

    private fun buildCsv(session: SessionEntity, points: List<SessionDataPoint>): String {
        val sb = StringBuilder()
        sb.appendLine("# OBDII Tools Session Export")
        sb.appendLine("# Device,${csvSafeCell(session.deviceName)}")
        session.vin?.takeIf { it.isNotBlank() }?.let { sb.appendLine("# VIN,${csvSafeCell(it)}") }
        if (session.make.isNotBlank() || session.model.isNotBlank()) {
            val vehicle = listOf(session.make, session.model).filter { it.isNotBlank() }.joinToString(" ")
            sb.appendLine("# Vehicle,${csvSafeCell(vehicle)}")
        }
        sb.appendLine("# Start,${timestampFmt.format(Date(session.startTimeMs))}")
        session.endTimeMs?.let {
            sb.appendLine("# End,${timestampFmt.format(Date(it))}")
            val durMin = (it - session.startTimeMs) / 60_000
            sb.appendLine("# Duration,${durMin} min")
        }
        session.distanceKm?.let { sb.appendLine("# Distance,${"%.2f".format(Locale.US, it)} km") }
        session.maxRpm?.let { sb.appendLine("# Max RPM,$it") }
        session.maxSpeedKph?.let { sb.appendLine("# Max Speed,$it kph") }
        session.maxCoolantTempC?.let { sb.appendLine("# Max Coolant,$it C") }
        sb.appendLine("#")
        sb.appendLine("timestamp_ms,elapsed_sec,rpm,speed_kph,coolant_c,throttle_pct,engine_load_pct,maf_g_s,battery_v,cvt_speed_kph,cvt_engine_rpm,cvt_slip_rpm,cvt_temp_c,cvt_ratio,cvt_primary_pressure_mpa,cvt_secondary_pressure_mpa,cvt_target_secondary_pressure_mpa")
        for (pt in points) {
            val elapsed = (pt.timestampMs - session.startTimeMs) / 1000f
            sb.append(pt.timestampMs).append(',')
            sb.append("%.1f".format(Locale.US, elapsed)).append(',')
            sb.append(pt.rpm ?: "").append(',')
            sb.append(pt.speedKph ?: "").append(',')
            sb.append(pt.coolantTempC ?: "").append(',')
            sb.append(pt.throttlePercent?.let { "%.1f".format(Locale.US, it) } ?: "").append(',')
            sb.append(pt.engineLoadPercent?.let { "%.1f".format(Locale.US, it) } ?: "").append(',')
            sb.append(pt.mafGramsPerSec?.let { "%.2f".format(Locale.US, it) } ?: "").append(',')
            sb.append(pt.batteryVoltage?.let { "%.2f".format(Locale.US, it) } ?: "").append(',')
            sb.append(pt.cvtSpeedKph ?: "").append(',')
            sb.append(pt.cvtEngineRpm ?: "").append(',')
            sb.append(pt.cvtSlipRpm ?: "").append(',')
            sb.append(pt.cvtTempC ?: "").append(',')
            sb.append(pt.cvtRatio?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
            sb.append(pt.cvtPrimaryPressureMpa?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
            sb.append(pt.cvtSecondaryPressureMpa?.let { "%.3f".format(Locale.US, it) } ?: "").append(',')
            sb.appendLine(pt.cvtTargetSecondaryPressureMpa?.let { "%.3f".format(Locale.US, it) } ?: "")
        }
        return sb.toString()
    }
}

/** Quote CSV text and neutralize spreadsheet formula prefixes in untrusted fields. */
internal fun csvSafeCell(value: String): String {
    val firstMeaningful = value.dropWhile { it.isWhitespace() }.firstOrNull()
    val safeValue = if (firstMeaningful in listOf('=', '+', '-', '@')) "'$value" else value
    return "\"" + safeValue.replace("\"", "\"\"") + "\""
}
