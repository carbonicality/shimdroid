package dev.openanalytics.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import dev.openanalytics.wear.R

/** "Paper, dark": warm graphite, hairlines, one blue accent. */
object Palette {
    val Bg = Color(0xFF1D1C1A)
    val Surface = Color(0xFF262522)
    val Hairline = Color(0xFF3A3833)
    val Track = Color(0xFF2B2A27)
    val BarMuted = Color(0xFF45423C)
    val OnSurface = Color(0xFFEDE9E1)
    val Muted = Color(0xFF9F9B92)
    val Accent = Color(0xFF8EA8FF)
    val Up = Color(0xFF7FD3A2)
    val Down = Color(0xFFF29A8C)
}

/** DM Sans (variable font; the weight axis follows FontWeight). SIL OFL, see licenses/. */
val DmSans = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
    Font(R.font.dm_sans, FontWeight.SemiBold),
)

private val colors = Colors(
    primary = Palette.OnSurface,
    primaryVariant = Palette.OnSurface,
    secondary = Palette.Accent,
    secondaryVariant = Palette.Accent,
    background = Palette.Bg,
    surface = Palette.Surface,
    error = Palette.Down,
    onPrimary = Palette.Bg,
    onSecondary = Palette.Bg,
    onBackground = Palette.OnSurface,
    onSurface = Palette.OnSurface,
    onSurfaceVariant = Palette.Muted,
    onError = Palette.Bg,
)

@Composable
fun OpenAnalyticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = colors, typography = Typography(defaultFontFamily = DmSans), content = content)
}
