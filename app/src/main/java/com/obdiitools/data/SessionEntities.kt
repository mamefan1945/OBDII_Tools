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
    val make: String = "",
    val model: String = "",
    val totalFuelLitres: Float? = null,
    val vin: String? = null,
    val deviceAddress: String = "",
    val transport: String = "unknown",
    val adapterAti: String = "",
    val adapterDescription: String = "",
    val adapterManufacturerData: String = "",
    val adapterManufacturer: String = "",
    val adapterIdentificationEvidence: String = "",
    val adapterServiceUUIDs: String = "",
    val adapterRssi: Int? = null,
    val adapterModel: String = "",
    val adapterModelConfidence: String = "unknown",
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
    val fuelLevelPercent: Float? = null,
    val batteryVoltage: Float? = null,
    val cvtSpeedKph: Int? = null,
    val cvtEngineRpm: Int? = null,
    val cvtSlipRpm: Int? = null,
    val cvtTempC: Int? = null,
    val cvtRatio: Float? = null,
    val cvtPrimaryPressureMpa: Float? = null,
    val cvtSecondaryPressureMpa: Float? = null,
    val cvtTargetSecondaryPressureMpa: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val mazdaSelectedGear: String? = null,
    val mazdaTransmissionTempC: Double? = null,
    val mazdaFrontLeftWheelSpeedKph: Int? = null,
    val mazdaFrontRightWheelSpeedKph: Int? = null,
    val mazdaRearLeftWheelSpeedKph: Int? = null,
    val mazdaRearRightWheelSpeedKph: Int? = null,
    val ambientTempC: Int? = null,
    val diagnosticEventType: String? = null,
    val diagnosticEventDetail: String? = null,
)
