package com.obdiitools.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.ui.theme.GaugeHigh
import com.obdiitools.ui.theme.GaugeLow
import com.obdiitools.ui.theme.GaugeMid
import com.obdiitools.ui.theme.GaugeTrack
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircularGauge(
    value: Float?,
    minValue: Float = 0f,
    maxValue: Float = 100f,
    label: String,
    unit: String,
    size: Dp = 160.dp,
    accentColor: Color = NeonCyan,
    showColorZones: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val animatedValue = remember { Animatable(minValue) }

    LaunchedEffect(value) {
        animatedValue.animateTo(
            targetValue = (value ?: minValue).coerceIn(minValue, maxValue),
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        )
    }

    val sweepAngle = 240f
    val startAngle = 150f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = this.size.width * 0.08f
            val radius = (this.size.width - strokeWidth) / 2f
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(radius * 2f, radius * 2f)

            // Background track
            drawArc(
                color = GaugeTrack,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Value arc
            val fraction = (animatedValue.value - minValue) / (maxValue - minValue)
            val valueSweep = fraction * sweepAngle

            if (valueSweep > 0f) {
                val arcColor = when {
                    showColorZones && fraction > 0.85f -> GaugeHigh
                    showColorZones && fraction > 0.6f  -> GaugeMid
                    else -> accentColor
                }

                val brush = Brush.sweepGradient(
                    colors = listOf(arcColor.copy(alpha = 0.4f), arcColor),
                )
                drawArc(
                    brush = brush,
                    startAngle = startAngle,
                    sweepAngle = valueSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Glow effect (second thinner arc)
                drawArc(
                    color = arcColor.copy(alpha = 0.25f),
                    startAngle = startAngle,
                    sweepAngle = valueSweep,
                    useCenter = false,
                    topLeft = Offset(0f, 0f),
                    size = Size(this.size.width, this.size.height),
                    style = Stroke(width = strokeWidth * 2f, cap = StrokeCap.Round),
                )

                // Tip dot
                drawNeedleTip(
                    center = this.center,
                    radius = radius,
                    angle = startAngle + valueSweep,
                    color = arcColor,
                    dotRadius = strokeWidth * 0.7f,
                )
            }

            // Tick marks
            drawTickMarks(
                center = this.center,
                radius = radius - strokeWidth * 0.3f,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                tickCount = 9,
                strokeWidth = strokeWidth,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (value == null) "--" else formatValue(animatedValue.value, unit),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.14f).sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = unit,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = (size.value * 0.07f).sp,
                color = accentColor,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = (size.value * 0.065f).sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatValue(value: Float, unit: String): String {
    return when {
        unit == "RPM"   -> "${(value / 1000f).let { "%.1f".format(it) }}k"
        unit.contains("°") -> "${value.toInt()}°"
        unit == "%"     -> "${value.toInt()}%"
        unit == "km/h"  -> "${value.toInt()}"
        unit == "kPa"   -> "${value.toInt()}"
        else            -> "%.1f".format(value)
    }
}

private fun DrawScope.drawNeedleTip(
    center: Offset,
    radius: Float,
    angle: Float,
    color: Color,
    dotRadius: Float,
) {
    val rad = Math.toRadians(angle.toDouble())
    val x = center.x + radius * cos(rad).toFloat()
    val y = center.y + radius * sin(rad).toFloat()
    drawCircle(color = color, radius = dotRadius, center = Offset(x, y))
    drawCircle(color = color.copy(alpha = 0.3f), radius = dotRadius * 1.8f, center = Offset(x, y))
}

private fun DrawScope.drawTickMarks(
    center: Offset,
    radius: Float,
    startAngle: Float,
    sweepAngle: Float,
    tickCount: Int,
    strokeWidth: Float,
) {
    val innerRadius = radius - strokeWidth * 0.8f
    val outerRadius = radius - strokeWidth * 0.1f
    for (i in 0..tickCount) {
        val angle = startAngle + (sweepAngle * i / tickCount)
        val rad = Math.toRadians(angle.toDouble())
        val startX = center.x + innerRadius * cos(rad).toFloat()
        val startY = center.y + innerRadius * sin(rad).toFloat()
        val endX = center.x + outerRadius * cos(rad).toFloat()
        val endY = center.y + outerRadius * sin(rad).toFloat()
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 1.5f,
        )
    }
}
