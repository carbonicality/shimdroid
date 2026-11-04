package dev.openanalytics.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Flat, cool palette: ink background, slate surfaces, one periwinkle accent. */
object Palette {
    val Bg = Color(0xFF11151C)
    val Surface = Color(0xFF1A2029)
    val SurfaceHigh = Color(0xFF262E3A)
    val OnSurface = Color(0xFFE6EAF2)
    val Muted = Color(0xFF8D97A9)
    val Accent = Color(0xFFA9B8FF)
    val Ink = Color(0xFF141A2B) // text on accent
    val Up = Color(0xFF7FD8B0)
    val Down = Color(0xFFFF9C9C)
}

private val colors = Colors(
    primary = Palette.Accent,
    primaryVariant = Palette.Accent,
    secondary = Palette.Accent,
    secondaryVariant = Palette.Accent,
    background = Palette.Bg,
    surface = Palette.Surface,
    error = Palette.Down,
    onPrimary = Palette.Ink,
    onSecondary = Palette.Ink,
    onBackground = Palette.OnSurface,
    onSurface = Palette.OnSurface,
    onSurfaceVariant = Palette.Muted,
    onError = Palette.Ink,
)

@Composable
fun OpenAnalyticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = colors, content = content)
}
