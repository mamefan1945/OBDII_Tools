package com.obdiitools.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val maxRpm: Int? = null,
    val maxSpeedKph: Int? = null,
    val maxCoolantTempC: Int? = null,
    val distanceKm: Float? = null,
    val deviceName: String = "",
)

@Entity(
    tableName = "session_data_points",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class SessionDataPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val rpm: Int? = null,
    val speedKph: Int? = null,
    val coolantTempC: Int? = null,
    val throttlePercent: Float? = null,
    val engineLoadPercent: Float? = null,
    val mafGramsPerSec: Float? = null,
    val batteryVoltage: Float? = null,
)
