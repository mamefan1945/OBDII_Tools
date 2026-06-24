package com.obdiitools.ui.screens

import android.content.Intent
import android.net.Uri
import com.obdiitools.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.data.AirMassUnit
import com.obdiitools.data.AlertThresholds
import com.obdiitools.data.FuelEconomyUnit
import com.obdiitools.data.FuelPriceMode
import com.obdiitools.data.PressureUnit
import com.obdiitools.data.SpeedUnit
import com.obdiitools.data.TemperatureUnit
import com.obdiitools.data.TorqueUnit
import com.obdiitools.util.UnitConverter
import com.obdiitools.ui.components.GlassCard
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonPurple
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel, onNavigateToDiagnostics: () -> Unit = {}) {
    val prefs by viewModel.userPreferences.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Column {
                Text(
                    text = "SETTINGS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NeonCyan,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = "Display Units",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = TextPrimary,
                )
            }

            HorizontalDivider(color = SurfaceBorder)

            // Speed
            SettingsSection(label = "SPEED", accentColor = NeonCyan) {
                UnitSelector(
                    options = SpeedUnit.entries,
                    selected = prefs.speedUnit,
                    label = { it.symbol },
                    onSelect = { viewModel.setSpeedUnit(it) },
                    accentColor = NeonCyan,
                )
                Spacer(Modifier.height(4.dp))
                Text(prefs.speedUnit.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            }

            // Temperature
            SettingsSection(label = "TEMPERATURE", accentColor = NeonOrange) {
                UnitSelector(
                    options = TemperatureUnit.entries,
                    selected = prefs.temperatureUnit,
                    label = { it.symbol },
                    onSelect = { viewModel.setTemperatureUnit(it) },
                    accentColor = NeonOrange,
                )
                Spacer(Modifier.height(4.dp))
                Text(prefs.temperatureUnit.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            }

            // Pressure
            SettingsSection(label = "PRESSURE", accentColor = NeonPurple) {
                UnitSelector(
                    options = PressureUnit.entries,
                    selected = prefs.pressureUnit,
                    label = { it.symbol },
                    onSelect = { viewModel.setPressureUnit(it) },
                    accentColor = NeonPurple,
                )
                Spacer(Modifier.height(4.dp))
                Text(prefs.pressureUnit.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            }

            // Torque
            SettingsSection(label = "TORQUE", accentColor = NeonRed) {
                UnitSelector(
                    options = TorqueUnit.entries,
                    selected = prefs.torqueUnit,
                    label = { it.symbol },
                    onSelect = { viewModel.setTorqueUnit(it) },
                    accentColor = NeonRed,
                )
                Spacer(Modifier.height(4.dp))
                Text(prefs.torqueUnit.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            }

            // Air Mass
            SettingsSection(label = "AIR MASS", accentColor = NeonYellow) {
                UnitSelector(
                    options = AirMassUnit.entries,
                    selected = prefs.airMassUnit,
                    label = { it.symbol },
                    onSelect = { viewModel.setAirMassUnit(it) },
                    accentColor = NeonYellow,
                )
                Spacer(Modifier.height(4.dp))
                Text(prefs.airMassUnit.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            }

            // Fuel Economy
            SettingsSection(label = "FUEL ECONOMY", accentColor = NeonGreen) {
                UnitSelector(
                    options = FuelEconomyUnit.entries,
                    selected = prefs.fuelEconomyUnit,
                    label = { it.symbol },
                    onSelect = { viewModel.setFuelEconomyUnit(it) },
                    accentColor = NeonGreen,
                )
                Spacer(Modifier.height(4.dp))
                Text(prefs.fuelEconomyUnit.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            }

            // Fuel Price
            SettingsSection(label = "FUEL PRICE", accentColor = NeonGreen) {
                UnitSelector(
                    options = FuelPriceMode.entries,
                    selected = prefs.fuelPriceMode,
                    label = { it.label },
                    onSelect = { viewModel.setFuelPriceMode(it) },
                    accentColor = NeonGreen,
                )
                if (prefs.fuelPriceMode == FuelPriceMode.MANUAL) {
                    var priceText by remember(prefs.manualFuelPrice) {
                        mutableStateOf("%.2f".format(prefs.manualFuelPrice))
                    }
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { text ->
                            priceText = text
                            text.toFloatOrNull()?.let { viewModel.setManualFuelPrice(it) }
                        },
                        label = { Text("Price ${UnitConverter.fuelPriceUnitLabel(prefs)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = NeonGreen,
                            focusedLabelColor    = NeonGreen,
                            unfocusedBorderColor = SurfaceBorder,
                            unfocusedLabelColor  = TextSecondary,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary,
                            cursorColor          = NeonGreen,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val context = LocalContext.current
                    val eiaRefreshing by viewModel.eiaRefreshing.collectAsState()
                    var keyText by remember(prefs.eiaApiKey) { mutableStateOf(prefs.eiaApiKey) }
                    val keyLooksValid = keyText.length >= 16 && keyText.all { it.isLetterOrDigit() || it == '-' }

                    // Auto-fetch when this section first appears (screen open with EIA selected)
                    LaunchedEffect(Unit) {
                        if (prefs.eiaApiKey.isNotBlank()) viewModel.refreshEiaPrice(prefs.eiaApiKey)
                    }

                    Text(
                        "Get a free API key from the EIA, then paste it below.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan.copy(alpha = 0.08f))
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.eia.gov/opendata/register.php"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            "Step 1 — Register at eia.gov/opendata (free)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NeonCyan,
                        )
                        Text("→", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = NeonCyan)
                    }
                    Text(
                        "Step 2 — Check your email for the API key.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                    var keyFieldFocused by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it; viewModel.setEiaApiKey(it.trim()) },
                        label = { Text("Step 3 — Paste API Key", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = if (keyLooksValid) NeonGreen else NeonCyan,
                            focusedLabelColor    = if (keyLooksValid) NeonGreen else NeonCyan,
                            unfocusedBorderColor = if (keyLooksValid) NeonGreen.copy(alpha = 0.6f) else SurfaceBorder,
                            unfocusedLabelColor  = TextSecondary,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary,
                            cursorColor          = NeonCyan,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (!state.isFocused && keyFieldFocused && keyLooksValid) {
                                    viewModel.refreshEiaPrice(keyText.trim())
                                }
                                keyFieldFocused = state.isFocused
                            },
                    )
                    val eiaPrice = UnitConverter.eiaDisplayPrice(prefs)
                    when {
                        eiaRefreshing ->
                            Text("Fetching current price...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan)
                        eiaPrice != null -> {
                            val updatedStr = if (prefs.eiaFuelPriceUpdatedMs > 0L)
                                " (as of ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(prefs.eiaFuelPriceUpdatedMs))})"
                            else ""
                            PreviewRow("Current price", "$eiaPrice$updatedStr", NeonGreen)
                        }
                        prefs.eiaApiKey.isNotBlank() ->
                            Text("Price will be fetched at the start of your next session.", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
                    }
                    Text(
                        "US national average (USD). Updates at the start of each session.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextSecondary.copy(alpha = 0.6f),
                    )
                }
            }

            // Display behaviour
            SettingsSection(label = "DISPLAY", accentColor = NeonCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Keep Screen On",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                        )
                        Text(
                            "Prevents sleep while app is in the foreground.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextSecondary,
                        )
                    }
                    Switch(
                        checked = prefs.keepScreenOn,
                        onCheckedChange = { viewModel.setKeepScreenOn(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.35f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = SurfaceBorder,
                        ),
                    )
                }
                if (prefs.keepScreenOn) {
                    Text(
                        "⚠ Screen will stay on while OBDII Tools is open. This increases battery drain.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = NeonOrange,
                    )
                }
            }

            // Alerts
            SettingsSection(label = "ALERTS", accentColor = NeonRed) {
                val thresholds = prefs.alertThresholds
                val tempUnit = prefs.temperatureUnit
                val coolantDisplay = UnitConverter.temperature(thresholds.coolantMaxC, tempUnit)
                AlertSlider(
                    label = "Max Coolant Temp",
                    value = coolantDisplay,
                    range = UnitConverter.temperature(80, tempUnit)..UnitConverter.temperature(130, tempUnit),
                    display = "${UnitConverter.formatTemp(thresholds.coolantMaxC, tempUnit)}${tempUnit.symbol}",
                    color = NeonRed,
                    onValueChange = { displayVal ->
                        val coolantC = when (tempUnit) {
                            TemperatureUnit.CELSIUS    -> displayVal.toInt()
                            TemperatureUnit.FAHRENHEIT -> ((displayVal - 32f) * 5f / 9f).toInt()
                        }
                        viewModel.setAlertThresholds(thresholds.copy(coolantMaxC = coolantC))
                    },
                )
                AlertSlider(
                    label = "Min Fuel Level",
                    value = thresholds.fuelMinPct.toFloat(),
                    range = 0f..30f,
                    display = "${thresholds.fuelMinPct}%",
                    color = NeonOrange,
                    onValueChange = { viewModel.setAlertThresholds(thresholds.copy(fuelMinPct = it.toInt())) },
                )
                AlertSlider(
                    label = "Min Battery Voltage",
                    value = thresholds.batteryMinV,
                    range = 10f..13f,
                    display = "${"%.1f".format(thresholds.batteryMinV)}V",
                    color = NeonYellow,
                    onValueChange = { viewModel.setAlertThresholds(thresholds.copy(batteryMinV = it)) },
                )
                AlertSlider(
                    label = "Max RPM",
                    value = thresholds.rpmMax.toFloat(),
                    range = 2000f..9000f,
                    display = "${thresholds.rpmMax} rpm",
                    color = NeonPurple,
                    onValueChange = { viewModel.setAlertThresholds(thresholds.copy(rpmMax = it.toInt())) },
                )
            }

            // Preview card
            GlassCard(modifier = Modifier.fillMaxWidth(), accentColor = NeonGreen) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PREVIEW",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        letterSpacing = 2.sp,
                    )
                    PreviewRow("Speed",       "100 km/h → ${if (prefs.speedUnit == SpeedUnit.MPH) "62 mph" else "100 km/h"}",                     NeonCyan)
                    PreviewRow("Temperature", "90°C → ${if (prefs.temperatureUnit == TemperatureUnit.FAHRENHEIT) "194°F" else "90°C"}",            NeonOrange)
                    PreviewRow("Pressure", when (prefs.pressureUnit) {
                        PressureUnit.KPA -> "100 kPa"
                        PressureUnit.PSI -> "100 kPa → 14.5 psi"
                        PressureUnit.BAR -> "100 kPa → 1.00 bar"
                    }, NeonPurple)
                    PreviewRow("Torque", when (prefs.torqueUnit) {
                        TorqueUnit.NM    -> "250 Nm"
                        TorqueUnit.LB_FT -> "250 Nm → 184 lb·ft"
                    }, NeonRed)
                    PreviewRow("Air Mass", when (prefs.airMassUnit) {
                        AirMassUnit.G_S    -> "10.00 g/s"
                        AirMassUnit.LB_MIN -> "10.00 g/s → 1.323 lb/min"
                    }, NeonYellow)
                    PreviewRow("Economy", when (prefs.fuelEconomyUnit) {
                        FuelEconomyUnit.L100KM -> "8.0 L/100km"
                        FuelEconomyUnit.MPG_US -> "8.0 L/100km → 29.4 mpg"
                    }, NeonGreen)
                }
            }

            // Diagnostics link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeonOrange.copy(alpha = 0.07f))
                    .clickable(onClick = onNavigateToDiagnostics)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "CONNECTION LOG",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NeonOrange,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = "BLE diagnostics & event trace",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                }
                Text("→", fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = NeonOrange)
            }

            // About card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                val buildDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    .format(Date(BuildConfig.BUILD_TIME))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ABOUT",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        letterSpacing = 2.sp,
                    )
                    Text("OBDII Tools", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Text("ELM327 Bluetooth OBD2 Scanner", fontFamily = FontFamily.Default, fontSize = 13.sp, color = TextSecondary)
                    PreviewRow("Version", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", NeonCyan)
                    PreviewRow("Build", buildDate, TextSecondary)
                    PreviewRow("Commit", BuildConfig.GIT_HASH, TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), accentColor = accentColor) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = accentColor,
                letterSpacing = 2.sp,
            )
            content()
        }
    }
}

@Composable
private fun <T> UnitSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor   = accentColor.copy(alpha = 0.15f),
                    activeContentColor     = accentColor,
                    activeBorderColor      = accentColor,
                    inactiveContainerColor = BackgroundDeep,
                    inactiveContentColor   = TextSecondary,
                    inactiveBorderColor    = SurfaceBorder,
                ),
            ) {
                Text(
                    text = label(option),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun AlertSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    color: androidx.compose.ui.graphics.Color,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            Text(display, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
        )
    }
}

@Composable
private fun PreviewRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
