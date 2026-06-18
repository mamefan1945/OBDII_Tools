package com.obdiitools.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.obdiitools.ui.theme.BackgroundCard
import com.obdiitools.ui.theme.SurfaceBorder

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    accentColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = if (accentColor == Color.Transparent) SurfaceBorder
                      else accentColor.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to BackgroundCard,
                        1f to BackgroundCard.copy(alpha = 0.85f),
                    )
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to borderColor.copy(alpha = 0.6f),
                    1f to borderColor.copy(alpha = 0.1f),
                ),
                shape = shape,
            )
            .padding(16.dp),
        content = content,
    )
}
