package com.hibernador.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF5EEAD4)
val Indigo = Color(0xFFA5B4FC)
val Background = Color(0xFF0D1117)
val Surface = Color(0xFF161B22)
val SurfaceHigh = Color(0xFF21262D)
val TextMuted = Color(0xFF8B949E)
val Danger = Color(0xFFF87171)
val Success = Color(0xFF4ADE80)

private val scheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF00201C),
    secondary = Indigo,
    onSecondary = Color(0xFF0F1240),
    background = Background,
    onBackground = Color(0xFFE6EDF3),
    surface = Surface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    error = Danger,
    outline = Color(0xFF30363D),
)

@Composable
fun HibernadorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
