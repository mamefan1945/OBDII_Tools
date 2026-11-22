package com.obdiitools.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Insert
    suspend fun insertDataPoint(point: SessionDataPoint)

    @Query("""
        SELECT * FROM session_data_points
        WHERE sessionId = :sessionId
          AND ABS(timestampMs - :timestampMs) <= :maxDeltaMs
          AND (rpm IS NOT NULL OR speedKph IS NOT NULL OR coolantTempC IS NOT NULL
               OR throttlePercent IS NOT NULL OR engineLoadPercent IS NOT NULL
               OR mafGramsPerSec IS NOT NULL OR batteryVoltage IS NOT NULL OR ambientTempC IS NOT NULL)
        ORDER BY ABS(timestampMs - :timestampMs) ASC
        LIMIT 1
    """)
    suspend fun findNearbyObdPoint(sessionId: Long, timestampMs: Long, maxDeltaMs: Long): SessionDataPoint?

    @Query("""
        SELECT * FROM session_data_points
        WHERE sessionId = :sessionId
          AND ABS(timestampMs - :timestampMs) <= :maxDeltaMs
          AND rpm IS NULL AND speedKph IS NULL AND coolantTempC IS NULL
          AND throttlePercent IS NULL AND engineLoadPercent IS NULL
          AND mafGramsPerSec IS NULL AND batteryVoltage IS NULL
          AND (
               latitude IS NOT NULL AND longitude IS NOT NULL
               OR cvtSpeedKph IS NOT NULL OR cvtEngineRpm IS NOT NULL OR cvtSlipRpm IS NOT NULL
               OR cvtTempC IS NOT NULL OR cvtRatio IS NOT NULL
               OR cvtPrimaryPressureMpa IS NOT NULL OR cvtSecondaryPressureMpa IS NOT NULL
               OR cvtTargetSecondaryPressureMpa IS NOT NULL
          )
        ORDER BY ABS(timestampMs - :timestampMs) ASC
        LIMIT 1
    """)
    suspend fun findNearbyCvtOnlyPoint(sessionId: Long, timestampMs: Long, maxDeltaMs: Long): SessionDataPoint?

    @Query("""
        UPDATE session_data_points SET
            rpm = :rpm, speedKph = :speedKph, coolantTempC = :coolantTempC,
            throttlePercent = :throttlePercent, engineLoadPercent = :engineLoadPercent,
            mafGramsPerSec = :mafGramsPerSec, batteryVoltage = :batteryVoltage,
            ambientTempC = :ambientTempC
        WHERE id = :id
    """)
    suspend fun updatePointWithObd(
        id: Long, rpm: Int?, speedKph: Int?, coolantTempC: Int?,
        throttlePercent: Float?, engineLoadPercent: Float?, mafGramsPerSec: Float?,
        batteryVoltage: Float?, ambientTempC: Int?,
    )

    @Query("""
        UPDATE session_data_points SET
            cvtSpeedKph = :cvtSpeedKph, cvtEngineRpm = :cvtEngineRpm,
            cvtSlipRpm = :cvtSlipRpm, cvtTempC = :cvtTempC, cvtRatio = :cvtRatio,
            cvtPrimaryPressureMpa = :cvtPrimaryPressureMpa,
            cvtSecondaryPressureMpa = :cvtSecondaryPressureMpa,
            cvtTargetSecondaryPressureMpa = :cvtTargetSecondaryPressureMpa
        WHERE id = :id
    """)
    suspend fun updatePointWithCvt(
        id: Long, cvtSpeedKph: Int?, cvtEngineRpm: Int?, cvtSlipRpm: Int?,
        cvtTempC: Int?, cvtRatio: Float?, cvtPrimaryPressureMpa: Float?,
        cvtSecondaryPressureMpa: Float?, cvtTargetSecondaryPressureMpa: Float?,
    )

    @Query("UPDATE session_data_points SET latitude = :latitude, longitude = :longitude, accuracy = :accuracy WHERE id = :id")
    suspend fun updatePointWithGps(id: Long, latitude: Double, longitude: Double, accuracy: Float)

    @Query("UPDATE session_data_points SET mazdaSelectedGear = :gear, mazdaTransmissionTempC = :temperatureC WHERE id = :id")
    suspend fun updatePointWithMazdaTcm(id: Long, gear: String?, temperatureC: Double?)

    @Query("UPDATE session_data_points SET diagnosticEventType = :eventType, diagnosticEventDetail = :detail WHERE id = :id")
    suspend fun updatePointWithDiagnosticEvent(id: Long, eventType: String, detail: String?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImportedSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImportedDataPoints(points: List<SessionDataPoint>)

    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startTimeMs = :startTimeMs LIMIT 1")
    suspend fun findMatchingImportedSession(startTimeMs: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE endTimeMs IS NULL")
    suspend fun getOpenSessions(): List<SessionEntity>

    @Query("SELECT * FROM session_data_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getDataPointsForSession(sessionId: Long): List<SessionDataPoint>

    @Query("UPDATE sessions SET make = :make, model = :model, vin = :vin WHERE id = :id")
    suspend fun updateMakeModelVin(id: Long, make: String, model: String, vin: String?)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM session_data_points WHERE sessionId = :sessionId AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY timestampMs ASC")
    suspend fun getGpsPointsForSession(sessionId: Long): List<SessionDataPoint>
}
