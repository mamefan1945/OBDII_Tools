package com.obdiitools.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.ui.theme.GaugeTrack
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary

@Composable
fun LinearGauge(
    value: Float?,
    minValue: Float = 0f,
    maxValue: Float = 100f,
    label: String,
    unit: String,
    accentColor: Color = NeonCyan,
    modifier: Modifier = Modifier,
) {
    val animatedFraction = remember { Animatable(0f) }

    LaunchedEffect(value) {
        val fraction = if (value == null) 0f
            else ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        animatedFraction.animateTo(
            targetValue = fraction,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (value == null) "--" else "${formatLinearValue(value, unit)} $unit",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 4.dp)
        ) {
            val cornerRadius = CornerRadius(4.dp.toPx())

            // Track
            drawRoundRect(
                color = GaugeTrack,
                topLeft = Offset(0f, 0f),
                size = Size(this.size.width, this.size.height),
                cornerRadius = cornerRadius,
            )

            // Fill
            val fillWidth = animatedFraction.value * this.size.width
            if (fillWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to accentColor.copy(alpha = 0.7f),
                        1f to accentColor,
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(fillWidth, this.size.height),
                    cornerRadius = cornerRadius,
                )
            }
        }
    }
}

private fun formatLinearValue(value: Float, unit: String): String {
    return when {
        unit.contains("°") -> "${value.toInt()}"
        unit == "%"        -> "${value.toInt()}"
        unit == "km/h"     -> "${value.toInt()}"
        else               -> "%.1f".format(value)
    }
}
