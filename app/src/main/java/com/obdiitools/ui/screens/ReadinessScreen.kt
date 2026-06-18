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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
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
import com.obdiitools.obd.ReadinessMonitor
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.SurfaceElevated
import com.obdiitools.ui.theme.TextDim
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.ReadinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessScreen(
    onBack: () -> Unit,
    viewModel: ReadinessViewModel = hiltViewModel(),
) {
    val status by viewModel.readinessStatus.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "READINESS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NeonCyan,
                        letterSpacing = 3.sp,
                    )
                    Text(
                        "Monitors",
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
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, null, tint = NeonCyan)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDeep),
        )

        HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))

        when {
            status == null && !isConnected -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, null, tint = SurfaceBorder, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Connect to view readiness monitors", color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            status == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Reading readiness monitors…", color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            else -> {
                val s = status!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // MIL + DTC summary
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (s.milOn) NeonRed.copy(alpha = 0.12f) else NeonGreen.copy(alpha = 0.12f))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "MIL (CHECK ENGINE)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (s.milOn) NeonRed else NeonGreen,
                                    letterSpacing = 1.sp,
                                )
                                Text(
                                    if (s.milOn) "ON — ${s.dtcCount} fault code${if (s.dtcCount != 1) "s" else ""}" else "OFF — system clear",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                )
                            }
                            Icon(
                                if (s.milOn) Icons.Default.Warning else Icons.Default.CheckCircle,
                                null,
                                tint = if (s.milOn) NeonRed else NeonGreen,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    item {
                        Text(
                            "READINESS MONITORS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            letterSpacing = 2.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    items(s.monitors) { monitor ->
                        MonitorRow(monitor)
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        val supported = s.monitors.filter { it.isSupported }
                        val complete = supported.count { it.isComplete }
                        Text(
                            "$complete / ${supported.size} monitors complete",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (s.allComplete) NeonGreen else NeonYellow,
                        )
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorRow(monitor: ReadinessMonitor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            monitor.name,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = if (monitor.isSupported) TextPrimary else TextDim,
        )
        when {
            !monitor.isSupported -> Icon(
                Icons.Default.Circle,
                null,
                tint = TextDim,
                modifier = Modifier.size(18.dp),
            )
            monitor.isComplete -> Icon(
                Icons.Default.CheckCircle,
                null,
                tint = NeonGreen,
                modifier = Modifier.size(18.dp),
            )
            else -> Icon(
                Icons.Default.Cancel,
                null,
                tint = NeonRed,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
