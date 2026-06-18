package com.obdiitools.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obdiitools.data.UserPreferences
import com.obdiitools.obd.AllPidDefinitions
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.LivePidValue
import com.obdiitools.obd.PidCategory
import com.obdiitools.util.UnitConverter
import com.obdiitools.ui.theme.BackgroundCard
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
import com.obdiitools.viewmodel.LiveDataViewModel

private fun PidCategory.accentColor(): Color = when (this) {
    PidCategory.ENGINE          -> NeonCyan
    PidCategory.SPEED_DISTANCE  -> NeonOrange
    PidCategory.TEMPERATURE     -> NeonRed
    PidCategory.FUEL            -> NeonGreen
    PidCategory.AIR_PRESSURE    -> NeonPurple
    PidCategory.THROTTLE_LOAD   -> NeonYellow
    PidCategory.OXYGEN          -> NeonCyan
    PidCategory.ELECTRICAL      -> NeonGreen
    PidCategory.EMISSIONS       -> NeonOrange
    PidCategory.TIME_COUNTERS   -> TextSecondary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDataScreen(
    onNavigateBack: () -> Unit,
    viewModel: LiveDataViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val pidValues by viewModel.pidValues.collectAsState()
    val scanIndex by viewModel.scanIndex.collectAsState()
    val discoveryComplete by viewModel.discoveryComplete.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    var showSupportedOnly by remember { mutableStateOf(false) }

    DisposableEffect(isConnected) {
        if (isConnected) viewModel.startLiveScan()
        onDispose { viewModel.stopLiveScan() }
    }

    val scanProgress = if (scanIndex >= 0 && viewModel.totalPids > 0)
        scanIndex.toFloat() / viewModel.totalPids.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = scanProgress,
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "scanProgress",
    )

    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "ALL PARAMETERS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp,
                        )
                        if (isConnected) {
                            Text(
                                if (scanIndex >= 0) "SCANNING ${scanIndex + 1}/${viewModel.totalPids}"
                                else "READY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = if (scanIndex >= 0) NeonCyan else TextSecondary,
                                letterSpacing = 1.sp,
                            )
                        }
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
                actions = {
                    if (discoveryComplete) {
                        IconButton(onClick = { showSupportedOnly = !showSupportedOnly }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = if (showSupportedOnly) "Show all" else "Supported only",
                                tint = if (showSupportedOnly) NeonCyan else TextSecondary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundCard,
                    titleContentColor = TextPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isConnected && scanIndex >= 0) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = NeonCyan,
                    trackColor = SurfaceBorder,
                )
            }

            if (!isConnected) {
                LiveDisconnectedState()
            } else {
                val displayed = if (showSupportedOnly)
                    pidValues.filter { it.supported == true }
                else
                    pidValues
                LiveParamList(
                    pidValues = displayed,
                    prefs = prefs,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LiveDisconnectedState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                "to view live parameters",
                color = TextSecondary.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveParamList(
    pidValues: List<LivePidValue>,
    prefs: UserPreferences,
    modifier: Modifier = Modifier,
) {
    val byCategory = remember(pidValues) {
        pidValues.groupBy { it.definition.category }
    }
    LazyColumn(modifier = modifier.padding(bottom = 80.dp)) {
        PidCategory.entries.forEach { category ->
            val pids = byCategory[category] ?: return@forEach
            stickyHeader(key = "header_${category.name}") {
                CategoryStickyHeader(category)
            }
            items(items = pids, key = { it.definition.command }) { liveValue ->
                PidRow(liveValue, category.accentColor(), prefs)
            }
        }
    }
}

@Composable
private fun CategoryStickyHeader(category: PidCategory) {
    val color = category.accentColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDeep.copy(alpha = 0.97f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = category.label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = color,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun PidRow(liveValue: LivePidValue, accentColor: Color, prefs: UserPreferences) {
    val isUnsupported = liveValue.supported == false
    val hasValue = liveValue.value != null && !isUnsupported
    val nameColor = if (isUnsupported) TextSecondary.copy(alpha = 0.4f) else TextPrimary
    val valueColor = when {
        isUnsupported -> TextSecondary.copy(alpha = 0.35f)
        liveValue.value != null -> accentColor
        else -> TextSecondary.copy(alpha = 0.5f)
    }
    val displayText = when {
        isUnsupported       -> "N/A"
        liveValue.value == null -> "—"
        else -> UnitConverter.formatPidValue(liveValue.value, liveValue.definition.unit, prefs)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = liveValue.definition.name,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = nameColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = displayText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = valueColor,
                textAlign = TextAlign.End,
            )
        }
        if (hasValue) {
            Spacer(Modifier.height(4.dp))
            MiniProgressBar(
                value = liveValue.value!!,
                min = liveValue.definition.minValue,
                max = liveValue.definition.maxValue,
                color = accentColor,
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(SurfaceBorder.copy(alpha = if (isUnsupported) 0.3f else 0.6f)),
            )
        }
    }
}

@Composable
private fun MiniProgressBar(value: Float, min: Float, max: Float, color: Color) {
    val range = (max - min).coerceAtLeast(0.001f)
    val fraction = ((value - min) / range).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 300),
        label = "barProgress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(SurfaceBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(color),
        )
    }
}
