package dev.openanalytics.wear.data

import kotlin.math.roundToInt

/** Ring geometry shared with the tile: minute m (0 = now) sits at START + m * STEP degrees, clockwise from 12 o'clock. */
object RingGeometry {
    const val START_DEG = 30f
    const val SPAN_DEG = 300f
    val STEP_DEG = SPAN_DEG / (REALTIME_MINUTES - 1)

    fun angleFor(minutesAgo: Int) = START_DEG + minutesAgo * STEP_DEG

    /** Which minute (0 = now) an angle (degrees clockwise from 12) points at, or null in the gap at the top. */
    fun minuteAt(angleDeg: Float): Int? {
        val a = ((angleDeg % 360f) + 360f) % 360f
        if (a < START_DEG - STEP_DEG / 2 || a > START_DEG + SPAN_DEG + STEP_DEG / 2) return null
        return ((a - START_DEG) / STEP_DEG).roundToInt().coerceIn(0, REALTIME_MINUTES - 1)
    }
}
