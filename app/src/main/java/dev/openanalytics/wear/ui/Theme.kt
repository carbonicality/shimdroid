package dev.openanalytics.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val Amber = Color(0xFFF9AB00)
val Orange = Color(0xFFE37400)
val Up = Color(0xFF81C995)
val Down = Color(0xFFF28B82)
val Muted = Color(0xFFBDC1C6)

private val colors = Colors(
    primary = Amber,
    primaryVariant = Orange,
    secondary = Color(0xFF8AB4F8),
    onPrimary = Color.Black,
)

@Composable
fun OpenAnalyticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = colors, content = content)
}
