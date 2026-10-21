package com.powermath.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.powermath.data.ThemePref

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DB7FF), onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF1B3A5C), onPrimaryContainer = Color(0xFFD3E6FF),
    secondary = Color(0xFFB4BCC8), onSecondary = Color(0xFF1A1D21),
    secondaryContainer = Color(0xFF2A2E35), onSecondaryContainer = Color(0xFFE3E6EA),
    background = Color(0xFF0E0F11), onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF17191C), onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF202328), onSurfaceVariant = Color(0xFF9AA0A8),
    outline = Color(0xFF3A3E45), error = Color(0xFFFF8A80), onError = Color(0xFF3B0A06)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5FAE), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E6FB), onPrimaryContainer = Color(0xFF00213F),
    secondary = Color(0xFF50565F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E6EB), onSecondaryContainer = Color(0xFF1A1D21),
    background = Color(0xFFF6F7F9), onBackground = Color(0xFF16181B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF16181B),
    surfaceVariant = Color(0xFFECEEF1), onSurfaceVariant = Color(0xFF5F656D),
    outline = Color(0xFFC4C8CE), error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF)
)

@Composable
fun PowerMathTheme(pref: ThemePref, content: @Composable () -> Unit) {
    val dark = when (pref) {
        ThemePref.SYSTEM -> isSystemInDarkTheme()
        ThemePref.DARK -> true
        ThemePref.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

/** Distinct, colour-blind-friendly curve colours (used by the graph screen). */
val GraphColors = listOf(
    Color(0xFF5AA9FF), Color(0xFFFF8C5A), Color(0xFF6FD08C),
    Color(0xFFD98CFF), Color(0xFFFFD35A), Color(0xFF5ADBD0)
)
