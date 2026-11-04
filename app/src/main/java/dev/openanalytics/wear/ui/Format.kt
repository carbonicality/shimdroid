package dev.openanalytics.wear.ui

import java.util.Locale

/** 950 -> "950", 1234 -> "1.2k", 3_400_000 -> "3.4M". */
fun compact(n: Long): String {
    fun f(v: Double, suffix: String): String {
        val s = if (v >= 100) "%.0f".format(Locale.US, v) else "%.1f".format(Locale.US, v)
        return s.removeSuffix(".0") + suffix
    }
    return when {
        n < 1_000 -> n.toString()
        n < 1_000_000 -> f(n / 1_000.0, "k")
        n < 1_000_000_000 -> f(n / 1_000_000.0, "M")
        else -> f(n / 1_000_000_000.0, "B")
    }
}

/** Percentage change, or null when there's no baseline. */
fun percentChange(current: Long, previous: Long): Int? =
    if (previous <= 0) null else Math.round((current - previous) * 100.0 / previous).toInt()
