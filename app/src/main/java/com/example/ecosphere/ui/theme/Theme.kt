package com.example.ecosphere.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = EcoGreenDark,
    onPrimary = Color(0xFF003921),
    primaryContainer = EcoGreenContainerDark,
    onPrimaryContainer = Color(0xFFC8F5D9),
    secondary = EcoTealDark,
    onSecondary = Color(0xFF003731),
    secondaryContainer = EcoTealContainerDark,
    onSecondaryContainer = Color(0xFFBCECE2),
    tertiary = EcoEarthDark,
    background = EcoBackgroundDark,
    surface = EcoSurfaceDark,
    surfaceVariant = EcoSurfaceVariantDark,
    onBackground = EcoOnSurfaceDark,
    onSurface = EcoOnSurfaceDark,
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A)
)

private val LightColorScheme = lightColorScheme(
    primary = EcoGreen,
    onPrimary = Color.White,
    primaryContainer = EcoGreenContainer,
    onPrimaryContainer = Color(0xFF082D1C),
    secondary = EcoTeal,
    onSecondary = Color.White,
    secondaryContainer = EcoTealContainer,
    onSecondaryContainer = Color(0xFF0B2F2A),
    tertiary = EcoEarth,
    background = EcoBackground,
    surface = EcoSurface,
    surfaceVariant = EcoSurfaceVariant,
    onBackground = EcoOnSurface,
    onSurface = EcoOnSurface,
    error = EcoError,
    errorContainer = EcoErrorContainer
)

@Composable
fun EcoSphereTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
