package com.obdiitools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.obdiitools.data.SpeedUnit
import com.obdiitools.data.TemperatureUnit
import com.obdiitools.data.UserPreferences
import com.obdiitools.util.UnitConverter
import com.obdiitools.obd.CVTData
import com.obdiitools.obd.CVTDataSource
import com.obdiitools.obd.CVTReading
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.LockupState
import com.obdiitools.ui.components.GlassCard
import com.obdiitools.ui.components.ShifterPositionCard
import com.obdiitools.ui.components.DTCCard
import com.obdiitools.ui.theme.BackgroundCard
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
import com.obdiitools.viewmodel.CVTViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CVTScreen(
    onNavigateBack: () -> Unit,
    viewModel: CVTViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val cvtData         by viewModel.cvtData.collectAsState()
    val prefs           by viewModel.userPreferences.collectAsState()
    val sourceDesc      by viewModel.sourceDescription.collectAsState()
    val rawCapture      by viewModel.rawCapture.collectAsState()
    val livePollDiagnostics by viewModel.livePollDiagnostics.collectAsState()
    val isCapturing     by viewModel.isCapturing.collectAsState()
    val cvtDtcs          by viewModel.cvtDtcs.collectAsState()
    val isReadingCvtDtcs by viewModel.isReadingCvtDtcs.collectAsState()
    val cvtDtcStatus     by viewModel.cvtDtcStatus.collectAsState()
    val cvtDeterioration by viewModel.cvtDeterioration.collectAsState()
    val isReadingCvtDeterioration by viewModel.isReadingCvtDeterioration.collectAsState()
    val cvtDeteriorationStatus by viewModel.cvtDeteriorationStatus.collectAsState()
    val transmissionModel by viewModel.detectedTransmissionModel.collectAsState()
    val transmissionPartNumber by viewModel.transmissionPartNumber.collectAsState()
    val transmissionEcuAddress by viewModel.transmissionEcuAddress.collectAsState()
    var showCvtInfo by remember { mutableStateOf(false) }

    val isConnected     = connectionState is ConnectionState.Connected

    DisposableEffect(isConnected) {
        if (isConnected) viewModel.start()
        onDispose { viewModel.stop() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = BackgroundDeep,
        topBar = {
            CenterAlignedTopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CVT MONITOR",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            "Continuously Variable Transmission",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonCyan,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundCard,
                ),
            )
        },
    ) { innerPadding ->
        if (!isConnected) {
            CVTDisconnectedState(modifier = Modifier.padding(innerPadding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Keep discovery progress and TCM confirmation visible without
                // requiring the user to scroll past the metric cards.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showCvtInfo = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan.copy(alpha = 0.15f),
                        ),
                    ) {
                        Text(
                            "CVT INFO",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NeonCyan,
                        )
                    }
                }
                ConnectionStatusCard(sourceDesc)
                if (transmissionEcuAddress != null) LivePollDiagnosticsCard(livePollDiagnostics)
                if (sourceDesc.contains("controller confirmed", ignoreCase = true)) {
                    Button(
                        onClick = { viewModel.captureRawTransmissionData() },
                        enabled = !isCapturing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.85f)),
                    ) {
                        Text(
                            if (isCapturing) "CAPTURING TCM DATA…" else "CAPTURE RAW TCM DATA",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = BackgroundDeep,
                        )
                    }
                }
                if (rawCapture.isNotBlank()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "RAW TCM CAPTURE — SELECTABLE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = NeonCyan,
                            )
                            SelectionContainer {
                                Text(
                                    rawCapture,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    lineHeight = 15.sp,
                                )
                            }
                        }
                    }
                }
                if (cvtData.lockupState.value != null &&
                    cvtData.lockupState.value != LockupState.UNKNOWN &&
                    cvtData.lockupState.source != CVTDataSource.UNAVAILABLE
                ) {
                    LockupStateBanner(cvtData.lockupState)
                }
                ShifterPositionCard(
                    title = "2016 SENTRA • CVT SELECTOR",
                    positions = listOf("P", "R", "N", "D", "L"),
                    selectedPosition = cvtData.selectorPosition.value,
                    statusText = if (cvtData.selectorPosition.value == null)
                        "Factory gate shown; live TCM position unavailable"
                    else "Live position reported by the transmission controller",
                    detailText = cvtData.selectorPosition.source.badge,
                )
                MetricGrid(cvtData, prefs)

                if (sourceDesc.contains("controller confirmed", ignoreCase = true)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "TRANSMISSION FAULT CODES",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NeonCyan,
                            letterSpacing = 1.sp,
                        )
                        Button(
                            onClick = { viewModel.readCvtDTCs() },
                            enabled = !isReadingCvtDtcs && !isCapturing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan.copy(alpha = 0.15f),
                                disabledContainerColor = SurfaceElevated,
                            ),
                        ) {
                            Text(
                                if (isReadingCvtDtcs) "READING CVT CODES…" else "READ CVT CODES",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isReadingCvtDtcs) TextSecondary else NeonCyan,
                            )
                        }
                        Text(
                            cvtDtcStatus,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary,
                        )
                        cvtDtcs.forEach { dtc -> DTCCard(dtc = dtc) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "CVT FLUID DETERIORATION",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NeonCyan,
                            letterSpacing = 1.sp,
                        )
                        Button(
                            onClick = { viewModel.readCvtDeterioration() },
                            enabled = !isReadingCvtDeterioration && !isReadingCvtDtcs && !isCapturing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan.copy(alpha = 0.15f),
                                disabledContainerColor = SurfaceElevated,
                            ),
                        ) {
                            Text(
                                if (isReadingCvtDeterioration) "READING DETERIORATION…" else "READ DETERIORATION COUNTER",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isReadingCvtDeterioration) TextSecondary else NeonCyan,
                            )
                        }
                        Text(
                            cvtDeteriorationStatus,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary,
                        )
                        cvtDeterioration?.let { result ->
                            CvtInfoRow("CVTF DEGRADATION", result.rawCounter.toString())
                            CvtInfoRow("REPLACEMENT THRESHOLD", "210,000")
                            Text(
                                if (result.fluidReplacementRequired)
                                    "AT OR ABOVE NISSAN'S REFERENCE THRESHOLD — CVT FLUID REPLACEMENT INDICATED."
                                else
                                    "BELOW NISSAN'S REFERENCE THRESHOLD — FLUID REPLACEMENT NOT INDICATED BY THIS COUNTER.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCvtInfo) {
        AlertDialog(
            onDismissRequest = { showCvtInfo = false },
            title = {
                Text(
                    "CVT INFORMATION",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CvtInfoRow("IDENTIFICATION", if (transmissionEcuAddress != null) "CONFIRMED" else "NOT CONFIRMED")
                    CvtInfoRow("TRANSMISSION", transmissionModel)
                    CvtInfoRow("TCM PART NUMBER", transmissionPartNumber ?: "Not available")
                    CvtInfoRow("ECU ADDRESS", transmissionEcuAddress ?: "Not detected")
                    Text(
                        if (transmissionEcuAddress != null)
                            "Identification is based on the TCM identification response."
                        else
                            "The monitor has not yet confirmed a supported transmission controller. No model or part number is assumed.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCvtInfo = false }) {
                    Text("CLOSE", color = NeonCyan, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = BackgroundCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }
}

@Composable
private fun LivePollDiagnosticsCard(details: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "LIVE POLL DIAGNOSTICS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = NeonCyan,
            )
            SelectionContainer {
                Text(
                    details,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun CvtInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = TextSecondary,
            letterSpacing = 1.sp,
        )
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = TextPrimary,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Lockup state banner
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun LockupStateBanner(reading: CVTReading<LockupState>) {
    val state = reading.value ?: LockupState.UNKNOWN
    val accent = state.accentColor()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "LOCKUP STATE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                )
                SourceBadge(reading.source)
            }
            Text(
                text = state.label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = accent,
                letterSpacing = 1.sp,
            )
            Text(
                text = state.description,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = accent.copy(alpha = 0.7f),
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// 2-column metric grid
// ──────────────────────────────────────────────────────────────────────────────

private data class MetricDisplay(
    val label: String,
    val value: String,
    val source: CVTDataSource,
    val accent: Color,
)

@Composable
private fun MetricGrid(data: CVTData, prefs: UserPreferences) {
    val metrics = buildList {
        data.engineRpm.value?.let {
            add(MetricDisplay("ENGINE RPM", "$it RPM", data.engineRpm.source, NeonCyan))
        }
        data.speedKph.value?.let {
            add(MetricDisplay("VEHICLE SPEED", "${UnitConverter.formatSpeed(it, prefs.speedUnit)} ${prefs.speedUnit.symbol}", data.speedKph.source, NeonOrange))
        }
        data.inputShaftRpm.value?.let {
            add(MetricDisplay("INPUT SHAFT", "$it RPM", data.inputShaftRpm.source, NeonPurple))
        }
        data.slipRpm.value?.let {
            val signed = if (it >= 0) "+$it" else "$it"
            val endpointCandidate = it == 127 || it == -128
            add(
                MetricDisplay(
                    if (endpointCandidate) "SLIP RPM • BYTE LIMIT" else "SLIP RPM",
                    "$signed RPM",
                    data.slipRpm.source,
                    if (endpointCandidate) NeonOrange else NeonGreen,
                )
            )
        }
        data.cvtRatio.value?.let {
            add(MetricDisplay("GEAR RATIO", "%.2f:1".format(java.util.Locale.US, it), data.cvtRatio.source, NeonYellow))
        }
        data.accelerator.value?.let {
            add(MetricDisplay("ACCELERATOR", "%.2f".format(it), data.accelerator.source, NeonCyan))
        }
        data.torqueConverterRatio.value?.let {
            add(MetricDisplay("TORQUE RATIO", "%.2f:1".format(java.util.Locale.US, it), data.torqueConverterRatio.source, NeonYellow))
        }
        data.virtualGear.value?.let {
            add(MetricDisplay("VIRTUAL GEAR", "$it", data.virtualGear.source, NeonPurple))
        }
        data.dGearRatio.value?.let {
            add(MetricDisplay("D GEAR RATIO", "%.2f:1".format(java.util.Locale.US, it), data.dGearRatio.source, NeonYellow))
        }
        data.gForce.value?.let {
            add(MetricDisplay("G-FORCE", "%.2f g".format(it), data.gForce.source, NeonCyan))
        }
        data.engineTorqueNm.value?.let {
            add(MetricDisplay("ENGINE TORQUE", "%.1f N·m".format(it), data.engineTorqueNm.source, NeonOrange))
        }
        data.enginePowerHp.value?.let {
            add(MetricDisplay("ENGINE POWER", "$it HP", data.enginePowerHp.source, NeonGreen))
        }
        data.outputTorqueNm.value?.let {
            add(MetricDisplay("OUTPUT TORQUE", "$it N·m", data.outputTorqueNm.source, NeonPurple))
        }
        data.selectorStatusFlag.value?.let {
            add(MetricDisplay("SELECTOR STATUS", if (it) "ON" else "OFF", data.selectorStatusFlag.source, NeonCyan))
        }
        data.selectorPosition.value?.let {
            add(MetricDisplay("SELECTOR", it, data.selectorPosition.source, NeonCyan))
        }
        data.cvtTemperatureCount.value?.let {
            add(MetricDisplay("CVT TEMP COUNT", "$it", data.cvtTemperatureCount.source, NeonRed))
        }
        data.batteryVoltage.value?.let {
            add(MetricDisplay("TCM BATTERY", "%.2f V".format(it), data.batteryVoltage.source, NeonGreen))
        }
        data.lockupCurrentAmps.value?.let {
            add(MetricDisplay("LOCKUP CURRENT", "%.3f A".format(it), data.lockupCurrentAmps.source, NeonPurple))
        }
        data.luPressureMpa.value?.let {
            add(MetricDisplay("LU PRESSURE", "%.2f MPa".format(it), data.luPressureMpa.source, NeonOrange))
        }
        data.cvtTempC.value?.let {
            val value = when (prefs.temperatureUnit) {
                TemperatureUnit.CELSIUS -> "$it °C"
                TemperatureUnit.FAHRENHEIT -> "${"%.0f".format(it * 9f / 5f + 32f)} °F"
            }
            add(MetricDisplay("CVT TEMP", value, data.cvtTempC.source, NeonRed))
        }
        data.primaryPressureMpa.value?.let {
            add(MetricDisplay("PRIMARY PRESSURE", "${"%.2f".format(it)} MPa", data.primaryPressureMpa.source, NeonPurple))
        }
        data.secondaryPressureMpa.value?.let {
            add(MetricDisplay("SECONDARY PRESSURE", "${"%.2f".format(it)} MPa", data.secondaryPressureMpa.source, NeonCyan))
        }
        data.targetSecondaryPressureMpa.value?.let {
            add(MetricDisplay("TARGET SECONDARY", "${"%.2f".format(it)} MPa", data.targetSecondaryPressureMpa.source, NeonYellow))
        }
        data.linePressureMpa.value?.let {
            add(MetricDisplay("LINE PRESSURE", "${"%.2f".format(it)} MPa", data.linePressureMpa.source, NeonOrange))
        }
    }

    if (metrics.isEmpty()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "No supported live CVT measurements are currently available.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSecondary,
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { metric ->
                    MetricCard(
                        metric.label,
                        metric.value,
                        metric.source,
                        metric.accent,
                        Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricRow(
    leftLabel: String,   leftValue: String?,   leftSource: CVTDataSource,   leftAccent: Color,
    rightLabel: String,  rightValue: String?,  rightSource: CVTDataSource,  rightAccent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricCard(leftLabel, leftValue, leftSource, leftAccent, Modifier.weight(1f))
        MetricCard(rightLabel, rightValue, rightSource, rightAccent, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String?,
    source: CVTDataSource,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = TextSecondary,
                letterSpacing = 1.sp,
            )
            Text(
                text = value ?: "—",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (value != null) accent else TextSecondary.copy(alpha = 0.4f),
            )
            SourceBadge(source)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Source badge chip
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SourceBadge(source: CVTDataSource) {
    val (bg, fg) = when (source) {
        CVTDataSource.SENSOR      -> NeonGreen.copy(alpha = 0.15f)  to NeonGreen
        CVTDataSource.INFERRED    -> NeonOrange.copy(alpha = 0.15f) to NeonOrange
        CVTDataSource.UNAVAILABLE -> SurfaceBorder.copy(alpha = 0.5f) to TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            source.badge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp,
            color = fg,
            letterSpacing = 0.5.sp,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Data source info card
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectionStatusCard(description: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "TCM CONNECTION STATUS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
            // Make diagnostic traces selectable so the complete response
            // details can be copied and pasted into a support conversation.
            SelectionContainer {
                Text(
                    description,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceBadge(CVTDataSource.SENSOR)
                Text("Direct OBD sensor", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceBadge(CVTDataSource.INFERRED)
                Text("Calculated from available data", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Disconnected state
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun CVTDisconnectedState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                "to monitor CVT transmission",
                color = TextSecondary.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

private fun LockupState.accentColor(): Color = when (this) {
    LockupState.LOCKED   -> NeonGreen
    LockupState.SLIPPING -> NeonOrange
    LockupState.OPEN     -> NeonRed
    LockupState.UNKNOWN  -> TextSecondary
}
