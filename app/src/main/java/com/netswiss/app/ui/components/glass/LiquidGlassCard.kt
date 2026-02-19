package com.netswiss.app.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.netswiss.app.ui.theme.Radius
import com.netswiss.app.ui.theme.liquidGlassBlur

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 0.dp,
    shape: Shape = RoundedCornerShape(Radius.card),
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(6.dp, shape, clip = false)
            .then(
                if (blurRadius.value > 0f) {
                    Modifier.liquidGlassBlur(blurRadius, shape)
                } else {
                    Modifier
                }
            )
            .background(tint, shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    )
                ),
                shape
            )
            .border(0.5.dp, borderColor, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}
