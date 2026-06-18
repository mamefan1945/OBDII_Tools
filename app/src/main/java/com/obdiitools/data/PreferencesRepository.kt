package com.obdiitools.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val SPEED_UNIT      = stringPreferencesKey("speed_unit")
        val TEMP_UNIT       = stringPreferencesKey("temp_unit")
        val PRESSURE_UNIT   = stringPreferencesKey("pressure_unit")
        val TORQUE_UNIT     = stringPreferencesKey("torque_unit")
        val AIR_MASS_UNIT      = stringPreferencesKey("air_mass_unit")
        val FUEL_ECONOMY_UNIT  = stringPreferencesKey("fuel_economy_unit")
        val ALERT_COOLANT      = intPreferencesKey("alert_coolant_max_c")
        val ALERT_FUEL      = intPreferencesKey("alert_fuel_min_pct")
        val ALERT_BATTERY   = floatPreferencesKey("alert_battery_min_v")
        val ALERT_RPM       = intPreferencesKey("alert_rpm_max")
        val KEEP_SCREEN_ON  = booleanPreferencesKey("keep_screen_on")
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            speedUnit       = prefs[Keys.SPEED_UNIT]?.let    { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }       ?: SpeedUnit.KMH,
            temperatureUnit = prefs[Keys.TEMP_UNIT]?.let     { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() } ?: TemperatureUnit.CELSIUS,
            pressureUnit    = prefs[Keys.PRESSURE_UNIT]?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() }    ?: PressureUnit.KPA,
            torqueUnit      = prefs[Keys.TORQUE_UNIT]?.let   { runCatching { TorqueUnit.valueOf(it) }.getOrNull() }      ?: TorqueUnit.NM,
            airMassUnit     = prefs[Keys.AIR_MASS_UNIT]?.let      { runCatching { AirMassUnit.valueOf(it) }.getOrNull() }     ?: AirMassUnit.G_S,
            fuelEconomyUnit = prefs[Keys.FUEL_ECONOMY_UNIT]?.let { runCatching { FuelEconomyUnit.valueOf(it) }.getOrNull() } ?: FuelEconomyUnit.L100KM,
            alertThresholds = AlertThresholds(
                coolantMaxC = prefs[Keys.ALERT_COOLANT]  ?: 105,
                fuelMinPct  = prefs[Keys.ALERT_FUEL]     ?: 10,
                batteryMinV = prefs[Keys.ALERT_BATTERY]  ?: 11.8f,
                rpmMax      = prefs[Keys.ALERT_RPM]      ?: 6500,
            ),
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
        )
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        dataStore.edit { it[Keys.SPEED_UNIT] = unit.name }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { it[Keys.TEMP_UNIT] = unit.name }
    }

    suspend fun setPressureUnit(unit: PressureUnit) {
        dataStore.edit { it[Keys.PRESSURE_UNIT] = unit.name }
    }

    suspend fun setTorqueUnit(unit: TorqueUnit) {
        dataStore.edit { it[Keys.TORQUE_UNIT] = unit.name }
    }

    suspend fun setAirMassUnit(unit: AirMassUnit) {
        dataStore.edit { it[Keys.AIR_MASS_UNIT] = unit.name }
    }

    suspend fun setFuelEconomyUnit(unit: FuelEconomyUnit) {
        dataStore.edit { it[Keys.FUEL_ECONOMY_UNIT] = unit.name }
    }

    suspend fun setAlertThresholds(thresholds: AlertThresholds) {
        dataStore.edit { prefs ->
            prefs[Keys.ALERT_COOLANT] = thresholds.coolantMaxC
            prefs[Keys.ALERT_FUEL]    = thresholds.fuelMinPct
            prefs[Keys.ALERT_BATTERY] = thresholds.batteryMinV
            prefs[Keys.ALERT_RPM]     = thresholds.rpmMax
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }
}
