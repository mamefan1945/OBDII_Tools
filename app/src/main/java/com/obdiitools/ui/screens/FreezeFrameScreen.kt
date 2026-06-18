package com.obdiitools.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.obdiitools.data.UserPreferences
import com.obdiitools.obd.FreezeFrame
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.SurfaceElevated
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.util.UnitConverter
import com.obdiitools.viewmodel.FreezeFrameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezeFrameScreen(
    onBack: () -> Unit,
    viewModel: FreezeFrameViewModel = hiltViewModel(),
) {
    val frames by viewModel.freezeFrames.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "FREEZE FRAME",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NeonOrange,
                        letterSpacing = 3.sp,
                    )
                    Text(
                        "Fault Snapshot",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDeep),
        )

        HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))

        if (frames.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, null, tint = SurfaceBorder, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No freeze frame data",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan for fault codes to load snapshot data",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(frames) { frame ->
                    FreezeFrameCard(frame, prefs)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun FreezeFrameCard(frame: FreezeFrame, prefs: UserPreferences) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            frame.dtcCode,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = NeonRed,
        )
        HorizontalDivider(color = SurfaceBorder)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            frame.rpm?.let { FreezeRow("RPM", "$it rpm", NeonCyan) }
            frame.speedKph?.let {
                FreezeRow("Speed", "${UnitConverter.formatSpeed(it, prefs.speedUnit)} ${prefs.speedUnit.symbol}", NeonCyan)
            }
            frame.coolantTempC?.let {
                FreezeRow("Coolant Temp", "${UnitConverter.formatTemp(it, prefs.temperatureUnit)}${prefs.temperatureUnit.symbol}", NeonOrange)
            }
            frame.throttlePercent?.let { FreezeRow("Throttle", "${"%.1f".format(it)}%", NeonCyan) }
            frame.engineLoadPercent?.let { FreezeRow("Engine Load", "${"%.1f".format(it)}%", NeonCyan) }
            frame.mafGramsPerSec?.let {
                FreezeRow("MAF", "${UnitConverter.formatAirMass(it, prefs.airMassUnit)} ${prefs.airMassUnit.symbol}", NeonCyan)
            }
            frame.stftBank1Pct?.let { FreezeRow("STFT Bank 1", "%+.1f%%".format(it), if (it < -10 || it > 10) NeonRed else NeonCyan) }
            frame.ltftBank1Pct?.let { FreezeRow("LTFT Bank 1", "%+.1f%%".format(it), if (it < -10 || it > 10) NeonRed else NeonCyan) }
            frame.intakeAirTempC?.let {
                FreezeRow("Intake Air Temp", "${UnitConverter.formatTemp(it, prefs.temperatureUnit)}${prefs.temperatureUnit.symbol}", NeonCyan)
            }
            frame.timingAdvanceDeg?.let { FreezeRow("Timing Advance", "${"%.1f".format(it)}°", NeonCyan) }
        }
    }
}

@Composable
private fun FreezeRow(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
