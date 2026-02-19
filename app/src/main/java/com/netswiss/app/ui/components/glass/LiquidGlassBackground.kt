package com.netswiss.app.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.netswiss.app.ui.theme.AppBackgroundDark
import com.netswiss.app.ui.theme.Blue40
import com.netswiss.app.ui.theme.Cyan40

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        AppBackgroundDark,
                        AppBackgroundDark.copy(alpha = 0.95f),
                        Blue40.copy(alpha = 0.08f),
                        Cyan40.copy(alpha = 0.08f),
                        AppBackgroundDark
                    )
                )
            )
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.03f),
                        Color.Transparent
                    )
                )
            )
    )
}
