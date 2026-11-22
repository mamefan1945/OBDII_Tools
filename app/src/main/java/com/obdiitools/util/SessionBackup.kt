package com.obdiitools.util

import android.content.Context
import android.net.Uri
import com.obdiitools.data.SessionDataPoint
import com.obdiitools.data.SessionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SessionBackupOptions(
    val includeVehicleDetails: Boolean = true,
    val includeTripStatistics: Boolean = true,
    val includeTelemetry: Boolean = true,
    val includeGps: Boolean = true,
)

data class ImportedSessionBackup(
    val sessions: List<SessionEntity>,
    val pointsByOldId: Map<Long, List<SessionDataPoint>>,
)

object SessionBackup {
    fun writeBackup(
        context: Context,
        destination: Uri,
        sessions: List<SessionEntity>,
        pointsBySession: Map<Long, List<SessionDataPoint>>,
        options: SessionBackupOptions,
    ) {
        val json = JSONObject().put("format", "obdiitools-session-backup")
            .put("version", 1)
            .put("options", JSONObject()
                .put("vehicleDetails", options.includeVehicleDetails)
                .put("tripStatistics", options.includeTripStatistics)
                .put("telemetry", options.includeTelemetry)
                .put("gps", options.includeGps))
        val sessionArray = JSONArray()
        val pointArray = JSONArray()
        sessions.forEach { s ->
            val item = JSONObject()
                .put("id", s.id)
                .put("startTimeMs", s.startTimeMs)
                .put("endTimeMs", s.endTimeMs ?: JSONObject.NULL)
            if (options.includeTripStatistics) {
                item.put("maxRpm", s.maxRpm ?: JSONObject.NULL)
                    .put("maxSpeedKph", s.maxSpeedKph ?: JSONObject.NULL)
                    .put("maxCoolantTempC", s.maxCoolantTempC ?: JSONObject.NULL)
                    .put("distanceKm", s.distanceKm ?: JSONObject.NULL)
                    .put("totalFuelLitres", s.totalFuelLitres ?: JSONObject.NULL)
            }
            if (options.includeVehicleDetails) {
                item.put("deviceName", s.deviceName).put("make", s.make)
                    .put("model", s.model).put("vin", s.vin ?: JSONObject.NULL)
            }
            sessionArray.put(item)
            pointsBySession[s.id].orEmpty().forEach { p ->
                val point = JSONObject().put("sessionId", s.id).put("timestampMs", p.timestampMs)
                if (options.includeTelemetry) {
                    point.put("rpm", p.rpm ?: JSONObject.NULL)
                        .put("speedKph", p.speedKph ?: JSONObject.NULL)
                        .put("coolantTempC", p.coolantTempC ?: JSONObject.NULL)
                        .put("throttlePercent", p.throttlePercent ?: JSONObject.NULL)
                        .put("engineLoadPercent", p.engineLoadPercent ?: JSONObject.NULL)
                        .put("mafGramsPerSec", p.mafGramsPerSec ?: JSONObject.NULL)
                        .put("batteryVoltage", p.batteryVoltage ?: JSONObject.NULL)
                        .put("cvtSpeedKph", p.cvtSpeedKph ?: JSONObject.NULL)
                        .put("cvtEngineRpm", p.cvtEngineRpm ?: JSONObject.NULL)
                        .put("cvtSlipRpm", p.cvtSlipRpm ?: JSONObject.NULL)
                        .put("cvtTempC", p.cvtTempC ?: JSONObject.NULL)
                        .put("cvtRatio", p.cvtRatio ?: JSONObject.NULL)
                        .put("cvtPrimaryPressureMpa", p.cvtPrimaryPressureMpa ?: JSONObject.NULL)
                        .put("cvtSecondaryPressureMpa", p.cvtSecondaryPressureMpa ?: JSONObject.NULL)
                        .put("cvtTargetSecondaryPressureMpa", p.cvtTargetSecondaryPressureMpa ?: JSONObject.NULL)
                        .put("mazdaSelectedGear", p.mazdaSelectedGear ?: JSONObject.NULL)
                        .put("mazdaTransmissionTempC", p.mazdaTransmissionTempC ?: JSONObject.NULL)
                        .put("mazdaFrontLeftWheelSpeedKph", p.mazdaFrontLeftWheelSpeedKph ?: JSONObject.NULL)
                        .put("mazdaFrontRightWheelSpeedKph", p.mazdaFrontRightWheelSpeedKph ?: JSONObject.NULL)
                        .put("mazdaRearLeftWheelSpeedKph", p.mazdaRearLeftWheelSpeedKph ?: JSONObject.NULL)
                        .put("mazdaRearRightWheelSpeedKph", p.mazdaRearRightWheelSpeedKph ?: JSONObject.NULL)
                }
                if (options.includeGps) {
                    point.put("latitude", p.latitude ?: JSONObject.NULL)
                        .put("longitude", p.longitude ?: JSONObject.NULL)
                        .put("accuracy", p.accuracy ?: JSONObject.NULL)
                }
                if (options.includeTelemetry || options.includeGps) pointArray.put(point)
            }
        }
        json.put("sessions", sessionArray).put("dataPoints", pointArray)
        val output = context.contentResolver.openOutputStream(destination, "w")
            ?: error("Unable to create the selected backup file")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("OBDII Tools session backup. Restore it from Drive History > Restore Sessions.\\n".toByteArray())
            zip.closeEntry()
        }
    }

    fun readImport(context: Context, uri: Uri): ImportedSessionBackup {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to open the selected file")
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            var backupJson: String? = null
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "backup.json") backupJson = zip.readBytes().toString(Charsets.UTF_8)
                    zip.closeEntry()
                }
            }
            return parseBackup(backupJson ?: error("This ZIP does not contain an OBDII Tools backup"))
        }
        return parseLegacyCsv(bytes.toString(Charsets.UTF_8))
    }

    private fun parseBackup(raw: String): ImportedSessionBackup {
        val root = JSONObject(raw)
        require(root.optString("format") == "obdiitools-session-backup") { "Unrecognized backup format" }
        require(root.optInt("version") == 1) { "Unsupported backup version" }
        val sessions = mutableListOf<SessionEntity>()
        val points = mutableMapOf<Long, MutableList<SessionDataPoint>>()
        val array = root.getJSONArray("sessions")
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            sessions += SessionEntity(
                id = o.getLong("id"), startTimeMs = o.getLong("startTimeMs"),
                endTimeMs = o.nullableLong("endTimeMs"),
                maxRpm = o.nullableInt("maxRpm"), maxSpeedKph = o.nullableInt("maxSpeedKph"),
                maxCoolantTempC = o.nullableInt("maxCoolantTempC"),
                distanceKm = o.nullableFloat("distanceKm"),
                deviceName = o.optString("deviceName"), make = o.optString("make"),
                model = o.optString("model"), totalFuelLitres = o.nullableFloat("totalFuelLitres"),
                vin = o.nullableString("vin"),
            )
        }
        val pointArray = root.optJSONArray("dataPoints") ?: JSONArray()
        for (i in 0 until pointArray.length()) {
            val o = pointArray.getJSONObject(i)
            val sessionId = o.getLong("sessionId")
            points.getOrPut(sessionId) { mutableListOf() } += SessionDataPoint(
                sessionId = sessionId, timestampMs = o.getLong("timestampMs"),
                rpm = o.nullableInt("rpm"), speedKph = o.nullableInt("speedKph"),
                coolantTempC = o.nullableInt("coolantTempC"),
                throttlePercent = o.nullableFloat("throttlePercent"),
                engineLoadPercent = o.nullableFloat("engineLoadPercent"),
                mafGramsPerSec = o.nullableFloat("mafGramsPerSec"),
                batteryVoltage = o.nullableFloat("batteryVoltage"),
                cvtSpeedKph = o.nullableInt("cvtSpeedKph"),
                cvtEngineRpm = o.nullableInt("cvtEngineRpm"),
                cvtSlipRpm = o.nullableInt("cvtSlipRpm"),
                cvtTempC = o.nullableInt("cvtTempC"),
                cvtRatio = o.nullableFloat("cvtRatio"),
                cvtPrimaryPressureMpa = o.nullableFloat("cvtPrimaryPressureMpa"),
                cvtSecondaryPressureMpa = o.nullableFloat("cvtSecondaryPressureMpa"),
                cvtTargetSecondaryPressureMpa = o.nullableFloat("cvtTargetSecondaryPressureMpa"),
                mazdaSelectedGear = if (o.isNull("mazdaSelectedGear")) null else o.optString("mazdaSelectedGear"),
                mazdaTransmissionTempC = o.nullableDouble("mazdaTransmissionTempC"),
                mazdaFrontLeftWheelSpeedKph = o.nullableInt("mazdaFrontLeftWheelSpeedKph"),
                mazdaFrontRightWheelSpeedKph = o.nullableInt("mazdaFrontRightWheelSpeedKph"),
                mazdaRearLeftWheelSpeedKph = o.nullableInt("mazdaRearLeftWheelSpeedKph"),
                mazdaRearRightWheelSpeedKph = o.nullableInt("mazdaRearRightWheelSpeedKph"),
                latitude = o.nullableDouble("latitude"), longitude = o.nullableDouble("longitude"),
                accuracy = o.nullableFloat("accuracy"),
            )
        }
        return ImportedSessionBackup(sessions, points)
    }

    // Backward compatibility with the per-session CSV files produced by earlier app versions.
    private fun parseLegacyCsv(raw: String): ImportedSessionBackup {
        val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.any { it.startsWith("# OBDII Tools Session Export") }) { "Select an OBDII Tools ZIP backup or a previously exported session CSV" }
        val meta = lines.filter { it.startsWith("# ") && it.contains(',') }
            .associate { it.removePrefix("# ").substringBefore(',') to it.substringAfter(',').trim().trim('"') }
        val start = meta["Start"] ?: error("CSV is missing its session start time")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val startMs = sdf.parse(start)?.time ?: error("Invalid session start time")
        val endMs = meta["End"]?.let { sdf.parse(it)?.time }
        val device = meta["Device"].orEmpty()
        val vehicle = meta["Vehicle"].orEmpty().split(' ', limit = 2)
        val sessionId = startMs
        val headerIndex = lines.indexOfFirst { it.startsWith("timestamp_ms,") }
        val importedPoints = mutableListOf<SessionDataPoint>()
        if (headerIndex >= 0) {
            val headers = lines[headerIndex].split(',').mapIndexed { index, name -> name to index }.toMap()
            lines.drop(headerIndex + 1).forEach { line ->
                val c = line.split(',')
                fun cell(name: String): String? = headers[name]?.let(c::getOrNull)?.takeIf { it.isNotBlank() }
                val ts = cell("timestamp_ms")?.toLongOrNull() ?: return@forEach
                importedPoints += SessionDataPoint(
                    sessionId = sessionId,
                    timestampMs = ts,
                    rpm = cell("rpm")?.toIntOrNull(),
                    speedKph = cell("speed_kph")?.toIntOrNull(),
                    coolantTempC = cell("coolant_c")?.toIntOrNull(),
                    throttlePercent = cell("throttle_pct")?.toFloatOrNull(),
                    engineLoadPercent = cell("engine_load_pct")?.toFloatOrNull(),
                    mafGramsPerSec = cell("maf_g_s")?.toFloatOrNull(),
                    batteryVoltage = cell("battery_v")?.toFloatOrNull(),
                    cvtSpeedKph = cell("cvt_speed_kph")?.toIntOrNull(),
                    cvtEngineRpm = cell("cvt_engine_rpm")?.toIntOrNull(),
                    cvtSlipRpm = cell("cvt_slip_rpm")?.toIntOrNull(),
                    cvtTempC = cell("cvt_temp_c")?.toIntOrNull(),
                    cvtRatio = cell("cvt_ratio")?.toFloatOrNull(),
                    cvtPrimaryPressureMpa = cell("cvt_primary_pressure_mpa")?.toFloatOrNull(),
                    cvtSecondaryPressureMpa = cell("cvt_secondary_pressure_mpa")?.toFloatOrNull(),
                    cvtTargetSecondaryPressureMpa = cell("cvt_target_secondary_pressure_mpa")?.toFloatOrNull(),
                    mazdaSelectedGear = cell("mazda_selected_gear"),
                    mazdaTransmissionTempC = cell("mazda_transmission_temp_c")?.toDoubleOrNull(),
                    mazdaFrontLeftWheelSpeedKph = cell("mazda_front_left_wheel_speed_kph")?.toIntOrNull(),
                    mazdaFrontRightWheelSpeedKph = cell("mazda_front_right_wheel_speed_kph")?.toIntOrNull(),
                    mazdaRearLeftWheelSpeedKph = cell("mazda_rear_left_wheel_speed_kph")?.toIntOrNull(),
                    mazdaRearRightWheelSpeedKph = cell("mazda_rear_right_wheel_speed_kph")?.toIntOrNull(),
                    latitude = cell("latitude")?.toDoubleOrNull(),
                    longitude = cell("longitude")?.toDoubleOrNull(),
                    accuracy = cell("gps_accuracy_m")?.toFloatOrNull(),
                )
            }
        }
        return ImportedSessionBackup(
            listOf(SessionEntity(
                id = sessionId, startTimeMs = startMs, endTimeMs = endMs,
                deviceName = device, make = vehicle.getOrElse(0) { "" },
                model = vehicle.getOrElse(1) { "" }, vin = meta["VIN"],
                maxRpm = meta["Max RPM"]?.toIntOrNull(),
                maxSpeedKph = meta["Max Speed"]?.substringBefore(' ')?.toIntOrNull(),
                maxCoolantTempC = meta["Max Coolant"]?.substringBefore(' ')?.toIntOrNull(),
                distanceKm = meta["Distance"]?.substringBefore(' ')?.toFloatOrNull(),
            )),
            mapOf(sessionId to importedPoints),
        )
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key)
    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)
    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)
    private fun JSONObject.nullableFloat(key: String): Float? =
        if (isNull(key) || !has(key)) null else optDouble(key).toFloat()
    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key)
}
