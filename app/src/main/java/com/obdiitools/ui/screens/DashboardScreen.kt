package com.obdiitools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.OBDData
import com.obdiitools.ui.components.CircularGauge
import com.obdiitools.ui.components.ConnectionStatusBar
import com.obdiitools.ui.components.GlassCard
import com.obdiitools.ui.components.LinearGauge
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonPurple
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.data.FuelEconomyUnit
import com.obdiitools.data.UserPreferences
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.util.UnitConverter
import com.obdiitools.viewmodel.MainViewModel

@Composable
fun DashboardScreen(viewModel: MainViewModel, onNavigateToLiveData: () -> Unit = {}) {
    val connectionState by viewModel.connectionState.collectAsState()
    val data by viewModel.obdData.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        if (!isConnected) {
            DisconnectedState()
        } else {
            DashboardContent(
                data = data,
                connectionState = connectionState,
                prefs = prefs,
                onNavigateToLiveData = onNavigateToLiveData,
            )
        }
    }
}

@Composable
private fun DisconnectedState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BluetoothDisabled,
                null,
                tint = SurfaceBorder,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Connect to a device",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "to view live data",
                color = TextSecondary.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    data: OBDData,
    connectionState: ConnectionState,
    prefs: UserPreferences,
    onNavigateToLiveData: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "LIVE DATA",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = NeonCyan,
                letterSpacing = 3.sp,
            )
            Text(
                text = "Dashboard",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = TextPrimary,
            )
        }

        ConnectionStatusBar(state = connectionState)
        HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(20.dp))

        // Primary gauges — RPM + Speed
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CircularGauge(
                value = data.rpm?.toFloat(),
                minValue = 0f,
                maxValue = 8000f,
                label = "ENGINE",
                unit = "RPM",
                size = 170.dp,
                accentColor = NeonCyan,
                showColorZones = true,
            )
            CircularGauge(
                value = data.speedKph?.let { UnitConverter.speed(it, prefs.speedUnit) },
                minValue = 0f,
                maxValue = UnitConverter.speedMax(prefs.speedUnit),
                label = "SPEED",
                unit = prefs.speedUnit.symbol,
                size = 170.dp,
                accentColor = NeonOrange,
                showColorZones = false,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Secondary gauges — Coolant + Throttle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CircularGauge(
                value = data.coolantTempC?.let { UnitConverter.temperature(it, prefs.temperatureUnit) },
                minValue = UnitConverter.tempMin(prefs.temperatureUnit),
                maxValue = UnitConverter.tempMax(prefs.temperatureUnit),
                label = "COOLANT",
                unit = prefs.temperatureUnit.symbol,
                size = 130.dp,
                accentColor = NeonRed,
                showColorZones = true,
            )
            CircularGauge(
                value = data.throttlePercent,
                minValue = 0f,
                maxValue = 100f,
                label = "THROTTLE",
                unit = "%",
                size = 130.dp,
                accentColor = NeonGreen,
            )
            CircularGauge(
                value = data.engineLoadPercent,
                minValue = 0f,
                maxValue = 100f,
                label = "ENGINE LOAD",
                unit = "%",
                size = 130.dp,
                accentColor = NeonYellow,
                showColorZones = true,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Linear gauges card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            accentColor = NeonCyan,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "SENSORS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                )
                LinearGauge(
                    value = data.fuelLevelPercent,
                    label = "Fuel Level",
                    unit = "%",
                    accentColor = NeonGreen,
                )
                LinearGauge(
                    value = data.intakeAirTempC?.let { UnitConverter.temperature(it, prefs.temperatureUnit) },
                    minValue = UnitConverter.tempMin(prefs.temperatureUnit),
                    maxValue = UnitConverter.temperature(80, prefs.temperatureUnit),
                    label = "Intake Air Temp",
                    unit = prefs.temperatureUnit.symbol,
                    accentColor = NeonCyan,
                )
                LinearGauge(
                    value = data.timingAdvanceDeg,
                    minValue = -64f,
                    maxValue = 64f,
                    label = "Timing Advance",
                    unit = "°",
                    accentColor = NeonPurple,
                )
                LinearGauge(
                    value = data.mafGramsPerSec?.let { UnitConverter.airMass(it, prefs.airMassUnit) },
                    minValue = 0f,
                    maxValue = if (prefs.airMassUnit == com.obdiitools.data.AirMassUnit.G_S) 655.35f else 86.67f,
                    label = "MAF Rate",
                    unit = prefs.airMassUnit.symbol,
                    accentColor = NeonOrange,
                )
                val maf = data.mafGramsPerSec
                val spd = data.speedKph
                if (maf != null) {
                    val isIdling = spd == null || spd < 5
                    val econValue: Float?
                    val econMax: Float
                    val econLabel: String
                    val econUnit: String
                    if (isIdling) {
                        econValue = UnitConverter.fuelFlowLph(maf)
                        econMax   = 20f
                        econLabel = "Fuel Flow (idle)"
                        econUnit  = "L/h"
                    } else {
                        econLabel = "Instant Economy"
                        when (prefs.fuelEconomyUnit) {
                            FuelEconomyUnit.L100KM -> {
                                econValue = UnitConverter.fuelEconomyL100km(maf, spd!!)
                                econMax   = 30f
                                econUnit  = "L/100km"
                            }
                            FuelEconomyUnit.MPG_US -> {
                                econValue = UnitConverter.fuelEconomyL100km(maf, spd!!)
                                    ?.let { UnitConverter.l100kmToMpgUs(it) }
                                econMax   = 60f
                                econUnit  = "mpg"
                            }
                        }
                    }
                    LinearGauge(
                        value     = econValue,
                        minValue  = 0f,
                        maxValue  = econMax,
                        label     = econLabel,
                        unit      = econUnit,
                        accentColor = NeonGreen,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats row card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                StatItem(
                    label = "OIL TEMP",
                    value = data.oilTempC?.let { UnitConverter.formatTemp(it, prefs.temperatureUnit) + prefs.temperatureUnit.symbol } ?: "--",
                    color = NeonOrange,
                )
                StatItem(
                    label = "BATTERY",
                    value = data.batteryVoltage?.let { "${"%.1f".format(it)}V" } ?: "--",
                    color = NeonGreen,
                )
                StatItem(
                    label = "AMBIENT",
                    value = data.ambientTempC?.let { UnitConverter.formatTemp(it, prefs.temperatureUnit) + prefs.temperatureUnit.symbol } ?: "--",
                    color = NeonCyan,
                )
                StatItem(
                    label = "BARO",
                    value = data.baroPressureKpa?.let { UnitConverter.formatPressure(it, prefs.pressureUnit) + " " + prefs.pressureUnit.symbol } ?: "--",
                    color = NeonPurple,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // View All Parameters link
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NeonCyan.copy(alpha = 0.08f))
                .clickable(onClick = onNavigateToLiveData)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "ALL PARAMETERS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = NeonCyan,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "Live monitor — every readable PID",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color,
        )
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = TextSecondary,
            letterSpacing = 1.sp,
        )
    }
}
