package com.netswiss.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import kotlin.math.ln

fun ColorScheme.surfaceColorAtElevation(elevation: Dp): Color {
    if (elevation.value <= 0f) return surface
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return surfaceTint.copy(alpha = alpha).compositeOver(surface)
}
