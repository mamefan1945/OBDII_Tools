package com.obdiitools.obd

data class FreezeFrame(
    val dtcCode: String,
    val rpm: Int? = null,
    val speedKph: Int? = null,
    val coolantTempC: Int? = null,
    val throttlePercent: Float? = null,
    val engineLoadPercent: Float? = null,
    val mafGramsPerSec: Float? = null,
    val stftBank1Pct: Float? = null,
    val ltftBank1Pct: Float? = null,
    val intakeAirTempC: Int? = null,
    val timingAdvanceDeg: Float? = null,
) {
    val hasData: Boolean get() =
        rpm != null || speedKph != null || coolantTempC != null ||
        throttlePercent != null || engineLoadPercent != null ||
        mafGramsPerSec != null || stftBank1Pct != null || ltftBank1Pct != null ||
        intakeAirTempC != null || timingAdvanceDeg != null
}
