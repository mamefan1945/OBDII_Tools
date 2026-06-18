package com.obdiitools.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.obd.ConnectionState
import com.obdiitools.ui.components.ConnectionStatusBar
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.components.DTCCard
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.SurfaceElevated
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun DTCScreen(
    viewModel: MainViewModel,
    onNavigateToFreezeFrame: () -> Unit = {},
    onNavigateToReadiness: () -> Unit = {},
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val dtcList by viewModel.dtcList.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected

    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var scanDone by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // Header
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "DIAGNOSTICS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NeonRed,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = "Fault Codes",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = TextPrimary,
                )
            }

            Spacer(Modifier.height(8.dp))
            ConnectionStatusBar(state = connectionState)
            HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        if (!isScanning && isConnected) {
                            isScanning = true
                            scanDone = false
                            scope.launch {
                                viewModel.scanDTCs()
                                isScanning = false
                                scanDone = true
                            }
                        }
                    },
                    enabled = isConnected && !isScanning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan.copy(alpha = 0.15f),
                        disabledContainerColor = SurfaceElevated,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan,
                        )
                    } else {
                        Icon(Icons.Default.ManageSearch, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (isScanning) "Scanning…" else "Scan Codes",
                        color = if (isConnected) NeonCyan else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }

                Button(
                    onClick = {
                        if (!isClearing && isConnected && dtcList.isNotEmpty()) {
                            isClearing = true
                            scope.launch {
                                viewModel.clearDTCs()
                                isClearing = false
                                scanDone = false
                            }
                        }
                    },
                    enabled = isConnected && dtcList.isNotEmpty() && !isClearing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonRed.copy(alpha = 0.15f),
                        disabledContainerColor = SurfaceElevated,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = NeonRed,
                        )
                    } else {
                        Icon(Icons.Default.DeleteSweep, null, tint = if (dtcList.isNotEmpty() && isConnected) NeonRed else TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (isClearing) "Clearing…" else "Clear All",
                        color = if (dtcList.isNotEmpty() && isConnected) NeonRed else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }
            }

            // Secondary action buttons
            if (isConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onNavigateToReadiness,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Shield, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Readiness", color = NeonGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onNavigateToFreezeFrame,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Camera, null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Freeze Frame", color = NeonOrange, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = Triple(isScanning, scanDone, dtcList),
                label = "dtc_content",
            ) { (scanning, done, codes) ->
                when {
                    scanning -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Reading fault codes…", color = TextSecondary, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    done && codes.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No fault codes detected",
                                    color = NeonGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Your vehicle is running clean",
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    codes.isNotEmpty() -> {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${codes.size} CODE${if (codes.size != 1) "S" else ""} FOUND",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = NeonRed,
                                    letterSpacing = 2.sp,
                                )
                                val pending = codes.count { it.isPending }
                                if (pending > 0) {
                                    Text(
                                        "$pending pending",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(codes, key = { it.code }) { dtc ->
                                    DTCCard(dtc = dtc)
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }
                        }
                    }
                    !isConnected -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Warning, null, tint = SurfaceBorder, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Connect to a device first",
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Tap Scan Codes to begin",
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
