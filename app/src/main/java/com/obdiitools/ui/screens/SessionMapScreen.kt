package com.obdiitools.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.obdiitools.data.SessionDataPoint
import com.obdiitools.data.SpeedUnit
import com.obdiitools.data.UserPreferences
import com.obdiitools.ui.components.GlassCard
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.util.UnitConverter
import com.obdiitools.viewmodel.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionMapScreen(sessionId: Long, onBack: () -> Unit) {
    val viewModel: SessionViewModel = hiltViewModel()
    val gpsPoints by viewModel.gpsPoints.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    var selectedPoint by remember { mutableStateOf<SessionDataPoint?>(null) }

    // Cumulative fuel (litres) at each GPS index
    val cumulativeFuel = remember(gpsPoints) {
        val result = FloatArray(gpsPoints.size)
        var total = 0f
        for (i in 1 until gpsPoints.size) {
            val prev = gpsPoints[i - 1]
            val curr = gpsPoints[i]
            val maf = prev.mafGramsPerSec
            if (maf != null && maf > 0f) {
                val elapsedHours = (curr.timestampMs - prev.timestampMs) / 3_600_000f
                total += UnitConverter.fuelFlowLph(maf) * elapsedHours
            }
            result[i] = total
        }
        result.toList()
    }

    // Cumulative distance (metres) at each GPS index
    val cumulativeDistancesM = remember(gpsPoints) {
        val result = FloatArray(gpsPoints.size)
        for (i in 1 until gpsPoints.size) {
            val prev = gpsPoints[i - 1]
            val curr = gpsPoints[i]
            result[i] = result[i - 1] + if (
                prev.latitude != null && prev.longitude != null &&
                curr.latitude != null && curr.longitude != null
            ) {
                haversineMetres(prev.latitude, prev.longitude, curr.latitude, curr.longitude).toFloat()
            } else 0f
        }
        result.toList()
    }
    val totalDistanceM = cumulativeDistancesM.lastOrNull() ?: 0f

    val costAtPoint = remember(selectedPoint, cumulativeFuel, prefs) {
        selectedPoint?.let { pt ->
            val idx = gpsPoints.indexOfFirst { it.id == pt.id }
            if (idx >= 0) UnitConverter.fuelCost(cumulativeFuel.getOrElse(idx) { 0f }, prefs)
            else null
        }
    }

    val distanceInfoM: Pair<Float, Float>? = remember(selectedPoint, cumulativeDistancesM) {
        selectedPoint?.let { pt ->
            val idx = gpsPoints.indexOfFirst { it.id == pt.id }
            if (idx >= 0) {
                val soFar = cumulativeDistancesM.getOrElse(idx) { 0f }
                soFar to (totalDistanceM - soFar)
            } else null
        }
    }

    // Speed limit fetched on-demand from OSM Overpass when a point is selected
    var speedLimitKph by remember { mutableStateOf<Float?>(null) }
    var speedLimitLoading by remember { mutableStateOf(false) }
    LaunchedEffect(selectedPoint) {
        val pt = selectedPoint
        if (pt?.latitude != null && pt.longitude != null) {
            speedLimitLoading = true
            speedLimitKph = null
            speedLimitKph = fetchSpeedLimitKph(pt.latitude, pt.longitude)
            speedLimitLoading = false
        } else {
            speedLimitKph = null
            speedLimitLoading = false
        }
    }

    LaunchedEffect(sessionId) { viewModel.loadGpsTrail(sessionId) }

    val cameraPositionState = rememberCameraPositionState()
    LaunchedEffect(gpsPoints) {
        if (gpsPoints.size >= 2) {
            val bounds = LatLngBounds.builder().apply {
                gpsPoints.forEach { include(it.latLng()) }
            }.build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (gpsPoints.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BackgroundDeep),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No GPS data recorded",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Grant location permission before connecting",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { tapped ->
                    selectedPoint = gpsPoints
                        .filter { it.latitude != null && it.longitude != null }
                        .minByOrNull { pt ->
                            haversineMetres(tapped.latitude, tapped.longitude, pt.latitude!!, pt.longitude!!)
                        }
                },
            ) {
                Polyline(
                    points = gpsPoints.map { it.latLng() },
                    color  = NeonCyan,
                    width  = 8f,
                )
                Marker(
                    state = MarkerState(position = gpsPoints.first().latLng()),
                    title = "Start",
                )
                Marker(
                    state = MarkerState(position = gpsPoints.last().latLng()),
                    title = "End",
                )
                selectedPoint?.let { pt ->
                    Circle(
                        center      = pt.latLng(),
                        radius      = 20.0,
                        fillColor   = NeonOrange.copy(alpha = 0.5f),
                        strokeColor = NeonOrange,
                        strokeWidth = 4f,
                    )
                }
            }
        }

        TopAppBar(
            title = {
                Column {
                    Text(
                        "GPS TRAIL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = NeonCyan,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        "Session Map",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        color = TextPrimary,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDeep.copy(alpha = 0.85f)),
        )

        AnimatedVisibility(
            visible = selectedPoint != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit  = slideOutVertically { it },
        ) {
            selectedPoint?.let { pt ->
                OBDPointCard(
                    point = pt,
                    prefs = prefs,
                    sessionStartMs = gpsPoints.firstOrNull()?.timestampMs ?: pt.timestampMs,
                    costSoFar = costAtPoint,
                    speedLimitKph = speedLimitKph,
                    speedLimitLoading = speedLimitLoading,
                    distanceInfoM = distanceInfoM,
                    onDismiss = { selectedPoint = null },
                )
            }
        }
    }
}

@Composable
private fun OBDPointCard(
    point: SessionDataPoint,
    prefs: UserPreferences,
    sessionStartMs: Long,
    costSoFar: Float?,
    speedLimitKph: Float?,
    speedLimitLoading: Boolean,
    distanceInfoM: Pair<Float, Float>?,
    onDismiss: () -> Unit,
) {
    val totalSec = (point.timestampMs - sessionStartMs) / 1000
    val timeLabel = "%d:%02d into session".format(totalSec / 60, totalSec % 60)

    val distLabel: String? = distanceInfoM?.let { (soFarM, remainingM) ->
        val (soFar, remaining) = when (prefs.speedUnit) {
            SpeedUnit.MPH -> soFarM / 1609.34f to remainingM / 1609.34f
            SpeedUnit.KMH -> soFarM / 1000f to remainingM / 1000f
        }
        val unit = if (prefs.speedUnit == SpeedUnit.MPH) "mi" else "km"
        "${"%.1f".format(soFar)} / -${"%.1f".format(remaining)} $unit"
    }

    val headerLine = if (distLabel != null) "$timeLabel  ·  $distLabel" else timeLabel

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        accentColor = NeonOrange,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    headerLine,
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NeonOrange,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                point.speedKph?.let { kph ->
                    val speedStr = UnitConverter.formatSpeed(kph, prefs.speedUnit)
                    val limitStr = when {
                        speedLimitLoading -> "?"
                        speedLimitKph != null -> UnitConverter.formatSpeed(speedLimitKph.toInt(), prefs.speedUnit)
                        else -> "--"
                    }
                    OBDPointStat("Speed / Limit", "$speedStr / $limitStr ${prefs.speedUnit.symbol}")
                }
                point.rpm?.let {
                    OBDPointStat("RPM", "$it")
                }
                point.coolantTempC?.let {
                    OBDPointStat("Coolant", "${UnitConverter.formatTemp(it, prefs.temperatureUnit)} ${prefs.temperatureUnit.symbol}")
                }
                point.throttlePercent?.let {
                    OBDPointStat("Throttle", "${"%.0f".format(it)}%")
                }
                point.engineLoadPercent?.let {
                    OBDPointStat("Load", "${"%.0f".format(it)}%")
                }
                costSoFar?.let {
                    OBDPointStat("Est. Cost", "$${"%.2f".format(it)}")
                }
            }
        }
    }
}

@Composable
private fun OBDPointStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = TextPrimary,
        )
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = TextSecondary,
        )
    }
}

private suspend fun fetchSpeedLimitKph(lat: Double, lon: Double): Float? = withContext(Dispatchers.IO) {
    try {
        val query = "[out:json][timeout:10];way(around:30,$lat,$lon)[highway][maxspeed];out tags;"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val conn = URL("https://overpass-api.de/api/interpreter?data=$encoded")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout    = 10_000
        conn.setRequestProperty("User-Agent", "OBDIITools/1.0")
        val json = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val elements = JSONObject(json).getJSONArray("elements")
        for (i in 0 until elements.length()) {
            val tags = elements.getJSONObject(i).optJSONObject("tags") ?: continue
            val maxspeed = tags.optString("maxspeed", "").takeIf { it.isNotEmpty() } ?: continue
            val kph = parseMaxspeedKph(maxspeed)
            if (kph != null) return@withContext kph
        }
        null
    } catch (_: Exception) {
        null
    }
}

private fun parseMaxspeedKph(value: String): Float? {
    val s = value.trim().lowercase()
    if (s in setOf("none", "signals", "national", "rural", "urban", "living_street", "walk")) return null
    val mphMatch = Regex("""^(\d+(?:\.\d+)?)\s*mph$""").find(s)
    if (mphMatch != null) return mphMatch.groupValues[1].toFloat() * 1.60934f
    return s.replace(Regex("""\s*km[/h]*$"""), "").toFloatOrNull()
}

private fun haversineMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(a))
}

private fun SessionDataPoint.latLng() = LatLng(latitude!!, longitude!!)
