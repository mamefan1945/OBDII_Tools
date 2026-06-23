package com.obdiitools.data

import com.obdiitools.obd.OBDData
import com.obdiitools.util.UnitConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val dao: SessionDao,
) {
    private val mutex = Mutex()
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

    private val _sessionFuelLitres = MutableStateFlow(0f)
    val sessionFuelLitres: StateFlow<Float> = _sessionFuelLitres.asStateFlow()

    val allSessions: Flow<List<SessionEntity>> = dao.getAllSessions()

    suspend fun startSession(deviceName: String): Long {
        if (currentSessionId != null) endSession()
        val now = System.currentTimeMillis()
        dao.getOpenSessions().forEach { orphan -> dao.updateSession(orphan.copy(endTimeMs = now)) }
        val session = SessionEntity(startTimeMs = System.currentTimeMillis(), deviceName = deviceName)
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
        _sessionFuelLitres.value = 0f
        return id
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
                batteryVoltage = data.batteryVoltage,
            )
        )
    }

    suspend fun endSession(): TripSummary? {
        // Atomically snapshot accumulated stats and mark session ended so no
        // further recordDataPoint() calls can modify them.
        data class Snap(val id: Long, val maxRpm: Int?, val maxSpeed: Int?, val maxCoolant: Int?,
                        val distKm: Float?, val speedSum: Long, val speedCount: Int, val fuelLitres: Float?)

        val snap = mutex.withLock {
            val id = currentSessionId ?: return@withLock null
            Snap(id, sessionMaxRpm, sessionMaxSpeed, sessionMaxCoolant,
                 totalDistanceKm.takeIf { it > 0f }, sessionSpeedSum, sessionSpeedCount,
                 totalFuelLitres.takeIf { it > 0f })
                .also { currentSessionId = null }
        } ?: return null

        val session = dao.getSessionById(snap.id) ?: return null
        val endTime = System.currentTimeMillis()
        dao.updateSession(session.copy(
            endTimeMs = endTime,
            maxRpm = snap.maxRpm,
            maxSpeedKph = snap.maxSpeed,
            maxCoolantTempC = snap.maxCoolant,
            distanceKm = snap.distKm,
            totalFuelLitres = snap.fuelLitres,
        ))

        val duration = endTime - session.startTimeMs
        return if (duration < 30_000L) null else TripSummary(
            deviceName = session.deviceName,
            make = session.make,
            model = session.model,
            durationMs = duration,
            distanceKm = snap.distKm,
            maxRpm = snap.maxRpm,
            maxSpeedKph = snap.maxSpeed,
            maxCoolantTempC = snap.maxCoolant,
            totalFuelLitres = snap.fuelLitres,
            avgSpeedKph = snap.distKm?.let { dist ->
                val hours = (endTime - session.startTimeMs) / 3_600_000f
                if (hours > 0f) dist / hours else null
            } ?: if (snap.speedCount > 0) snap.speedSum.toFloat() / snap.speedCount else null,
        )
    }

    suspend fun updateCurrentSessionMakeModel(make: String, model: String) {
        val id = currentSessionId ?: return
        dao.updateMakeModel(id, make, model)
    }

    suspend fun getDataPoints(sessionId: Long): List<SessionDataPoint> =
        dao.getDataPointsForSession(sessionId)

    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)
}
