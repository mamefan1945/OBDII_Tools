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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import com.obdiitools.obd.AllPidDefinitions
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.OBDData
import com.obdiitools.obd.MazdaCx5DashboardData
import com.obdiitools.ui.components.CircularGauge
import com.obdiitools.ui.components.ConnectionStatusBar
import com.obdiitools.ui.components.ConnectDevicePrompt
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
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToLiveData: () -> Unit = {},
    onNavigateToBluetooth: () -> Unit = {},
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val data by viewModel.obdData.collectAsState()
    val mazdaCx5Data by viewModel.mazdaCx5DashboardData.collectAsState()
    val livePidValues by viewModel.livePidValues.collectAsState()
    val confirmedPids by viewModel.confirmedPids.collectAsState()
    val previouslyKnownPids by viewModel.previouslyKnownPids.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    val sessionFuelLitres by viewModel.sessionFuelLitres.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        if (!isConnected) {
            ConnectDevicePrompt(onClick = onNavigateToBluetooth)
        } else {
            DashboardContent(
                data = data,
                mazdaCx5Data = mazdaCx5Data,
                livePidValues = livePidValues,
                confirmedPids = confirmedPids,
                previouslyKnownPids = previouslyKnownPids,
                connectionState = connectionState,
                prefs = prefs,
                sessionFuelLitres = sessionFuelLitres,
                onNavigateToLiveData = onNavigateToLiveData,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    data: OBDData,
    mazdaCx5Data: MazdaCx5DashboardData?,
    livePidValues: Map<String, Float>,
    confirmedPids: Set<String>,
    previouslyKnownPids: Set<String>,
    connectionState: ConnectionState,
    prefs: UserPreferences,
    sessionFuelLitres: Float,
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
        mazdaCx5Data?.let { MazdaTransmissionDashboardCard(it, data.ambientTempC) }
        HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(20.dp))

        if (confirmedPids.isEmpty()) {
            val message = if (previouslyKnownPids.isNotEmpty()) {
                "RECHECKING ${previouslyKnownPids.size} PREVIOUSLY LEARNED READINGS…"
            } else {
                "LEARNING VEHICLE CAPABILITIES — waiting for verified live readings…"
            }
            Text(
                text = message,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = NeonYellow,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        // Primary gauges — only show values confirmed by repeated live samples.
        if (confirmedPids.any { it in setOf("010C", "010D") }) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if ("010C" in confirmedPids) CircularGauge(
                value = data.rpm?.toFloat(),
                minValue = 0f,
                maxValue = 8000f,
                label = "ENGINE",
                unit = "RPM",
                size = 170.dp,
                accentColor = NeonCyan,
                showColorZones = true,
            )
            if ("010D" in confirmedPids) CircularGauge(
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

        // Secondary gauges — show only confirmed sensors.
        if (confirmedPids.any { it in setOf("0105", "0111", "0104") }) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if ("0105" in confirmedPids) CircularGauge(
                value = data.coolantTempC?.let { UnitConverter.temperature(it, prefs.temperatureUnit) },
                minValue = UnitConverter.tempMin(prefs.temperatureUnit),
                maxValue = UnitConverter.tempMax(prefs.temperatureUnit),
                label = "COOLANT",
                unit = prefs.temperatureUnit.symbol,
                size = 130.dp,
                accentColor = NeonRed,
                showColorZones = true,
            )
            if ("0111" in confirmedPids) CircularGauge(
                value = data.throttlePercent,
                minValue = 0f,
                maxValue = 100f,
                label = "THROTTLE",
                unit = "%",
                size = 130.dp,
                accentColor = NeonGreen,
            )
            if ("0104" in confirmedPids) CircularGauge(
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

        // Linear gauges card — omit sensors the vehicle has not verified.
        if (confirmedPids.any { it in setOf("012F", "010F", "010E", "0110") }) GlassCard(
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
                if ("012F" in confirmedPids) LinearGauge(
                    value = data.fuelLevelPercent,
                    label = "Fuel Level",
                    unit = "%",
                    accentColor = NeonGreen,
                )
                if ("010F" in confirmedPids) LinearGauge(
                    value = data.intakeAirTempC?.let { UnitConverter.temperature(it, prefs.temperatureUnit) },
                    minValue = UnitConverter.tempMin(prefs.temperatureUnit),
                    maxValue = UnitConverter.temperature(80, prefs.temperatureUnit),
                    label = "Intake Air Temp",
                    unit = prefs.temperatureUnit.symbol,
                    accentColor = NeonCyan,
                )
                if ("010E" in confirmedPids) LinearGauge(
                    value = data.timingAdvanceDeg,
                    minValue = -64f,
                    maxValue = 64f,
                    label = "Timing Advance",
                    unit = "°",
                    accentColor = NeonPurple,
                )
                if ("0110" in confirmedPids) LinearGauge(
                    value = data.mafGramsPerSec?.let { UnitConverter.airMass(it, prefs.airMassUnit) },
                    minValue = 0f,
                    maxValue = if (prefs.airMassUnit == com.obdiitools.data.AirMassUnit.G_S) 655.35f else 86.67f,
                    label = "MAF Rate",
                    unit = prefs.airMassUnit.symbol,
                    accentColor = NeonOrange,
                )
                val maf = data.mafGramsPerSec
                val spd = data.speedKph
                if ("0110" in confirmedPids && maf != null) {
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

        // Stats row card — battery is adapter-reported; other fields require PID confirmation.
        if (data.batteryVoltage != null || confirmedPids.any { it in setOf("015C", "0146", "0133") }) GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                if ("015C" in confirmedPids) StatItem(
                    label = "OIL TEMP",
                    value = data.oilTempC?.let { UnitConverter.formatTemp(it, prefs.temperatureUnit) + prefs.temperatureUnit.symbol } ?: "--",
                    color = NeonOrange,
                )
                StatItem(
                    label = "BATTERY",
                    value = data.batteryVoltage?.let { "${"%.1f".format(it)}V" } ?: "--",
                    color = NeonGreen,
                )
                if ("0146" in confirmedPids) StatItem(
                    label = "AMBIENT",
                    value = data.ambientTempC?.let { UnitConverter.formatTemp(it, prefs.temperatureUnit) + prefs.temperatureUnit.symbol } ?: "--",
                    color = NeonCyan,
                )
                if ("0133" in confirmedPids) StatItem(
                    label = "BARO",
                    value = data.baroPressureKpa?.let { UnitConverter.formatPressure(it, prefs.pressureUnit) + " " + prefs.pressureUnit.symbol } ?: "--",
                    color = NeonPurple,
                )
            }
        }

        // Additional verified Mode 01 readings are generated from the PID catalogue.
        val dashboardCorePids = setOf(
            "010C", "010D", "0105", "0111", "0104", "012F",
            "010F", "010E", "0110", "015C", "0146", "0133",
        )
        val additionalReadings = AllPidDefinitions.ALL.filter { definition ->
            definition.command in confirmedPids &&
                definition.command !in dashboardCorePids &&
                livePidValues.containsKey(definition.command)
        }
        if (additionalReadings.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                accentColor = NeonPurple,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "ADAPTIVE SENSORS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        letterSpacing = 2.sp,
                    )
                    additionalReadings.forEach { definition ->
                        LinearGauge(
                            value = livePidValues[definition.command],
                            minValue = definition.minValue,
                            maxValue = definition.maxValue,
                            label = definition.shortName,
                            unit = definition.unit,
                            accentColor = NeonPurple,
                        )
                    }
                }
            }
        }

        // Session fuel cost — only show when there's data and a price is configured
        val sessionCost = UnitConverter.fuelCost(sessionFuelLitres, prefs)
        if (sessionFuelLitres > 0f && sessionCost != null) {
            Spacer(Modifier.height(16.dp))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                accentColor = NeonGreen,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    StatItem(
                        label = "SESSION COST",
                        value = "$${"%.2f".format(sessionCost)}",
                        color = NeonGreen,
                    )
                    StatItem(
                        label = "FUEL USED",
                        value = UnitConverter.formatFuelVolume(sessionFuelLitres, prefs.fuelEconomyUnit),
                        color = NeonYellow,
                    )
                }
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
private fun MazdaTransmissionDashboardCard(data: MazdaCx5DashboardData, ambientTempC: Int?) {
    var clusterTemperatureF by rememberSaveable { mutableStateOf("") }
    val transmission = data.transmission
    val gearAgeMs = data.gearCapturedAtMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) }
    val temperatureAgeMs = data.temperatureCapturedAtMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) }
    val clusterF = clusterTemperatureF.toFloatOrNull()

    Spacer(Modifier.height(16.dp))
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        accentColor = NeonOrange,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MAZDA CX-5 TRANSMISSION", fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 10.sp, color = NeonOrange,
                letterSpacing = 1.5.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StatItem("SELECTED GEAR", transmission.gear ?: "--", NeonCyan)
                    Text(gearAgeMs?.let { "age \${"%.1f".format(it / 1000.0)}s" } ?: "no reading",
                        fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                        color = if (gearAgeMs == null || gearAgeMs > 4_000L) NeonYellow else TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StatItem("ATF TEMP", transmission.transmissionTemperatureC?.let { "%.1f °C".format(it) } ?: "--", NeonOrange)
                    Text(temperatureAgeMs?.let { "age \${"%.1f".format(it / 1000.0)}s" } ?: "no reading",
                        fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                        color = if (temperatureAgeMs == null || temperatureAgeMs > 8_000L) NeonYellow else TextSecondary)
                }
            }
            HorizontalDivider(color = SurfaceBorder)
            Text("AMBIENT TEMPERATURE COMPARISON", fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 10.sp, color = NeonCyan)
            Text("OBD-II PID 46: \${ambientTempC?.let { "%.0f °F".format(it * 9f / 5f + 32f) } ?: "--"}",
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
            OutlinedTextField(
                value = clusterTemperatureF,
                onValueChange = { value ->
                    if (value.length <= 6 && (value.isEmpty() || value.matches(Regex("-?\\d{0,3}(\\.\\d{0,1})?")))) {
                        clusterTemperatureF = value
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Instrument cluster reading (°F)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            val pid46F = ambientTempC?.let { it * 9f / 5f + 32f }
            val difference = if (pid46F != null && clusterF != null) pid46F - clusterF else null
            Text(
                difference?.let { "PID 46 minus cluster: \${if (it > 0) "+" else ""}\${"%.1f".format(it)} °F" }
                    ?: "Enter the cluster temperature to calculate the difference",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = if (difference != null && kotlin.math.abs(difference) >= 3f) NeonYellow else TextSecondary)
            Text("Read-only TCM data · individual signal ages shown above",
                fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = TextSecondary)
        }
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
