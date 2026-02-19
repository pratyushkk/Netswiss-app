package com.netswiss.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Cyan80,
    surface = SurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    background = AppBackgroundDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    onBackground = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Cyan40,
    surface = SurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    background = AppBackgroundLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    onBackground = TextPrimaryLight
)

@Composable
fun NetSwissTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
