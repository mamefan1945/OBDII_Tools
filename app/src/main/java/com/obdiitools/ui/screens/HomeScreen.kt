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
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.data.AlertType
import com.obdiitools.data.TripSummary
import com.obdiitools.data.UserPreferences
import com.obdiitools.data.VinDiagnostic
import com.obdiitools.data.VinInfo
import com.obdiitools.obd.ConnectionState
import com.obdiitools.util.UnitConverter
import com.obdiitools.obd.OBDData
import com.obdiitools.ui.components.ConnectionStatusBar
import com.obdiitools.ui.components.GlassCard
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonPurple
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.SurfaceElevated
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToCanMonitor: () -> Unit = {},
    onNavigateToCvtMonitor: () -> Unit = {},
    onNavigateToUdsReader: () -> Unit = {},
    onNavigateToReadiness: () -> Unit = {},
    onNavigateToFreezeFrame: () -> Unit = {},
    onNavigateToSessions: () -> Unit = {},
    onNavigateToCustomPids: () -> Unit = {},
    onNavigateToDeepScan: () -> Unit = {},
    onNavigateToGlossary: () -> Unit = {},
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val data by viewModel.obdData.collectAsState()
    val dtcList by viewModel.dtcList.collectAsState()
    val lowVoltageWarning by viewModel.lowVoltageWarning.collectAsState()
    val activeAlerts by viewModel.activeAlerts.collectAsState()
    val vinInfo by viewModel.vinInfo.collectAsState()
    val vinDiag by viewModel.vinDiagnostic.collectAsState()
    val vinDiagRunning by viewModel.vinDiagRunning.collectAsState()
    val tripSummary by viewModel.lastTripSummary.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to BackgroundDeep,
                    0.4f to BackgroundDeep,
                    1f to SurfaceElevated.copy(alpha = 0.3f),
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App title
            Column {
                Text(
                    text = "OBDII",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NeonCyan,
                    letterSpacing = 4.sp,
                )
                Text(
                    text = "Tools",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = TextPrimary,
                )
            }

            ConnectionStatusBar(state = connectionState)

            // System alerts (low-voltage + threshold alerts)
            if (lowVoltageWarning) {
                AlertBanner(
                    title = "LOW BATTERY",
                    body = "Vehicle battery below 12.2V — possible charging issue",
                )
            }
            activeAlerts.forEach { alertType ->
                AlertBanner(
                    title = alertType.label.uppercase(),
                    body = when (alertType) {
                        AlertType.HIGH_COOLANT -> "Coolant temperature exceeds threshold — check cooling system"
                        AlertType.LOW_FUEL     -> "Fuel level below threshold — refuel soon"
                        AlertType.HIGH_RPM     -> "Engine RPM exceeds configured limit"
                        AlertType.LOW_BATTERY  -> "Battery below configured threshold"
                    },
                )
            }

            // Trip summary card (shown after disconnect while session data is fresh)
            tripSummary?.let { trip ->
                TripSummaryCard(trip = trip, onDismiss = { viewModel.dismissTripSummary() })
            }

            // VIN card
            if (vinInfo != null || isConnected) {
                VinCard(
                    vinInfo = vinInfo,
                    vinDiag = vinDiag,
                    vinDiagRunning = vinDiagRunning,
                    onFetch = { viewModel.fetchVin() },
                    onDiagnose = { viewModel.runVinDiagnostic() },
                    isConnected = isConnected,
                )
            }

            // Vehicle status summary card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = when (connectionState) {
                    is ConnectionState.Connected    -> NeonGreen
                    is ConnectionState.Connecting   -> NeonOrange
                    is ConnectionState.Disconnected -> NeonCyan
                    is ConnectionState.Error        -> NeonRed
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "VEHICLE STATUS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            letterSpacing = 2.sp,
                        )
                        Icon(
                            Icons.Default.DirectionsCar,
                            null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (connectionState is ConnectionState.Connected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            QuickStatItem(
                                icon = Icons.Default.Speed,
                                value = data.rpm?.let { "${it} RPM" } ?: "--",
                                label = "RPM",
                                color = NeonCyan,
                            )
                            QuickStatItem(
                                icon = Icons.Default.Speed,
                                value = data.speedKph?.let { "${UnitConverter.formatSpeed(it, prefs.speedUnit)} ${prefs.speedUnit.symbol}" } ?: "--",
                                label = "SPEED",
                                color = NeonOrange,
                            )
                            QuickStatItem(
                                icon = Icons.Default.Thermostat,
                                value = data.coolantTempC?.let { "${UnitConverter.formatTemp(it, prefs.temperatureUnit)}${prefs.temperatureUnit.symbol}" } ?: "--",
                                label = "TEMP",
                                color = NeonRed,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            QuickStatItem(
                                icon = Icons.Default.LocalGasStation,
                                value = data.fuelLevelPercent?.let { "${it.toInt()}%" } ?: "--",
                                label = "FUEL",
                                color = NeonGreen,
                            )
                            QuickStatItem(
                                icon = Icons.Default.BatteryFull,
                                value = data.batteryVoltage?.let { "${"%.1f".format(it)}V" } ?: "--",
                                label = "BATTERY",
                                color = NeonYellow,
                            )
                            QuickStatItem(
                                icon = Icons.Default.Warning,
                                value = "${dtcList.size} DTCs",
                                label = "FAULTS",
                                color = if (dtcList.isEmpty()) NeonGreen else NeonRed,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when (connectionState) {
                                    is ConnectionState.Connecting -> "Establishing connection…"
                                    is ConnectionState.Error -> (connectionState as ConnectionState.Error).message
                                    else -> "Connect via Bluetooth tab to see live data"
                                },
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // DTC summary card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = if (dtcList.isNotEmpty()) NeonRed else NeonGreen,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "FAULT CODES",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            letterSpacing = 2.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (dtcList.isEmpty()) "No faults" else "${dtcList.size} fault${if (dtcList.size != 1) "s" else ""} detected",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (dtcList.isEmpty()) NeonGreen else NeonRed,
                        )
                        if (dtcList.isNotEmpty()) {
                            val pending = dtcList.count { it.isPending }
                            Text(
                                text = "${dtcList.size - pending} stored · $pending pending",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint = if (dtcList.isEmpty()) NeonGreen else NeonRed,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            // Advanced tools section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ADVANCED TOOLS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                )
                AdvancedToolCard(
                    title = "READINESS",
                    subtitle = "OBD monitor completion status",
                    color = NeonGreen,
                    onClick = onNavigateToReadiness,
                )
                AdvancedToolCard(
                    title = "FREEZE FRAME",
                    subtitle = "Sensor snapshot at fault time",
                    color = NeonOrange,
                    onClick = onNavigateToFreezeFrame,
                )
                AdvancedToolCard(
                    title = "SESSIONS",
                    subtitle = "Drive history & sensor graphs",
                    color = NeonCyan,
                    onClick = onNavigateToSessions,
                )
                AdvancedToolCard(
                    title = "CUSTOM PIDs",
                    subtitle = "User-defined OBD parameters",
                    color = NeonYellow,
                    onClick = onNavigateToCustomPids,
                )
                AdvancedToolCard(
                    title = "CVT MONITOR",
                    subtitle = "Lockup state, slip RPM, drive ratio, CVT temp",
                    color = NeonPurple,
                    onClick = onNavigateToCvtMonitor,
                )
                AdvancedToolCard(
                    title = "CAN MONITOR",
                    subtitle = "Live ATMA raw frame sniffer",
                    color = NeonOrange,
                    onClick = onNavigateToCanMonitor,
                )
                AdvancedToolCard(
                    title = "UDS READER",
                    subtitle = "Mode 22 ReadDataByIdentifier",
                    color = NeonPurple,
                    onClick = onNavigateToUdsReader,
                )
                AdvancedToolCard(
                    title = "DEEP SCAN",
                    subtitle = "Discover all ECUs and supported PIDs",
                    color = NeonCyan,
                    onClick = onNavigateToDeepScan,
                )
                AdvancedToolCard(
                    title = "GLOSSARY",
                    subtitle = "OBD2 & ELM327 terminology guide",
                    color = NeonGreen,
                    onClick = onNavigateToGlossary,
                )
            }

            // Info card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "HOW TO USE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        letterSpacing = 2.sp,
                    )
                    InfoStep("1", "Pair your ELM327 adapter via device Bluetooth settings")
                    InfoStep("2", "Open the Bluetooth tab and tap your device to connect")
                    InfoStep("3", "View live sensor data on the Dashboard tab")
                    InfoStep("4", "Scan and reset fault codes on the Faults tab")
                }
            }
        }
    }
}

@Composable
private fun TripSummaryCard(trip: TripSummary, onDismiss: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), accentColor = NeonGreen) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("LAST TRIP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = NeonGreen, letterSpacing = 2.sp)
                    Text(trip.deviceName, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextSecondary)
                }
                TextButton(onClick = onDismiss) {
                    Text("DISMISS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NeonGreen, fontWeight = FontWeight.Bold)
                }
            }
            val totalSecs = trip.durationMs / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            val durationStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripStat("DURATION", durationStr, modifier = Modifier.weight(1f))
                TripStat("DISTANCE", trip.distanceKm?.let { "${"%.1f".format(it)} km" } ?: "--", modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripStat("MAX RPM", trip.maxRpm?.let { "$it" } ?: "--", modifier = Modifier.weight(1f))
                TripStat("AVG SPEED", trip.avgSpeedKph?.let { "${"%.0f".format(it)} kph" } ?: "--", modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripStat("MAX SPEED", trip.maxSpeedKph?.let { "${it} kph" } ?: "--", modifier = Modifier.weight(1f))
                TripStat("MAX COOLANT", trip.maxCoolantTempC?.let { "${it}°C" } ?: "--", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TripStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextSecondary, letterSpacing = 1.sp)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
    }
}

@Composable
private fun AlertBanner(title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NeonRed.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, tint = NeonRed, modifier = Modifier.size(18.dp))
        Column {
            Text(title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NeonRed, letterSpacing = 1.sp)
            Text(body, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NeonRed.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun VinCard(
    vinInfo: VinInfo?,
    vinDiag: VinDiagnostic?,
    vinDiagRunning: Boolean,
    onFetch: () -> Unit,
    onDiagnose: () -> Unit,
    isConnected: Boolean,
) {
    var showDiag by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth(), accentColor = NeonCyan) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("VEHICLE ID", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TextSecondary, letterSpacing = 2.sp)
                if (vinInfo != null) {
                    Text(vinInfo.displayName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Text(vinInfo.vin, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan)
                } else {
                    Text("VIN not decoded", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextSecondary)
                }
            }
            if (isConnected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vinInfo == null) {
                        TextButton(onClick = onFetch) {
                            Text("DECODE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = {
                        showDiag = true
                        onDiagnose()
                    }) {
                        if (vinDiagRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = NeonPurple)
                        } else {
                            Text("DIAGNOSE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showDiag) {
        VinDiagDialog(
            diag = vinDiag,
            running = vinDiagRunning,
            onDismiss = { showDiag = false },
        )
    }
}

@Composable
private fun VinDiagDialog(diag: VinDiagnostic?, running: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", fontFamily = FontFamily.Monospace, color = NeonCyan) }
        },
        title = {
            Text("VIN DIAGNOSTICS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NeonCyan, letterSpacing = 2.sp)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (running || diag == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = NeonCyan)
                            DiagLine("Querying vehicle...", TextSecondary)
                        }
                    }
                } else {
                    // ── Mode 09 ──────────────────────────────────────────
                    DiagHeader("MODE 09  •  OBD-II VIN (PID 0902)")
                    DiagLine("Lines received: ${diag.mode09RawLines.size}", TextSecondary)
                    diag.mode09RawLines.forEachIndexed { i, line ->
                        DiagLine("  [${i + 1}] $line", TextSecondary)
                    }
                    DiagLine(
                        if (diag.mode09FrameFormat) "Format: frame-numbered (N: prefix)" else "Format: plain (4902 prefix)",
                        if (diag.mode09FrameFormat) NeonOrange else TextSecondary,
                    )
                    if (diag.mode09FilteredHex.isNotBlank()) {
                        DiagLine("Hex after stripping prefix:", TextSecondary)
                        DiagLine("  ${diag.mode09FilteredHex}", NeonCyan)
                    }
                    if (diag.mode09ParsedVin != null) {
                        DiagLine("✓  VIN: ${diag.mode09ParsedVin}", NeonGreen)
                    } else {
                        val charCount = diag.mode09FilteredHex.chunked(2).count { hex ->
                            val b = hex.toIntOrNull(16) ?: 0; b in 0x20..0x7E
                        }
                        if (diag.mode09FilteredHex.isBlank()) {
                            DiagLine("✗  No usable data — vehicle may not support Mode 09 VIN", NeonRed)
                        } else {
                            DiagLine("✗  Only $charCount printable bytes (need 17)", NeonRed)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── UDS F190 ─────────────────────────────────────────
                    DiagHeader("UDS DID F190  •  ISO 14229 VIN FALLBACK")
                    if (!diag.udsAttempted) {
                        DiagLine("Not attempted", TextSecondary)
                    } else {
                        DiagLine("Raw: ${diag.udsRaw.ifBlank { "(empty)" }}", TextSecondary)
                        if (diag.udsParsedVin != null) {
                            DiagLine("✓  VIN: ${diag.udsParsedVin}", NeonGreen)
                        } else {
                            val isNeg = diag.udsRaw.replace(" ", "").uppercase().startsWith("7F")
                            DiagLine(if (isNeg) "✗  Negative response (ECU rejected request)" else "✗  No usable VIN data", NeonRed)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── NHTSA ─────────────────────────────────────────────
                    DiagHeader("NHTSA VPIC  •  MAKE / MODEL / YEAR DECODE")
                    if (!diag.nhtsaAttempted) {
                        DiagLine("Not attempted — no VIN was found above", TextSecondary)
                    } else {
                        DiagLine("VIN submitted: ${diag.vinSelected}", TextSecondary)
                        if (diag.nhtsaError != null) {
                            DiagLine("✗  Network error: ${diag.nhtsaError}", NeonRed)
                            DiagLine("   Check internet connectivity", TextSecondary)
                        } else if (diag.nhtsaHttpStatus != 200) {
                            DiagLine("✗  HTTP ${diag.nhtsaHttpStatus}", NeonRed)
                        } else {
                            DiagLine("HTTP 200 OK", NeonGreen)
                            DiagLine("Make:  ${diag.nhtsaMake ?: "(not found)"}", if (diag.nhtsaMake != null) NeonGreen else NeonOrange)
                            DiagLine("Model: ${diag.nhtsaModel ?: "(not found)"}", if (diag.nhtsaModel != null) NeonGreen else NeonOrange)
                            DiagLine("Year:  ${diag.nhtsaYear ?: "(not found)"}", if (diag.nhtsaYear != null) NeonGreen else NeonOrange)
                            if (diag.nhtsaMake == null && diag.nhtsaModel == null) {
                                DiagLine("  Non-US market vehicle — NHTSA only covers", NeonOrange)
                                DiagLine("  vehicles sold in the United States.", NeonOrange)
                            }
                        }
                    }
                }
            }
        },
        containerColor = SurfaceElevated,
    )
}

@Composable
private fun DiagHeader(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        color = NeonCyan,
        letterSpacing = 1.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonCyan.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun DiagLine(text: String, color: Color) {
    Text(text = text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = color)
}

@Composable
private fun AdvancedToolCard(
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = color,
                letterSpacing = 2.sp,
            )
            Text(
                subtitle,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSecondary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun QuickStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = TextPrimary,
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

@Composable
private fun InfoStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = number,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = NeonCyan,
        )
        Text(
            text = text,
            fontFamily = FontFamily.Default,
            fontSize = 13.sp,
            color = TextSecondary,
        )
    }
}
