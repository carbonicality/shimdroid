package dev.openanalytics.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Filled line chart; values oldest first. */
@Composable
fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val stepX = size.width / (values.size - 1)
        val stroke = 2.dp.toPx()
        val usableH = size.height - stroke
        fun pt(i: Int) = Offset(i * stepX, stroke / 2 + usableH * (1f - values[i] / max))

        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until values.size) lineTo(pt(i).x, pt(i).y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.18f))
        drawPath(line, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Simple vertical bar chart; values oldest first, last bar highlighted. */
@Composable
fun BarChart(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val gap = 3.dp.toPx()
        val barW = (size.width - gap * (values.size - 1)) / values.size
        val minH = 2.dp.toPx()
        values.forEachIndexed { i, v ->
            val h = (size.height * v / max).coerceAtLeast(minH)
            drawRoundRect(
                color = if (i == values.lastIndex) color else color.copy(alpha = 0.55f),
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}
