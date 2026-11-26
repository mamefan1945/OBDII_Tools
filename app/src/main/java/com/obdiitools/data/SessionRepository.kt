package com.obdiitools.data

import com.obdiitools.obd.OBDData
import com.obdiitools.obd.CVTData
import com.obdiitools.util.UnitConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val gpsRepository: GpsRepository,
) {
    private val mutex = Mutex()
    private val lifecycleMutex = Mutex()
    private val gpsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gpsCollectionJob: Job? = null
    private var currentSessionId: Long? = null
    private var sessionMaxRpm: Int? = null
    private var sessionMaxSpeed: Int? = null
    private var sessionMaxCoolant: Int? = null
    private var sessionSpeedSum: Long = 0
    private var sessionSpeedCount: Int = 0
    private var lastSpeedKph: Int? = null
    private var lastTimestampMs: Long? = null
    private var totalDistanceKm: Float = 0f
    private var totalFuelLitres: Float = 0f
    private var lastCvtPointMs: Long = 0L

    private companion object {
        const val SESSION_POINT_MATCH_WINDOW_MS = 750L
        const val MAX_TRIP_GPS_ACCURACY_METERS = 50f
        const val MAX_TRIP_GPS_GAP_MS = 5_000L
        const val MAX_TRIP_GPS_SEGMENT_METERS = 50.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }

    private val _sessionFuelLitres = MutableStateFlow(0f)
    val sessionFuelLitres: StateFlow<Float> = _sessionFuelLitres.asStateFlow()

    val allSessions: Flow<List<SessionEntity>> = dao.getAllSessions()

    suspend fun startSession(
        deviceName: String,
        deviceAddress: String = "",
        transport: String = "unknown",
        adapterAti: String = "",
        adapterDescription: String = "",
        adapterManufacturerData: String = "",
    ): Long = lifecycleMutex.withLock {
        if (currentSessionId != null) endSessionLocked()
        val now = System.currentTimeMillis()
        dao.getOpenSessions().forEach { orphan -> dao.updateSession(orphan.copy(endTimeMs = now)) }
        val session = SessionEntity(
            startTimeMs = System.currentTimeMillis(), deviceName = deviceName,
            deviceAddress = deviceAddress, transport = transport,
            adapterAti = adapterAti, adapterDescription = adapterDescription,
            adapterManufacturerData = adapterManufacturerData,
        )
        val id = dao.insertSession(session)
        currentSessionId = id
        sessionMaxRpm = null
        sessionMaxSpeed = null
        sessionMaxCoolant = null
        sessionSpeedSum = 0
        sessionSpeedCount = 0
        lastSpeedKph = null
        lastTimestampMs = null
        totalDistanceKm = 0f
        totalFuelLitres = 0f
        lastCvtPointMs = 0L
        _sessionFuelLitres.value = 0f
        gpsCollectionJob?.cancel()
        gpsCollectionJob = gpsScope.launch {
            gpsRepository.locations.collect { location ->
                mutex.withLock {
                    val activeSessionId = currentSessionId ?: return@withLock
                    val nearbyObdPoint = dao.findNearbyObdPoint(
                        activeSessionId, location.time, SESSION_POINT_MATCH_WINDOW_MS
                    )
                    if (nearbyObdPoint != null) {
                        dao.updatePointWithGps(
                            nearbyObdPoint.id, location.latitude, location.longitude, location.accuracy
                        )
                    } else {
                        dao.insertDataPoint(
                            SessionDataPoint(
                                sessionId = activeSessionId,
                                timestampMs = location.time,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy,
                            )
                        )
                    }
                }
            }
        }
        gpsRepository.startUpdates()
        id
    }

    suspend fun recordDataPoint(data: OBDData) = mutex.withLock {
        val sessionId = currentSessionId ?: return@withLock
        val now = System.currentTimeMillis()

        data.rpm?.let { if (sessionMaxRpm == null || it > sessionMaxRpm!!) sessionMaxRpm = it }
        data.coolantTempC?.let { if (sessionMaxCoolant == null || it > sessionMaxCoolant!!) sessionMaxCoolant = it }
        data.speedKph?.let { speed ->
            if (sessionMaxSpeed == null || speed > sessionMaxSpeed!!) sessionMaxSpeed = speed
            sessionSpeedSum += speed
            sessionSpeedCount++
            lastTimestampMs?.let { prevTime ->
                lastSpeedKph?.let { prevSpeed ->
                    val elapsedHours = (now - prevTime) / 3_600_000f
                    totalDistanceKm += ((prevSpeed + speed) / 2f) * elapsedHours
                }
            }
        }

        // Accumulate fuel consumed using MAF-based flow rate
        data.mafGramsPerSec?.let { maf ->
            if (maf > 0f) {
                lastTimestampMs?.let { prevTime ->
                    val elapsedHours = (now - prevTime) / 3_600_000f
                    totalFuelLitres += UnitConverter.fuelFlowLph(maf) * elapsedHours
                    _sessionFuelLitres.value = totalFuelLitres
                }
            }
        }

        lastSpeedKph = data.speedKph
        lastTimestampMs = now

        val nearbyCvtPoint = dao.findNearbyCvtOnlyPoint(sessionId, now, SESSION_POINT_MATCH_WINDOW_MS)
        if (nearbyCvtPoint != null) {
            dao.updatePointWithObd(
                id = nearbyCvtPoint.id,
                rpm = data.rpm,
                speedKph = data.speedKph,
                coolantTempC = data.coolantTempC,
                throttlePercent = data.throttlePercent,
                engineLoadPercent = data.engineLoadPercent,
                mafGramsPerSec = data.mafGramsPerSec,
                fuelLevelPercent = data.fuelLevelPercent,
                batteryVoltage = data.batteryVoltage,
                ambientTempC = data.ambientTempC,
            )
        } else {
            dao.insertDataPoint(
                SessionDataPoint(
                    sessionId = sessionId,
                    timestampMs = now,
                    rpm = data.rpm,
                    speedKph = data.speedKph,
                    coolantTempC = data.coolantTempC,
                    throttlePercent = data.throttlePercent,
                    engineLoadPercent = data.engineLoadPercent,
                    mafGramsPerSec = data.mafGramsPerSec,
                    fuelLevelPercent = data.fuelLevelPercent,
                    batteryVoltage = data.batteryVoltage,
                    ambientTempC = data.ambientTempC,
                    latitude = null,
                    longitude = null,
                    accuracy = null,
                )
            )
        }
    }

    suspend fun recordDiagnosticEvent(eventType: String, detail: String? = null) = mutex.withLock {
        val sessionId = currentSessionId ?: return@withLock
        val now = System.currentTimeMillis()
        dao.insertDataPoint(
            SessionDataPoint(
                sessionId = sessionId,
                timestampMs = now,
                diagnosticEventType = eventType.take(64),
                diagnosticEventDetail = detail?.take(512),
            )
        )
    }

    suspend fun recordMazdaTcmData(
        gear: String?,
        transmissionTemperatureC: Double?,
        frontLeftWheelSpeedKph: Int? = null,
        frontRightWheelSpeedKph: Int? = null,
        rearLeftWheelSpeedKph: Int? = null,
        rearRightWheelSpeedKph: Int? = null,
    ) = mutex.withLock {
        val sessionId = currentSessionId ?: return@withLock
        if (gear == null && transmissionTemperatureC == null &&
            frontLeftWheelSpeedKph == null && frontRightWheelSpeedKph == null &&
            rearLeftWheelSpeedKph == null && rearRightWheelSpeedKph == null) return@withLock
        val now = System.currentTimeMillis()
        // Prefer attaching TCM values to the nearest standard OBD sample. If none
        // exists, merge with a nearby Mazda-only sample before creating another row.
        val nearbyPoint = dao.findNearbyObdPoint(sessionId, now, SESSION_POINT_MATCH_WINDOW_MS)
            ?: dao.findNearbyMazdaOnlyPoint(sessionId, now, SESSION_POINT_MATCH_WINDOW_MS)
        if (nearbyPoint != null) {
            dao.updatePointWithMazdaTcm(nearbyPoint.id, gear, transmissionTemperatureC, frontLeftWheelSpeedKph, frontRightWheelSpeedKph, rearLeftWheelSpeedKph, rearRightWheelSpeedKph)
        } else {
            dao.insertDataPoint(
                SessionDataPoint(
                    sessionId = sessionId,
                    timestampMs = now,
                    mazdaSelectedGear = gear,
                    mazdaTransmissionTempC = transmissionTemperatureC,
                    mazdaFrontLeftWheelSpeedKph = frontLeftWheelSpeedKph,
                    mazdaFrontRightWheelSpeedKph = frontRightWheelSpeedKph,
                    mazdaRearLeftWheelSpeedKph = rearLeftWheelSpeedKph,
                    mazdaRearRightWheelSpeedKph = rearRightWheelSpeedKph,
                )
            )
        }
    }

    suspend fun recordCvtData(data: CVTData) = mutex.withLock {
        val sessionId = currentSessionId ?: return@withLock
        val now = System.currentTimeMillis()
        if (now - lastCvtPointMs < 1000L) return@withLock
        val values = listOf(
            data.speedKph.value, data.engineRpm.value, data.slipRpm.value,
            data.cvtTempC.value, data.cvtRatio.value, data.primaryPressureMpa.value,
            data.secondaryPressureMpa.value, data.targetSecondaryPressureMpa.value,
        )
        if (values.all { it == null }) return@withLock
        val nearbyObdPoint = dao.findNearbyObdPoint(sessionId, now, SESSION_POINT_MATCH_WINDOW_MS)
        if (nearbyObdPoint != null) {
            dao.updatePointWithCvt(
                id = nearbyObdPoint.id,
                cvtSpeedKph = data.speedKph.value,
                cvtEngineRpm = data.engineRpm.value,
                cvtSlipRpm = data.slipRpm.value,
                cvtTempC = data.cvtTempC.value,
                cvtRatio = data.cvtRatio.value,
                cvtPrimaryPressureMpa = data.primaryPressureMpa.value,
                cvtSecondaryPressureMpa = data.secondaryPressureMpa.value,
                cvtTargetSecondaryPressureMpa = data.targetSecondaryPressureMpa.value,
            )
        } else {
            dao.insertDataPoint(
                SessionDataPoint(
                    sessionId = sessionId,
                    timestampMs = now,
                    cvtSpeedKph = data.speedKph.value,
                    cvtEngineRpm = data.engineRpm.value,
                    cvtSlipRpm = data.slipRpm.value,
                    cvtTempC = data.cvtTempC.value,
                    cvtRatio = data.cvtRatio.value,
                    cvtPrimaryPressureMpa = data.primaryPressureMpa.value,
                    cvtSecondaryPressureMpa = data.secondaryPressureMpa.value,
                    cvtTargetSecondaryPressureMpa = data.targetSecondaryPressureMpa.value,
                )
            )
        }
        lastCvtPointMs = now
    }

    suspend fun endSession(expectedSessionId: Long? = null): TripSummary? =
        lifecycleMutex.withLock { endSessionLocked(expectedSessionId) }

    private suspend fun endSessionLocked(expectedSessionId: Long? = null): TripSummary? {
        // A delayed disconnect callback must never end a newer trip session.
        if (expectedSessionId != null && currentSessionId != expectedSessionId) return null
        // Atomically snapshot accumulated stats and mark session ended so no
        // further recordDataPoint() calls can modify them.
        data class Snap(val id: Long, val maxRpm: Int?, val maxSpeed: Int?, val maxCoolant: Int?,
                        val distKm: Float?, val speedSum: Long, val speedCount: Int, val fuelLitres: Float?)

        gpsRepository.stopUpdates()
        gpsCollectionJob?.cancelAndJoin()
        gpsCollectionJob = null

        val snap = mutex.withLock {
            val id = currentSessionId ?: return@withLock null
            Snap(id, sessionMaxRpm, sessionMaxSpeed, sessionMaxCoolant,
                 totalDistanceKm.takeIf { it > 0f }, sessionSpeedSum, sessionSpeedCount,
                 totalFuelLitres.takeIf { it > 0f })
                .also { currentSessionId = null }
        } ?: return null

        val session = dao.getSessionById(snap.id) ?: return null
        val endTime = System.currentTimeMillis()

        // OBD speed is polled less frequently than GPS and may be missing on
        // individual cycles. Prefer the recorded GPS track for trip distance,
        // rejecting inaccurate fixes, long gaps, and implausible jumps.
        val gpsDistanceKm = calculateGpsDistanceKm(dao.getGpsPointsForSession(snap.id))
        val tripDistanceKm = gpsDistanceKm?.takeIf { it > 0f } ?: snap.distKm

        dao.updateSession(session.copy(
            endTimeMs = endTime,
            maxRpm = snap.maxRpm,
            maxSpeedKph = snap.maxSpeed,
            maxCoolantTempC = snap.maxCoolant,
            distanceKm = tripDistanceKm,
            totalFuelLitres = snap.fuelLitres,
        ))

        val duration = endTime - session.startTimeMs
        return if (duration < 30_000L) null else TripSummary(
            deviceName = session.deviceName,
            make = session.make,
            model = session.model,
            durationMs = duration,
            distanceKm = tripDistanceKm,
            maxRpm = snap.maxRpm,
            maxSpeedKph = snap.maxSpeed,
            maxCoolantTempC = snap.maxCoolant,
            totalFuelLitres = snap.fuelLitres,
            avgSpeedKph = tripDistanceKm?.let { dist ->
                val hours = (endTime - session.startTimeMs) / 3_600_000f
                if (hours > 0f) dist / hours else null
            } ?: if (snap.speedCount > 0) snap.speedSum.toFloat() / snap.speedCount else null,
        )
    }

    suspend fun updateCurrentSessionMakeModel(make: String, model: String, vin: String? = null) {
        val id = currentSessionId ?: return
        dao.updateMakeModelVin(id, make, model, vin)
    }

    suspend fun getDataPoints(sessionId: Long): List<SessionDataPoint> =
        dao.getDataPointsForSession(sessionId)

    suspend fun importSessions(sessions: List<SessionEntity>, pointsByOldId: Map<Long, List<SessionDataPoint>>): Pair<Int, Int> {
        var imported = 0
        var skipped = 0
        sessions.forEach { oldSession ->
            // Match on the trip start timestamp, not the backup's database ID or optional vehicle fields.
            if (dao.findMatchingImportedSession(oldSession.startTimeMs) != null) {
                skipped++
                return@forEach
            }
            val newId = dao.insertImportedSession(oldSession.copy(id = 0))
            if (newId != -1L) {
                imported++
                val points = pointsByOldId[oldSession.id].orEmpty().map { it.copy(id = 0, sessionId = newId) }
                if (points.isNotEmpty()) dao.insertImportedDataPoints(points)
            }
        }
        return imported to skipped
    }

    suspend fun getGpsPoints(sessionId: Long): List<SessionDataPoint> =
        dao.getGpsPointsForSession(sessionId)

    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)

    private fun calculateGpsDistanceKm(points: List<SessionDataPoint>): Float? {
        val fixes = points.asSequence()
            .filter { it.latitude != null && it.longitude != null }
            .filter { (it.accuracy ?: Float.MAX_VALUE) <= MAX_TRIP_GPS_ACCURACY_METERS }
            .sortedBy { it.timestampMs }
            .toList()
        if (fixes.size < 2) return null

        var distanceMeters = 0.0
        for (index in 1 until fixes.size) {
            val previous = fixes[index - 1]
            val current = fixes[index]
            val elapsedMs = current.timestampMs - previous.timestampMs
            if (elapsedMs <= 0L || elapsedMs > MAX_TRIP_GPS_GAP_MS) continue

            val lat1 = Math.toRadians(previous.latitude!!)
            val lat2 = Math.toRadians(current.latitude!!)
            val deltaLat = lat2 - lat1
            val deltaLon = Math.toRadians(current.longitude!! - previous.longitude!!)
            val a = kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(deltaLon / 2) * kotlin.math.sin(deltaLon / 2)
            val segmentMeters = 2.0 * EARTH_RADIUS_METERS *
                kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))

            // A single bad fix must not inflate the trip distance.
            if (segmentMeters <= MAX_TRIP_GPS_SEGMENT_METERS) distanceMeters += segmentMeters
        }
        return (distanceMeters / 1000.0).toFloat().takeIf { it > 0f }
    }

}
