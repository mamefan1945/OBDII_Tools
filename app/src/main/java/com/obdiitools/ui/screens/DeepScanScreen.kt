package com.obdiitools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.obdiitools.obd.ConnectionState
import com.obdiitools.ui.theme.BackgroundCard
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonPurple
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.DeepScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeepScanViewModel = hiltViewModel(),
) {
    val scanState by viewModel.scanState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected

    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "DEEP SCAN",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp,
                        )
                        if (scanState.results.isNotEmpty() || scanState.isScanning) {
                            Text(
                                scanState.phase,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = if (scanState.isScanning) NeonCyan else TextSecondary,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundCard),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Progress bar during scan
            if (scanState.isScanning) {
                val (done, total) = scanState.progress ?: (0 to 1)
                LinearProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = NeonCyan,
                    trackColor = SurfaceBorder,
                )
            }

            if (!isConnected) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BluetoothDisabled, null, tint = SurfaceBorder, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Connect to a device", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                        Text("to run a deep scan", color = TextSecondary.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                // Explanation / start panel
                if (scanState.results.isEmpty() && !scanState.isScanning) {
                    item {
                        DeepScanInfoPanel(
                            onStart = { viewModel.startScan() },
                        )
                    }
                }

                // ECU result cards
                items(scanState.results, key = { it.address }) { ecuResult ->
                    EcuResultCard(ecuResult)
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            // Bottom action bar
            if (isConnected && (scanState.isScanning || scanState.results.isNotEmpty())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundCard)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (scanState.isScanning) {
                        OutlinedButton(
                            onClick = { viewModel.cancelScan() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        ) {
                            Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startScan() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.85f)),
                        ) {
                            Text("SCAN AGAIN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BackgroundDeep)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepScanInfoPanel(onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "HOW IT WORKS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
            InfoLine("1", "Scans all 128 addresses in the standard ECU range (0x700–0x77F) to find responding modules.")
            InfoLine("2", "Queries each responding ECU using the OBD-II PID support bitmask — no guessing, only confirmed-supported PIDs are listed.")
            InfoLine("3", "Also checks Mode 09 (vehicle info) on the broadcast address to discover VIN, calibration IDs, and ECU names.")
            Text(
                "This takes 2–5 minutes depending on how many ECUs respond.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.6f),
            )
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.85f)),
        ) {
            Text(
                "START DEEP SCAN",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = BackgroundDeep,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun InfoLine(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonCyan)
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun EcuResultCard(result: DeepScanViewModel.EcuResult) {
    val accentColor = when {
        result.address == "7DF"                      -> NeonCyan
        result.address.uppercase() == "7E0"          -> NeonGreen
        result.address.uppercase() == "7E1"          -> NeonOrange
        result.address.uppercase() in setOf("7B0", "748") -> NeonPurple
        else                                         -> NeonYellow
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.05f)),
    ) {
        // ECU header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "0x${result.address}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = accentColor,
                )
                Text(
                    result.moduleName ?: "Unknown Module",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (result.moduleName != null) TextPrimary else TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${result.totalPids} PIDs",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = accentColor,
                )
                if (result.mode09Pids.isNotEmpty()) {
                    Text(
                        "+${result.mode09Pids.size} info",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextSecondary,
                    )
                }
            }
        }

        // Mode 01 PIDs
        if (result.mode01Pids.isNotEmpty()) {
            ModeSection("MODE 01 — LIVE DATA", result.mode01Pids, accentColor)
        }

        // Mode 09 PIDs
        if (result.mode09Pids.isNotEmpty()) {
            ModeSection("MODE 09 — VEHICLE INFO", result.mode09Pids, NeonPurple)
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ModeSection(
    title: String,
    pids: List<DeepScanViewModel.SupportedPid>,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Text(
            title,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = accentColor.copy(alpha = 0.7f),
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        pids.forEach { pid ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor.copy(alpha = 0.5f)),
                )
                Text(
                    pid.code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = accentColor,
                    modifier = Modifier.width(42.dp),
                )
                Text(
                    pid.name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}
