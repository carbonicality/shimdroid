package dev.openanalytics.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Line chart with a dot on the latest value; values oldest first. */
@Composable
fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(30.dp)) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val dot = 3.dp.toPx()
        val stepX = (size.width - dot) / (values.size - 1)
        val top = dot
        val usableH = size.height - dot * 2
        fun pt(i: Int) = Offset(i * stepX, top + usableH * (1f - values[i] / max))

        // Baseline
        drawLine(
            Palette.SurfaceHigh, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until values.size) lineTo(pt(i).x, pt(i).y)
        }
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Palette.Surface, radius = dot + 1.5.dp.toPx(), center = pt(values.lastIndex))
        drawCircle(color, radius = dot, center = pt(values.lastIndex))
    }
}

/** Daily bars with weekday initials; the most recent day is highlighted. */
@Composable
fun DayBars(days: List<Pair<LocalDate, Long>>, color: Color, modifier: Modifier = Modifier) {
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
                        .fillMaxHeight((v / max).coerceAtLeast(0.06f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i == days.lastIndex) color else Palette.SurfaceHigh),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            days.forEachIndexed { i, (date, _) ->
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp,
                    color = if (i == days.lastIndex) Palette.OnSurface else Palette.Muted,
                )
            }
        }
    }
}

/** Thin share bar used under ranked rows. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.SurfaceHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}
