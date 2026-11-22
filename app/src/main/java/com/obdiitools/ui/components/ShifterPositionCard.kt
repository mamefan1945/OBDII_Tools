package com.obdiitools.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.TextSecondary

/** Shared shifter-gate graphic; vehicle-specific decoding and gate order are supplied by callers. */
@Composable
fun ShifterPositionCard(
    title: String,
    positions: List<String>,
    selectedPosition: String?,
    statusText: String,
    detailText: String? = null,
) {
    require(positions.isNotEmpty())
    val normalized = positions.map { it.trim().uppercase() }
    val selected = normalized.indexOf(selectedPosition?.trim()?.uppercase()).takeIf { it >= 0 }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, color = NeonCyan, letterSpacing = 1.sp)
                Text(selected?.let { normalized[it] } ?: "UNKNOWN",
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = if (selected != null) NeonCyan else NeonOrange)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF171B20)).border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val gateHeight = (normalized.size * 36).dp
                Column(modifier = Modifier.width(38.dp).height(gateHeight),
                    verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.End) {
                    normalized.forEachIndexed { index, position ->
                        Text(position, fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected == index) FontWeight.Black else FontWeight.Medium,
                            fontSize = 17.sp, color = if (selected == index) NeonCyan else TextSecondary)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Canvas(modifier = Modifier.width(52.dp).height(gateHeight)) {
                    val x = size.width / 2f
                    val top = 14.dp.toPx()
                    val bottom = size.height - 14.dp.toPx()
                    drawLine(Color(0xFF050709), Offset(x, top), Offset(x, bottom), 20.dp.toPx())
                    drawLine(Color(0xFF525B64), Offset(x - 7.dp.toPx(), top),
                        Offset(x - 7.dp.toPx(), bottom), 1.dp.toPx())
                    selected?.let { index ->
                        val y = if (normalized.size == 1) size.height / 2f
                            else top + (bottom - top) * index / (normalized.size - 1).toFloat()
                        drawCircle(Color(0xFF9DA8B2), 13.dp.toPx(), Offset(x, y))
                        drawCircle(NeonCyan, 10.dp.toPx(), Offset(x, y))
                        drawCircle(Color.White.copy(alpha = 0.9f), 4.dp.toPx(),
                            Offset(x - 3.dp.toPx(), y - 3.dp.toPx()))
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.height(gateHeight), verticalArrangement = Arrangement.SpaceEvenly) {
                    normalized.forEachIndexed { index, position ->
                        val label = when (position) {
                            "P" -> "PARK"; "R" -> "REVERSE"; "N" -> "NEUTRAL"
                            "D" -> "DRIVE"; "L" -> "LOW"; "M" -> "MANUAL"
                            else -> position
                        }
                        Box(modifier = Modifier.size(width = 78.dp, height = 26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected == index) NeonCyan.copy(alpha = 0.14f) else Color.Transparent)
                            .border(if (selected == index) 1.dp else 0.dp,
                                if (selected == index) NeonCyan.copy(alpha = 0.7f) else Color.Transparent,
                                RoundedCornerShape(6.dp)).padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart) {
                            Text(label, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                                fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected == index) NeonCyan else TextSecondary)
                        }
                    }
                }
            }
            Text(listOfNotNull(detailText, statusText).joinToString(" · "),
                fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextSecondary)
        }
    }
}
