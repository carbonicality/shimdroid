package dev.openanalytics.wear.data

import java.time.LocalDate

/** Core traffic metrics for one date range. */
data class MetricSet(
    val activeUsers: Long = 0,
    val newUsers: Long = 0,
    val sessions: Long = 0,
    val views: Long = 0,
)

data class RankedItem(val label: String, val value: Long)

data class DailyPoint(val date: LocalDate, val users: Long)

/** Realtime snapshot (GA4 realtime window = last 30 minutes). */
data class Realtime(
    val activeUsers: Long,
    /** Active users per minute, oldest first. Always [REALTIME_MINUTES] entries. */
    val perMinute: List<Long>,
    val topScreens: List<RankedItem>,
)

data class Summary(
    val today: MetricSet,
    val yesterday: MetricSet,
    /** Last 7 complete days (7daysAgo..yesterday). */
    val last7: MetricSet,
    /** The 7 days before that (14daysAgo..8daysAgo). */
    val prev7: MetricSet,
    /** Daily active users for the last 7 complete days, oldest first. */
    val daily: List<DailyPoint>,
    val topPages: List<RankedItem>,
    val topCountries: List<RankedItem>,
)

const val REALTIME_MINUTES = 30

/** Bad or missing local configuration (credentials / property ID). */
open class ConfigException(message: String) : Exception(message)

/** An error response from a Google API. */
class ApiException(val status: Int, message: String) : Exception(message)
