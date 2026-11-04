package dev.openanalytics.wear.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the tiles show. Small and JSON-serialisable so the app can cache
 * it for the tiles, and the tiles can fall back to it when offline.
 */
data class StatsSnapshot(
    val activeUsers: Long,
    val todayUsers: Long,
    val yesterdayUsers: Long,
    val todayViews: Long,
    val last7Users: Long,
    val prev7Users: Long,
    val updatedAtMs: Long,
    val nowUsers: Long = 0,
    val todaySessions: Long = 0,
    /** Oldest first, [REALTIME_MINUTES] entries. */
    val perMinute: List<Long> = List(REALTIME_MINUTES) { 0L },
    /** Hour 0..23 of today. */
    val hourly: List<Long> = List(24) { 0L },
) {
    fun toJson(): String = JSONObject()
        .put("activeUsers", activeUsers)
        .put("todayUsers", todayUsers)
        .put("yesterdayUsers", yesterdayUsers)
        .put("todayViews", todayViews)
        .put("last7Users", last7Users)
        .put("prev7Users", prev7Users)
        .put("updatedAtMs", updatedAtMs)
        .put("nowUsers", nowUsers)
        .put("todaySessions", todaySessions)
        .put("perMinute", JSONArray(perMinute))
        .put("hourly", JSONArray(hourly))
        .toString()

    companion object {
        fun of(realtime: Realtime, summary: Summary, atMs: Long) = StatsSnapshot(
            activeUsers = realtime.activeUsers,
            todayUsers = summary.today.activeUsers,
            yesterdayUsers = summary.yesterday.activeUsers,
            todayViews = summary.today.views,
            last7Users = summary.last7.activeUsers,
            prev7Users = summary.prev7.activeUsers,
            updatedAtMs = atMs,
            nowUsers = realtime.nowUsers,
            todaySessions = summary.today.sessions,
            perMinute = realtime.perMinute,
            hourly = summary.hourlyToday,
        )

        fun fromJson(json: String?): StatsSnapshot? = runCatching {
            val o = JSONObject(json ?: return null)
            StatsSnapshot(
                activeUsers = o.getLong("activeUsers"),
                todayUsers = o.getLong("todayUsers"),
                yesterdayUsers = o.getLong("yesterdayUsers"),
                todayViews = o.optLong("todayViews"),
                last7Users = o.getLong("last7Users"),
                prev7Users = o.getLong("prev7Users"),
                updatedAtMs = o.getLong("updatedAtMs"),
                nowUsers = o.optLong("nowUsers"),
                todaySessions = o.optLong("todaySessions"),
                perMinute = longs(o.optJSONArray("perMinute"), REALTIME_MINUTES),
                hourly = longs(o.optJSONArray("hourly"), 24),
            )
        }.getOrNull()

        private fun longs(arr: JSONArray?, size: Int): List<Long> =
            List(size) { i -> arr?.optLong(i) ?: 0L }
    }
}
