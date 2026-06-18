package com.obdiitools.obd

data class OBDData(
    val rpm: Int? = null,
    val speedKph: Int? = null,
    val coolantTempC: Int? = null,
    val intakeAirTempC: Int? = null,
    val throttlePercent: Float? = null,
    val engineLoadPercent: Float? = null,
    val fuelLevelPercent: Float? = null,
    val timingAdvanceDeg: Float? = null,
    val mafGramsPerSec: Float? = null,
    val oilTempC: Int? = null,
    val fuelPressureKpa: Int? = null,
    val baroPressureKpa: Int? = null,
    val ambientTempC: Int? = null,
    val batteryVoltage: Float? = null,
    // Fuel trims
    val stftBank1Pct: Float? = null,
    val ltftBank1Pct: Float? = null,
    val stftBank2Pct: Float? = null,
    val ltftBank2Pct: Float? = null,
    // O2 sensors (voltage in volts)
    val o2Bank1S1Volts: Float? = null,
    val o2Bank1S2Volts: Float? = null,
    val o2Bank2S1Volts: Float? = null,
    val o2Bank2S2Volts: Float? = null,
)

data class DTC(
    val code: String,
    val description: String = "",
    val isPending: Boolean = false,
)

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String, val deviceAddress: String = "") : ConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String = "") : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isPaired: Boolean = false,
    val isBle: Boolean = false,
)
