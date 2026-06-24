package com.obdiitools.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE endTimeMs IS NULL")
    suspend fun getOpenSessions(): List<SessionEntity>

    @Query("SELECT * FROM session_data_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getDataPointsForSession(sessionId: Long): List<SessionDataPoint>

    @Query("UPDATE sessions SET make = :make, model = :model WHERE id = :id")
    suspend fun updateMakeModel(id: Long, make: String, model: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM session_data_points WHERE sessionId = :sessionId AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY timestampMs ASC")
    suspend fun getGpsPointsForSession(sessionId: Long): List<SessionDataPoint>
}
