package com.obdiitools.data

data class TripSummary(
    val deviceName: String,
    val make: String,
    val model: String,
    val durationMs: Long,
    val distanceKm: Float?,
    val maxRpm: Int?,
    val maxSpeedKph: Int?,
    val maxCoolantTempC: Int?,
    val avgSpeedKph: Float?,
)
