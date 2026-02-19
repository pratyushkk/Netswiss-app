package com.netswiss.app.ui.theme

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.liquidGlassBlur(
    radius: Dp,
    shape: Shape
): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val px = radius.value
        this
            .clip(shape)
            .graphicsLayer {
                renderEffect = AndroidRenderEffect.createBlurEffect(
                    px,
                    px,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
    } else {
        this.clip(shape)
    }
}

val DefaultBlur = 20.dp
