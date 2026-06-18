package com.obdiitools.obd

enum class CVTDataSource(val badge: String) {
    SENSOR("SENSOR"),
    INFERRED("INFERRED"),
    UNAVAILABLE("N/A"),
}

enum class LockupState(val label: String, val description: String) {
    LOCKED("LOCKED",   "Converter clutch engaged — no slip"),
    SLIPPING("SLIPPING", "Converter clutch partially engaged"),
    OPEN("OPEN",       "Fluid coupling active — torque converter open"),
    UNKNOWN("UNKNOWN", "Insufficient data to determine state"),
}

data class CVTReading<T>(
    val value: T?,
    val source: CVTDataSource = CVTDataSource.UNAVAILABLE,
)

data class CVTData(
    val speedKph:      CVTReading<Int>           = CVTReading(null),
    val engineRpm:     CVTReading<Int>           = CVTReading(null),
    val inputShaftRpm: CVTReading<Int>           = CVTReading(null),
    val slipRpm:       CVTReading<Int>           = CVTReading(null),
    val lockupState:   CVTReading<LockupState>   = CVTReading(LockupState.UNKNOWN),
    val cvtTempC:      CVTReading<Int>           = CVTReading(null),
    val cvtRatio:      CVTReading<Float>         = CVTReading(null),
)
