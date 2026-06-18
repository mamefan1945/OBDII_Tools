package com.obdiitools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obdiitools.data.SpeedUnit
import com.obdiitools.data.TemperatureUnit
import com.obdiitools.data.UserPreferences
import com.obdiitools.obd.CVTData
import com.obdiitools.obd.CVTDataSource
import com.obdiitools.obd.CVTReading
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.LockupState
import com.obdiitools.ui.components.GlassCard
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
    val isConnected     = connectionState is ConnectionState.Connected

    DisposableEffect(isConnected) {
        if (isConnected) viewModel.start()
        onDispose { viewModel.stop() }
    }

    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            CenterAlignedTopAppBar(
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
                LockupStateBanner(cvtData.lockupState)
                MetricGrid(cvtData, prefs)
                DataSourceCard(sourceDesc)
            }
        }
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

@Composable
private fun MetricGrid(data: CVTData, prefs: UserPreferences) {
    val speedLabel = if (prefs.speedUnit == SpeedUnit.MPH) "VEHICLE SPEED" else "VEHICLE SPEED"
    val speedValue = data.speedKph.value?.let { kph ->
        when (prefs.speedUnit) {
            SpeedUnit.KMH -> "$kph km/h"
            SpeedUnit.MPH -> "${"%.0f".format(kph * 0.621371f)} mph"
        }
    }
    val tempValue = data.cvtTempC.value?.let { c ->
        when (prefs.temperatureUnit) {
            TemperatureUnit.CELSIUS    -> "$c °C"
            TemperatureUnit.FAHRENHEIT -> "${"%.0f".format(c * 9f / 5f + 32f)} °F"
        }
    }
    val slipSign = data.slipRpm.value?.let { if (it >= 0) "+$it" else "$it" }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricRow(
            leftLabel  = "ENGINE RPM",
            leftValue  = data.engineRpm.value?.let { "$it RPM" },
            leftSource = data.engineRpm.source,
            leftAccent = NeonCyan,
            rightLabel  = speedLabel,
            rightValue  = speedValue,
            rightSource = data.speedKph.source,
            rightAccent = NeonOrange,
        )
        MetricRow(
            leftLabel  = "INPUT SHAFT",
            leftValue  = data.inputShaftRpm.value?.let { "$it RPM" },
            leftSource = data.inputShaftRpm.source,
            leftAccent = NeonPurple,
            rightLabel  = "SLIP RPM",
            rightValue  = slipSign?.let { "$it RPM" },
            rightSource = data.slipRpm.source,
            rightAccent = if ((data.slipRpm.value ?: 0) > 100) NeonRed else NeonGreen,
        )
        MetricRow(
            leftLabel  = "DRIVE RATIO",
            leftValue  = data.cvtRatio.value?.let { "${"%.1f".format(it)} RPM/km·h" },
            leftSource = data.cvtRatio.source,
            leftAccent = NeonYellow,
            rightLabel  = "CVT TEMP",
            rightValue  = tempValue,
            rightSource = data.cvtTempC.source,
            rightAccent = NeonRed,
        )
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
private fun DataSourceCard(description: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "DATA SOURCES",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
            Text(
                description,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
            )
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
