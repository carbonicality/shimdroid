package dev.openanalytics.wear.data

import org.json.JSONObject

/**
 * The handful of numbers the tile shows. Small and JSON-serialisable so the app
 * can cache it for the tile, and the tile can fall back to it when offline.
 */
data class StatsSnapshot(
    val activeUsers: Long,
    val todayUsers: Long,
    val yesterdayUsers: Long,
    val todayViews: Long,
    val last7Users: Long,
    val prev7Users: Long,
    val updatedAtMs: Long,
) {
    fun toJson(): String = JSONObject()
        .put("activeUsers", activeUsers)
        .put("todayUsers", todayUsers)
        .put("yesterdayUsers", yesterdayUsers)
        .put("todayViews", todayViews)
        .put("last7Users", last7Users)
        .put("prev7Users", prev7Users)
        .put("updatedAtMs", updatedAtMs)
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
            )
        }.getOrNull()
    }
}
