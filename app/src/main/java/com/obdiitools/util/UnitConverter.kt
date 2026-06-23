package com.obdiitools.util

import com.obdiitools.data.AirMassUnit
import com.obdiitools.data.FuelEconomyUnit
import com.obdiitools.data.FuelPriceMode
import com.obdiitools.data.PressureUnit
import com.obdiitools.data.SpeedUnit
import com.obdiitools.data.TemperatureUnit
import com.obdiitools.data.TorqueUnit
import com.obdiitools.data.UserPreferences

object UnitConverter {

    fun speed(kph: Int, unit: SpeedUnit): Float = when (unit) {
        SpeedUnit.KMH -> kph.toFloat()
        SpeedUnit.MPH -> kph * 0.621371f
    }

    fun speedMax(unit: SpeedUnit): Float = when (unit) {
        SpeedUnit.KMH -> 260f
        SpeedUnit.MPH -> 160f
    }

    fun temperature(celsius: Int, unit: TemperatureUnit): Float = when (unit) {
        TemperatureUnit.CELSIUS    -> celsius.toFloat()
        TemperatureUnit.FAHRENHEIT -> celsius * 9f / 5f + 32f
    }

    fun tempMin(unit: TemperatureUnit): Float = when (unit) {
        TemperatureUnit.CELSIUS    -> -20f
        TemperatureUnit.FAHRENHEIT -> -4f
    }

    fun tempMax(unit: TemperatureUnit): Float = when (unit) {
        TemperatureUnit.CELSIUS    -> 130f
        TemperatureUnit.FAHRENHEIT -> 266f
    }

    fun pressure(kpa: Int, unit: PressureUnit): Float = when (unit) {
        PressureUnit.KPA -> kpa.toFloat()
        PressureUnit.PSI -> kpa * 0.145038f
        PressureUnit.BAR -> kpa * 0.01f
    }

    // 1 Nm = 0.737562 lb·ft
    fun torque(nm: Float, unit: TorqueUnit): Float = when (unit) {
        TorqueUnit.NM    -> nm
        TorqueUnit.LB_FT -> nm * 0.737562f
    }

    // MAF-based fuel economy. AFR=14.64 (stoich gasoline), density=740 g/L.
    // Constant = 3600 / (14.64 × 740) ≈ 0.3323 L/h per g/s.
    fun fuelFlowLph(mafGsec: Float): Float = mafGsec * 0.3323f

    // Returns null below 5 km/h (undefined) or when engine is off (MAF = 0).
    fun fuelEconomyL100km(mafGsec: Float, speedKph: Int): Float? {
        if (speedKph < 5 || mafGsec <= 0f) return null
        return mafGsec * 33.23f / speedKph
    }

    // Returns null when l100km is zero or negative (engine off / EV-only).
    fun l100kmToMpgUs(l100km: Float): Float? {
        if (l100km <= 0f) return null
        return 235.215f / l100km
    }

    // Formatted economy string. Shows L/h when idling, "EV" when engine off at speed.
    fun formatFuelEconomy(mafGsec: Float, speedKph: Int, unit: FuelEconomyUnit): String {
        if (speedKph < 5) return "${"%.1f".format(fuelFlowLph(mafGsec))} L/h"
        val l100km = fuelEconomyL100km(mafGsec, speedKph) ?: return "EV"
        return when (unit) {
            FuelEconomyUnit.L100KM -> "${"%.1f".format(l100km)} L/100km"
            FuelEconomyUnit.MPG_US -> "${"%.1f".format(l100kmToMpgUs(l100km))} mpg"
        }
    }

    // 1 g/s = 0.132277 lb/min
    fun airMass(gPerSec: Float, unit: AirMassUnit): Float = when (unit) {
        AirMassUnit.G_S    -> gPerSec
        AirMassUnit.LB_MIN -> gPerSec * 0.132277f
    }

    fun formatSpeed(kph: Int, unit: SpeedUnit): String = when (unit) {
        SpeedUnit.KMH -> "$kph"
        SpeedUnit.MPH -> "%.0f".format(kph * 0.621371f)
    }

    fun formatTemp(celsius: Int, unit: TemperatureUnit): String = when (unit) {
        TemperatureUnit.CELSIUS    -> "$celsius"
        TemperatureUnit.FAHRENHEIT -> "%.0f".format(celsius * 9f / 5f + 32f)
    }

    fun formatPressure(kpa: Int, unit: PressureUnit): String = when (unit) {
        PressureUnit.KPA -> "$kpa"
        PressureUnit.PSI -> "%.1f".format(kpa * 0.145038f)
        PressureUnit.BAR -> "%.2f".format(kpa * 0.01f)
    }

    fun formatTorque(nm: Float, unit: TorqueUnit): String = when (unit) {
        TorqueUnit.NM    -> "%.0f".format(nm)
        TorqueUnit.LB_FT -> "%.0f".format(nm * 0.737562f)
    }

    fun formatAirMass(gPerSec: Float, unit: AirMassUnit): String = when (unit) {
        AirMassUnit.G_S    -> "%.2f".format(gPerSec)
        AirMassUnit.LB_MIN -> "%.3f".format(gPerSec * 0.132277f)
    }

    // Returns the effective fuel price per litre in the user's local currency.
    // Manual mode: stored in user's natural unit (L or gal), converted to per-litre here.
    // EIA mode: stored as USD/gal, converted to per-litre (user must account for currency).
    fun effectivePricePerLitre(prefs: UserPreferences): Float? = when (prefs.fuelPriceMode) {
        FuelPriceMode.MANUAL -> when (prefs.fuelEconomyUnit) {
            FuelEconomyUnit.L100KM -> prefs.manualFuelPrice
            FuelEconomyUnit.MPG_US -> prefs.manualFuelPrice / 3.78541f
        }
        FuelPriceMode.EIA_AVERAGE -> prefs.eiaFuelPriceUsd?.div(3.78541f)
    }

    fun fuelCost(fuelLitres: Float, prefs: UserPreferences): Float? =
        effectivePricePerLitre(prefs)?.let { fuelLitres * it }

    // EIA price formatted in the user's preferred unit for display in Settings.
    fun eiaDisplayPrice(prefs: UserPreferences): String? {
        val usdPerGal = prefs.eiaFuelPriceUsd ?: return null
        return when (prefs.fuelEconomyUnit) {
            FuelEconomyUnit.MPG_US -> "${"%.3f".format(usdPerGal)} USD/gal"
            FuelEconomyUnit.L100KM -> "${"%.3f".format(usdPerGal / 3.78541f)} USD/L"
        }
    }

    // Label for the manual fuel price input field.
    fun fuelPriceUnitLabel(prefs: UserPreferences): String = when (prefs.fuelEconomyUnit) {
        FuelEconomyUnit.L100KM -> "per litre"
        FuelEconomyUnit.MPG_US -> "per gallon"
    }

    // Converts and formats a raw SI value for display in the All Parameters screen,
    // applying user-selected units where applicable.
    fun formatPidValue(value: Float, siUnit: String, prefs: UserPreferences): String = when (siUnit) {
        "km/h" -> when (prefs.speedUnit) {
            SpeedUnit.KMH -> "${value.toInt()} km/h"
            SpeedUnit.MPH -> "${"%.0f".format(value * 0.621371f)} mph"
        }
        "°C"   -> when (prefs.temperatureUnit) {
            TemperatureUnit.CELSIUS    -> "${value.toInt()} °C"
            TemperatureUnit.FAHRENHEIT -> "${"%.0f".format(value * 9f / 5f + 32f)} °F"
        }
        "kPa"  -> when (prefs.pressureUnit) {
            PressureUnit.KPA -> "${value.toInt()} kPa"
            PressureUnit.PSI -> "${"%.1f".format(value * 0.145038f)} psi"
            PressureUnit.BAR -> "${"%.3f".format(value * 0.01f)} bar"
        }
        "g/s"  -> when (prefs.airMassUnit) {
            AirMassUnit.G_S    -> "${"%.2f".format(value)} g/s"
            AirMassUnit.LB_MIN -> "${"%.3f".format(value * 0.132277f)} lb/min"
        }
        "RPM"  -> "${value.toInt()} RPM"
        "min"  -> "${value.toInt()} min"
        "s"    -> "${value.toInt()} s"
        "km"   -> when (prefs.speedUnit) {
            SpeedUnit.KMH -> "${value.toInt()} km"
            SpeedUnit.MPH -> "${"%.1f".format(value * 0.621371f)} mi"
        }
        "V"    -> "${"%.2f".format(value)} V"
        "L/h"  -> "${"%.1f".format(value)} L/h"
        "%"    -> "${"%.1f".format(value)} %"
        "Pa"   -> "${value.toInt()} Pa"
        "λ"    -> "${"%.3f".format(value)} λ"
        else   -> "${"%.1f".format(value)} $siUnit"
    }
}
