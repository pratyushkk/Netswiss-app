package com.netswiss.app.ui.components.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.netswiss.app.ui.theme.Motion
import com.netswiss.app.ui.theme.Radius

@Composable
fun LiquidGlassModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = Motion.spring) + scaleIn(animationSpec = Motion.spring, initialScale = 0.96f),
        exit = fadeOut(animationSpec = Motion.spring) + scaleOut(animationSpec = Motion.spring, targetScale = 0.96f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss, indication = null, interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource())
        ) {
            LiquidGlassCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                shape = RoundedCornerShape(Radius.modal),
                contentPadding = contentPadding
            ) {
                content()
            }
        }
    }
}
