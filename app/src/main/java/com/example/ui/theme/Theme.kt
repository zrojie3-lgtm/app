package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = HighDensityBackground,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = HighDensityBackground,
    onSurface = HighDensityBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = HighDensitySurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = HighDensityPrimary,
    onPrimary = HighDensityOnPrimary,
    secondary = HighDensitySecondary,
    background = HighDensityBackground,
    surface = HighDensitySurface,
    onBackground = HighDensityOnBackground,
    onSurface = HighDensityOnSurface,
    surfaceVariant = HighDensitySurfaceVariant,
    onSurfaceVariant = HighDensityOnSurfaceVariant,
    outline = HighDensityOutline,
    outlineVariant = HighDensityOutlineVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve our beautiful high density M3 values
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
