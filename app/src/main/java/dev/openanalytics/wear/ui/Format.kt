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

/** Full number with grouping ("1,284"); compact above 100k so it still fits. */
fun full(n: Long): String = if (n >= 100_000) compact(n) else "%,d".format(Locale.US, n)

/** "+13%" / "−5%" / "±0%", or null without a baseline. */
fun signedPercent(current: Long, previous: Long): String? = percentChange(current, previous)?.let {
    when {
        it > 0 -> "+$it%"
        it < 0 -> "−${-it}%"
        else -> "±0%"
    }
}
