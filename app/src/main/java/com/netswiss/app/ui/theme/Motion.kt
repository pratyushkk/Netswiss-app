package com.netswiss.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object Motion {
    val spring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMedium
    )
}
