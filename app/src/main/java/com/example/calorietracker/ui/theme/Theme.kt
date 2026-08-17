package com.example.calorietracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Petrol als Hauptfarbe, statt des früheren reinen Graustufen-Schemas (das war
// für ein E-Ink-Gerät gedacht — mittlere Farbtöne rendern dort unscharf/mit
// Ghosting). Läuft die App jetzt auf einem normalen Display, darf sie Farbe
// haben.
private val LightColors = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EEFFD),
    onPrimaryContainer = Color(0xFF001F24),
    inversePrimary = Color(0xFF4DD8E6),
    secondary = Color(0xFF4A6367),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8EC),
    onSecondaryContainer = Color(0xFF051F23),
    tertiary = Color(0xFF4D5F7C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD4E3FF),
    onTertiaryContainer = Color(0xFF071A35),
    background = Color(0xFFF7FAFA),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFF7FAFA),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFD5E5E7),
    onSurfaceVariant = Color(0xFF3F4849),
    surfaceTint = Color(0xFF006874),
    inverseSurface = Color(0xFF2D3132),
    inverseOnSurface = Color(0xFFEFF1F1),
    // Bewusst dieselbe Tinte wie primary statt Alarm-Rot: eine Zielüberschreitung
    // ist eine Information, kein Fehler, den man verstecken/vermeiden sollte.
    // Das galt schon beim Graustufen-Theme und ändert sich mit der Farbe nicht.
    error = Color(0xFF006874),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF9EEFFD),
    onErrorContainer = Color(0xFF001F24),
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBFC8CA),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD8E6),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF9EEFFD),
    inversePrimary = Color(0xFF006874),
    secondary = Color(0xFFB1CBCF),
    onSecondary = Color(0xFF1C3437),
    secondaryContainer = Color(0xFF324B4E),
    onSecondaryContainer = Color(0xFFCCE8EC),
    tertiary = Color(0xFFB4C7EA),
    onTertiary = Color(0xFF1D3157),
    tertiaryContainer = Color(0xFF35476D),
    onTertiaryContainer = Color(0xFFD4E3FF),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFDEE3E3),
    surface = Color(0xFF0F1415),
    onSurface = Color(0xFFDEE3E3),
    surfaceVariant = Color(0xFF3F4849),
    onSurfaceVariant = Color(0xFFBFC8CA),
    surfaceTint = Color(0xFF4DD8E6),
    inverseSurface = Color(0xFFDEE3E3),
    inverseOnSurface = Color(0xFF191C1C),
    error = Color(0xFF4DD8E6),
    onError = Color(0xFF00363D),
    errorContainer = Color(0xFF004F58),
    onErrorContainer = Color(0xFF9EEFFD),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4849),
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
