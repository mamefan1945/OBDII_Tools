package com.obdiitools.data

data class AlertThresholds(
    val coolantMaxC: Int   = 105,
    val fuelMinPct: Int    = 10,
    val batteryMinV: Float = 11.8f,
    val rpmMax: Int        = 6500,
)

data class UserPreferences(
    val speedUnit:       SpeedUnit       = SpeedUnit.KMH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val pressureUnit:    PressureUnit    = PressureUnit.KPA,
    val torqueUnit:      TorqueUnit      = TorqueUnit.NM,
    val airMassUnit:      AirMassUnit      = AirMassUnit.G_S,
    val fuelEconomyUnit:  FuelEconomyUnit  = FuelEconomyUnit.L100KM,
    val alertThresholds:  AlertThresholds  = AlertThresholds(),
    val keepScreenOn:     Boolean          = false,
)

enum class SpeedUnit(val symbol: String, val label: String) {
    KMH("km/h", "Kilometres per hour"),
    MPH("mph",  "Miles per hour"),
}

enum class TemperatureUnit(val symbol: String, val label: String) {
    CELSIUS("°C", "Celsius"),
    FAHRENHEIT("°F", "Fahrenheit"),
}

enum class PressureUnit(val symbol: String, val label: String) {
    KPA("kPa", "Kilopascal"),
    PSI("psi", "Pounds per sq. inch"),
    BAR("bar", "Bar"),
}

enum class TorqueUnit(val symbol: String, val label: String) {
    NM("Nm",    "Newton-meters"),
    LB_FT("lb·ft", "Pound-feet"),
}

enum class AirMassUnit(val symbol: String, val label: String) {
    G_S("g/s",   "Grams per second"),
    LB_MIN("lb/min", "Pounds per minute"),
}

enum class FuelEconomyUnit(val symbol: String, val label: String) {
    L100KM("L/100km", "Litres per 100 km"),
    MPG_US("mpg",     "Miles per gallon (US)"),
}
