package com.obdiitools.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obdiitools.data.FuelEconomyUnit
import com.obdiitools.data.SessionDataPoint
import com.obdiitools.data.SessionEntity
import com.obdiitools.data.SpeedUnit
import com.obdiitools.data.TemperatureUnit
import com.obdiitools.data.UserPreferences
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.util.SessionExporter
import com.obdiitools.util.getSessionTitle
import com.obdiitools.util.UnitConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.SurfaceElevated
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.SessionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onBack: () -> Unit,
    sessionId: Long? = null,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsState()
    val dataPoints by viewModel.selectedDataPoints.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    var selectedSession by remember { mutableStateOf<SessionEntity?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId, sessions) {
        if (sessionId != null && selectedSession == null) {
            viewModel.loadSession(sessionId)
            selectedSession = sessions.find { it.id == sessionId }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "SESSIONS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NeonGreen,
                        letterSpacing = 3.sp,
                    )
                    Text(
                        if (selectedSession != null) "Session Detail" else "Drive History",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (selectedSession != null) {
                        selectedSession = null
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                }
            },
            actions = {
                if (selectedSession != null) {
                    IconButton(onClick = {
                        val session = selectedSession ?: return@IconButton
                        scope.launch(Dispatchers.IO) {
                            val intent = SessionExporter.buildShareIntent(context, session, dataPoints)
                            withContext(Dispatchers.Main) {
                                context.startActivity(android.content.Intent.createChooser(intent, "Share Session"))
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = TextSecondary)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDeep),
        )

        HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))

        if (selectedSession != null) {
            SessionDetailContent(selectedSession!!, dataPoints, prefs)
        } else {
            SessionListContent(
                sessions = sessions,
                prefs = prefs,
                onSelect = { session ->
                    selectedSession = session
                    viewModel.loadSession(session.id)
                },
                onDelete = { viewModel.deleteSession(it.id) },
            )
        }
    }
}

@Composable
private fun SessionListContent(
    sessions: List<SessionEntity>,
    prefs: UserPreferences,
    onSelect: (SessionEntity) -> Unit,
    onDelete: (SessionEntity) -> Unit,
) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, null, tint = SurfaceBorder, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("No sessions recorded", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Connect to start recording", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionRow(session, prefs, onSelect, onDelete)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionEntity,
    prefs: UserPreferences,
    onSelect: (SessionEntity) -> Unit,
    onDelete: (SessionEntity) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceElevated.copy(alpha = 0.5f))
            .clickable { onSelect(session) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                getSessionTitle(session.make, session.model, session.deviceName),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
            )
            Text(
                dateFormat.format(Date(session.startTimeMs)),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSecondary,
            )
            val duration = session.endTimeMs?.let { ((it - session.startTimeMs) / 60000).toInt() }
            if (duration != null) {
                Text(
                    buildString {
                        append("${duration}m")
                        session.maxRpm?.let { append(" · ${it} RPM max") }
                        session.distanceKm?.let {
                            if (prefs.speedUnit == SpeedUnit.KMH) append(" · ${"%.1f".format(it)} km")
                            else append(" · ${"%.1f".format(it * 0.621371f)} mi")
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = NeonCyan,
                )
            }
        }
        IconButton(onClick = { onDelete(session) }) {
            Icon(Icons.Default.Delete, null, tint = NeonRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

private enum class GraphSeries(val label: String, val color: Color) {
    RPM("RPM", NeonCyan),
    SPEED("Speed", NeonOrange),
    COOLANT("Coolant", NeonRed),
    FUEL_ECONOMY("Economy", NeonGreen),
    BATTERY("Battery V", NeonYellow),
}

@Composable
private fun SessionDetailContent(session: SessionEntity, points: List<SessionDataPoint>, prefs: UserPreferences) {
    var selected by remember { mutableStateOf(GraphSeries.RPM) }
    val textMeasurer = rememberTextMeasurer()
    var zoomScale by remember(selected, session.id) { mutableStateOf(1f) }
    var panFraction by remember(selected, session.id) { mutableStateOf(0f) }
    val graphWHolder = remember { floatArrayOf(1f) }

    val hasVoltageData = remember(points) { points.any { it.batteryVoltage != null } }

    val avgFuelEconomy: String? = remember(points, prefs.fuelEconomyUnit) {
        val econValues = points.mapNotNull { pt ->
            val maf = pt.mafGramsPerSec ?: return@mapNotNull null
            val spd = pt.speedKph ?: return@mapNotNull null
            UnitConverter.fuelEconomyL100km(maf, spd)
        }
        if (econValues.isEmpty()) null
        else {
            val avgL100km = econValues.average().toFloat()
            when (prefs.fuelEconomyUnit) {
                FuelEconomyUnit.L100KM -> "${"%.1f".format(avgL100km)} L/100km"
                FuelEconomyUnit.MPG_US -> "${"%.1f".format(UnitConverter.l100kmToMpgUs(avgL100km))} mpg"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        // Summary row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            session.maxRpm?.let { item { StatChip("Max RPM", "$it", NeonCyan) } }
            session.maxSpeedKph?.let {
                val speedStr = "${UnitConverter.formatSpeed(it, prefs.speedUnit)} ${prefs.speedUnit.symbol}"
                item { StatChip("Max Speed", speedStr, NeonOrange) }
            }
            session.distanceKm?.let {
                val distStr = if (prefs.speedUnit == SpeedUnit.KMH) "${"%.1f".format(it)} km"
                              else "${"%.1f".format(it * 0.621371f)} mi"
                item { StatChip("Distance", distStr, NeonGreen) }
            }
            avgFuelEconomy?.let { item { StatChip("Avg Economy", it, NeonGreen) } }
        }

        Spacer(Modifier.height(12.dp))

        // Series selector
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(GraphSeries.entries) { series ->
                FilterChip(
                    selected = selected == series,
                    onClick = { selected = series },
                    label = { Text(series.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = series.color.copy(alpha = 0.15f),
                        selectedLabelColor = series.color,
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Graph
        if (points.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No data points", color = TextSecondary, fontFamily = FontFamily.Monospace)
            }
        } else {
            val timedValues = remember(points, selected, prefs.speedUnit, prefs.temperatureUnit, prefs.fuelEconomyUnit) {
                points.mapNotNull { point ->
                    val v: Float = when (selected) {
                        GraphSeries.RPM          -> point.rpm?.toFloat() ?: return@mapNotNull null
                        GraphSeries.SPEED        -> point.speedKph?.let { UnitConverter.speed(it, prefs.speedUnit) } ?: return@mapNotNull null
                        GraphSeries.COOLANT      -> {
                            val c = point.coolantTempC ?: return@mapNotNull null
                            // -40°C is the OBD raw=0 sentinel; above 130°C is physically implausible.
                            if (c == -40 || c > 130) return@mapNotNull null
                            UnitConverter.temperature(c, prefs.temperatureUnit)
                        }
                        GraphSeries.FUEL_ECONOMY -> {
                            val maf = point.mafGramsPerSec ?: return@mapNotNull null
                            val spd = point.speedKph ?: return@mapNotNull null
                            val l100km = UnitConverter.fuelEconomyL100km(maf, spd) ?: return@mapNotNull null
                            when (prefs.fuelEconomyUnit) {
                                FuelEconomyUnit.L100KM -> l100km
                                FuelEconomyUnit.MPG_US -> UnitConverter.l100kmToMpgUs(l100km)
                            }
                        }
                        GraphSeries.BATTERY      -> point.batteryVoltage ?: return@mapNotNull null
                    }
                    point.timestampMs to v
                }
            }

            val unitSuffix = when (selected) {
                GraphSeries.RPM          -> " rpm"
                GraphSeries.SPEED        -> " ${prefs.speedUnit.symbol}"
                GraphSeries.COOLANT      -> " ${prefs.temperatureUnit.symbol}"
                GraphSeries.FUEL_ECONOMY -> " ${prefs.fuelEconomyUnit.symbol}"
                GraphSeries.BATTERY      -> " V"
            }

            if (timedValues.size < 2) {
                Box(
                    Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Not enough data to graph", color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            } else {
                val values = timedValues.map { it.second }
                val min = values.min()
                val max = values.max()
                val range = (max - min).coerceAtLeast(1f)
                val color = selected.color

                val startMs = timedValues.first().first
                val spanMs = (timedValues.last().first - startMs).coerceAtLeast(1L)
                val visibleSpanMs = (spanMs / zoomScale).toLong().coerceAtLeast(1L)
                val clampedPanFraction = panFraction.coerceIn(0f, (1f - 1f / zoomScale).coerceAtLeast(0f))
                val visibleStart = startMs + (clampedPanFraction * spanMs).toLong()

                val tickIntervalMs: Long = when {
                    visibleSpanMs <= 60_000L    ->  10_000L
                    visibleSpanMs <= 180_000L   ->  30_000L
                    visibleSpanMs <= 600_000L   ->  60_000L
                    visibleSpanMs <= 1_800_000L -> 120_000L
                    visibleSpanMs <= 3_600_000L -> 300_000L
                    else                         -> 600_000L
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newZoom = (zoomScale * zoom).coerceIn(1f, 20f)
                                val newMaxPan = (1f - 1f / newZoom).coerceAtLeast(0f)
                                panFraction = (panFraction - pan.x / graphWHolder[0] / newZoom).coerceIn(0f, newMaxPan)
                                zoomScale = newZoom
                            }
                        }
                        .padding(16.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    val labelH = 18.dp.toPx()
                    val graphH = h - labelH

                    val labelStyle = TextStyle(
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                    )

                    // Y-axis: 5 evenly spaced labels (4 intervals)
                    val ySteps = 4
                    val yAxisLabels = (0..ySteps).map { i ->
                        "%.1f".format(min + range * i.toFloat() / ySteps)
                    }
                    val maxYLabelW = yAxisLabels
                        .maxOf { textMeasurer.measure(it, labelStyle).size.width.toFloat() }
                    val graphLeft = maxYLabelW + 6.dp.toPx()
                    val graphW = w - graphLeft

                    yAxisLabels.forEachIndexed { i, label ->
                        val frac = i.toFloat() / ySteps
                        val y = graphH * (1f - frac)
                        drawLine(
                            color = TextSecondary.copy(alpha = 0.15f),
                            start = Offset(graphLeft, y),
                            end = Offset(w, y),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                        val measured = textMeasurer.measure(label, labelStyle)
                        val labelTop = (y - measured.size.height / 2f)
                            .coerceIn(0f, graphH - measured.size.height.toFloat())
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = Offset(graphLeft - measured.size.width - 4.dp.toPx(), labelTop),
                            style = labelStyle,
                        )
                    }

                    graphWHolder[0] = graphW

                    // Data path
                    val path = Path()
                    var firstPoint = true
                    timedValues.forEach { (ts, v) ->
                        val x = graphLeft + ((ts - visibleStart).toFloat() / visibleSpanMs) * graphW
                        val y = graphH - ((v - min) / range) * graphH
                        if (firstPoint) { path.moveTo(x, y); firstPoint = false } else path.lineTo(x, y)
                    }
                    drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

                    // X-axis tick lines and labels
                    var lastLabelRightX = graphLeft - 1f
                    val firstTickN = (visibleStart - startMs) / tickIntervalMs + 1
                    var tickMs = startMs + firstTickN * tickIntervalMs
                    val visibleEnd = visibleStart + visibleSpanMs
                    while (tickMs < visibleEnd) {
                        val x = graphLeft + ((tickMs - visibleStart).toFloat() / visibleSpanMs) * graphW
                        drawLine(
                            color = TextSecondary.copy(alpha = 0.3f),
                            start = Offset(x, 0f),
                            end = Offset(x, graphH),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                        val label = formatElapsed(tickMs - startMs)
                        val measured = textMeasurer.measure(label, labelStyle)
                        val textW = measured.size.width.toFloat()
                        val labelLeft = (x - textW / 2f).coerceIn(graphLeft, w - textW)
                        if (labelLeft >= lastLabelRightX + 4.dp.toPx()) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = label,
                                topLeft = Offset(labelLeft, graphH + 2.dp.toPx()),
                                style = labelStyle,
                            )
                            lastLabelRightX = labelLeft + textW
                        }
                        tickMs += tickIntervalMs
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Min: ${"%.1f".format(min)}$unitSuffix", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextSecondary)
                    if (zoomScale > 1.05f)
                        Text("${"%.1f".format(zoomScale)}×", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = color.copy(alpha = 0.7f))
                    Text("Max: ${"%.1f".format(max)}$unitSuffix", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = selected.color)
                }
            }
        }

        if (hasVoltageData) {
            Spacer(Modifier.height(12.dp))
            BatteryAnalysisCard(points = points)
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BatteryAnalysisCard(points: List<SessionDataPoint>) {
    val allVolts = points.mapNotNull { it.batteryVoltage }
    if (allVolts.isEmpty()) return

    val minV = allVolts.min()
    val maxV = allVolts.max()
    val avgV = allVolts.average().toFloat()

    // speed > 0 = moving = engine on; rpm > 0 catches idle stops when the adapter responds.
    // Post-engine-off voltage drops are normal and must not drive alternator warnings.
    val runningVolts = points
        .filter { (it.speedKph ?: 0) > 0 || (it.rpm ?: 0) > 0 }
        .mapNotNull { it.batteryVoltage }

    val (statusText, statusColor) = when {
        maxV > 15.0f                                              -> "Overcharging" to NeonRed
        runningVolts.size >= 5 && runningVolts.min() < 11.5f     -> "Weak Battery?" to NeonRed
        runningVolts.size >= 5 && runningVolts.max() < 13.4f     -> "Check Alternator" to NeonOrange
        else                                                      -> "Healthy" to NeonGreen
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(statusColor.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("BATTERY HEALTH", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TextSecondary, letterSpacing = 2.sp)
            Text(statusText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = statusColor)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            VoltStat("Min", minV)
            VoltStat("Avg", avgV)
            VoltStat("Max", maxV)
        }
    }
}

@Composable
private fun VoltStat(label: String, volts: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${"%.1f".format(volts)}V", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NeonYellow)
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextSecondary)
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextSecondary)
    }
}
