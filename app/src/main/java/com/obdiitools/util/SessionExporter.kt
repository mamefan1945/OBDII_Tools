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
        sb.appendLine("# Device,${session.deviceName}")
        sb.appendLine("# Start,${timestampFmt.format(Date(session.startTimeMs))}")
        session.endTimeMs?.let {
            sb.appendLine("# End,${timestampFmt.format(Date(it))}")
            val durMin = (it - session.startTimeMs) / 60_000
            sb.appendLine("# Duration,${durMin} min")
        }
        session.distanceKm?.let { sb.appendLine("# Distance,${"%.2f".format(it)} km") }
        session.maxRpm?.let { sb.appendLine("# Max RPM,$it") }
        session.maxSpeedKph?.let { sb.appendLine("# Max Speed,$it kph") }
        session.maxCoolantTempC?.let { sb.appendLine("# Max Coolant,$it C") }
        sb.appendLine("#")
        sb.appendLine("timestamp_ms,elapsed_sec,rpm,speed_kph,coolant_c,throttle_pct,engine_load_pct,maf_g_s,battery_v")
        for (pt in points) {
            val elapsed = (pt.timestampMs - session.startTimeMs) / 1000f
            sb.append(pt.timestampMs).append(',')
            sb.append("%.1f".format(elapsed)).append(',')
            sb.append(pt.rpm ?: "").append(',')
            sb.append(pt.speedKph ?: "").append(',')
            sb.append(pt.coolantTempC ?: "").append(',')
            sb.append(pt.throttlePercent?.let { "%.1f".format(it) } ?: "").append(',')
            sb.append(pt.engineLoadPercent?.let { "%.1f".format(it) } ?: "").append(',')
            sb.append(pt.mafGramsPerSec?.let { "%.2f".format(it) } ?: "").append(',')
            sb.appendLine(pt.batteryVoltage?.let { "%.2f".format(it) } ?: "")
        }
        return sb.toString()
    }
}
