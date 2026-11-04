package dev.openanalytics.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import dev.openanalytics.wear.data.REALTIME_MINUTES
import dev.openanalytics.wear.data.RingGeometry
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One tick per minute around the screen edge; tick length = users that minute.
 * Ticks for the selected window ([selectedMinutes] back from now) are highlighted.
 * Tapping near the edge selects that minute.
 */
@Composable
fun MinuteRing(
    perMinute: List<Long>,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // perMinute is oldest first; index from "minutes ago".
    val max = (perMinute.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val dx = p.x - c.x
                    val dy = p.y - c.y
                    if (hypot(dx, dy) < size.width * 0.32f) return@detectTapGestures // centre: ignore
                    val deg = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                    RingGeometry.minuteAt(deg)?.let { onSelect(it + 1) }
                }
            },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension / 2f - 7.dp.toPx()
        val minLen = 5.dp.toPx()
        val extra = 13.dp.toPx()
        val stroke = 3.5.dp.toPx()
        for (m in 0 until REALTIME_MINUTES) {
            val v = perMinute.getOrElse(REALTIME_MINUTES - 1 - m) { 0L }
            val rad = Math.toRadians(RingGeometry.angleFor(m).toDouble())
            val s = sin(rad).toFloat()
            val k = -cos(rad).toFloat()
            val len = minLen + extra * (v / max)
            val color = when {
                m == 0 -> Palette.OnSurface
                m < selectedMinutes -> Palette.Accent
                else -> Palette.BarMuted
            }
            drawLine(
                color,
                start = Offset(c.x + outer * s, c.y + outer * k),
                end = Offset(c.x + (outer - len) * s, c.y + (outer - len) * k),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Square daily bars on a baseline with weekday initials; the latest day is highlighted. */
@Composable
fun DayBars(days: List<Pair<LocalDate, Long>>, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    val max = (days.maxOf { it.second }).coerceAtLeast(1).toFloat()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { i, (_, v) ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((v / max).coerceAtLeast(0.05f))
                        .background(if (i == days.lastIndex) Palette.Accent else Palette.BarMuted),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.OnSurface))
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            days.forEachIndexed { i, (date, _) ->
                val last = i == days.lastIndex
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp,
                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (last) Palette.Accent else Palette.Muted,
                )
            }
        }
    }
}

/** Thin share bar under ranked rows. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(Palette.Track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(2.dp).background(color))
    }
}
