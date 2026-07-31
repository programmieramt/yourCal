package com.example.calorietracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Reines Graustufen-Schema, keine Buntfarben: auf E-Ink-Displays (z.B. Boox-
// Geräte) rendern mittlere Farbtöne wie Grün/Amber als unscharfe, kontrastarme
// Graupixel. Schwarz/Weiß/Grau mit hohem Kontrast ist auf E-Ink deutlich
// lesbarer und vermeidet zusätzliches Ghosting beim Refresh.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A1A1A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9D9D9),
    onPrimaryContainer = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFFCFCFCF),
    secondary = Color(0xFF4D4D4D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E3E3),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF4D4D4D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E3E3),
    onTertiaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFF3A3A3A),
    surfaceTint = Color(0xFF1A1A1A),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFFFFFFF),
    // Bewusst dieselbe neutrale Tinte statt Alarm-Rot: eine Zielüberschreitung
    // ist eine Information, kein Fehler, den man verstecken/vermeiden sollte.
    error = Color(0xFF1A1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFD9D9D9),
    onErrorContainer = Color(0xFF1A1A1A),
    outline = Color(0xFF6E6E6E),
    outlineVariant = Color(0xFFC2C2C2),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF3A3A3A),
    onPrimaryContainer = Color(0xFFE0E0E0),
    inversePrimary = Color(0xFF3A3A3A),
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFFB0B0B0),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2A2A2A),
    onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFCCCCCC),
    surfaceTint = Color(0xFFE0E0E0),
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFE0E0E0),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF3A3A3A),
    onErrorContainer = Color(0xFFE0E0E0),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF4A4A4A),
    scrim = Color(0xFF000000),
)

@Composable
fun CalorieTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
