package com.obdiitools.data

enum class AlertType(val label: String) {
    HIGH_COOLANT("High Coolant Temp"),
    LOW_FUEL("Low Fuel Level"),
    LOW_BATTERY("Low Battery Voltage"),
    HIGH_RPM("High RPM"),
}
