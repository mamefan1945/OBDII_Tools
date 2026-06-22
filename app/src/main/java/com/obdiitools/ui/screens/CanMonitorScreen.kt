package com.obdiitools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obdiitools.obd.CanFrame
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.DecodedSignal
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.CanMonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanMonitorScreen(
    onNavigateBack: () -> Unit,
    viewModel: CanMonitorViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected    = connectionState is ConnectionState.Connected
    val isMonitoring   by viewModel.isMonitoring.collectAsState()
    val filteredFrames by viewModel.filteredFrames.collectAsState()
    val decodedSignals by viewModel.decodedSignals.collectAsState()
    val filterText     by viewModel.filterText.collectAsState()
    val showDecoded    by viewModel.showDecoded.collectAsState()
    val showUnknown    by viewModel.showUnknown.collectAsState()
    val dbcLoaded      by viewModel.dbcLoaded.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.stopMonitoring() }
    }

    val subtitle = if (showDecoded) "${decodedSignals.size} signals" else "${filteredFrames.size} frames"

    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CAN MONITOR",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                        )
                        Text(
                            subtitle,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = { viewModel.clearFrames() }) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundDeep),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Filter + START/STOP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { viewModel.setFilter(it) },
                    placeholder = {
                        Text(
                            if (showDecoded) "Filter by signal or message…" else "Filter by ID or data…",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextPrimary,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    trailingIcon = {
                        if (filterText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setFilter("") }) {
                                Icon(Icons.Default.Clear, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
                FilledTonalButton(
                    onClick = {
                        if (isMonitoring) viewModel.stopMonitoring() else viewModel.startMonitoring()
                    },
                    enabled = isConnected,
                ) {
                    Text(
                        if (isMonitoring) "STOP" else "START",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }

            // Mode toggle: RAW | DECODED  +  Unknown chip (DECODED mode only)
            if (dbcLoaded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeTab("RAW",     selected = !showDecoded) { if (showDecoded)  viewModel.toggleDecoded() }
                    ModeTab("DECODED", selected = showDecoded)  { if (!showDecoded) viewModel.toggleDecoded() }
                    if (showDecoded) {
                        Spacer(Modifier.weight(1f))
                        ModeChip("Unknown", active = showUnknown) { viewModel.toggleUnknown() }
                    }
                }
            }

            if (!isConnected) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Not connected", color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
                return@Scaffold
            }

            if (showDecoded) {
                when {
                    !dbcLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading DBC file…", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                    decodedSignals.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (isMonitoring) "Waiting for decoded signals…" else "Press START to begin",
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(decodedSignals, key = { "${it.messageName}.${it.signalName}" }) { sig ->
                            DecodedSignalRow(sig)
                        }
                    }
                }
            } else {
                if (filteredFrames.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (isMonitoring) "Waiting for frames…" else "Press START to begin",
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }
                    return@Scaffold
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredFrames, key = { it.receivedAt }) { frame ->
                        CanFrameRow(frame)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg     = if (selected) NeonCyan.copy(alpha = 0.12f) else Color.Transparent
    val border = if (selected) NeonCyan else SurfaceBorder
    val color  = if (selected) NeonCyan  else TextSecondary
    Text(
        text = label,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun ModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    val bg     = if (active) NeonYellow.copy(alpha = 0.12f) else Color.Transparent
    val border = if (active) NeonYellow else SurfaceBorder
    val color  = if (active) NeonYellow  else TextSecondary
    Text(
        text = label,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun DecodedSignalRow(signal: DecodedSignal) {
    val nameColor  = if (signal.isUnknown) TextSecondary else TextPrimary
    val valueColor = if (signal.isUnknown) TextSecondary else NeonGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceBorder.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = signal.signalName,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = signal.messageName,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = signal.displayValue,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = valueColor,
        )
    }
}

@Composable
private fun CanFrameRow(frame: CanFrame) {
    val idColor = when {
        frame.id.startsWith("7E") -> NeonGreen
        frame.id.startsWith("7D") -> NeonCyan
        else -> TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceBorder.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = frame.id,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = idColor,
            modifier = Modifier.width(42.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = frame.dataHex,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextPrimary,
            )
            Text(
                text = frame.dataAscii,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextSecondary,
            )
        }
        Text(
            text = "${frame.data.size}B",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = NeonRed.copy(alpha = 0.6f),
        )
    }
}
