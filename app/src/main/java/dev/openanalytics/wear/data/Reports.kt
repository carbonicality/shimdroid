package dev.openanalytics.wear.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Request builders and response parsers for the GA4 Data API. */
internal object Reports {
    const val TODAY = "today"
    const val YESTERDAY = "yesterday"
    const val LAST_7 = "last7"
    const val PREV_7 = "prev7"

    private val GA_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE // yyyyMMdd

    // ---- request builders ----

    fun dateRange(start: String, end: String, name: String? = null): JSONObject =
        JSONObject().put("startDate", start).put("endDate", end).apply { if (name != null) put("name", name) }

    fun metrics(vararg names: String) = JSONArray().apply { names.forEach { put(JSONObject().put("name", it)) } }
    fun dimensions(vararg names: String) = JSONArray().apply { names.forEach { put(JSONObject().put("name", it)) } }

    fun orderByMetricDesc(metric: String) =
        JSONArray().put(JSONObject().put("metric", JSONObject().put("metricName", metric)).put("desc", true))

    fun orderByDimension(dim: String) =
        JSONArray().put(JSONObject().put("dimension", JSONObject().put("dimensionName", dim)))

    fun realtimeTotal() = JSONObject().put("metrics", metrics("activeUsers"))

    fun realtimePerMinute() = JSONObject()
        .put("dimensions", dimensions("minutesAgo"))
        .put("metrics", metrics("activeUsers"))
        .put("limit", REALTIME_MINUTES)

    fun realtimeTopScreens(limit: Int = 5) = JSONObject()
        .put("dimensions", dimensions("unifiedScreenName"))
        .put("metrics", metrics("activeUsers"))
        .put("orderBys", orderByMetricDesc("activeUsers"))
        .put("limit", limit)

    fun summaryBatch(limit: Int = 5): JSONObject {
        val last7 = dateRange("7daysAgo", "yesterday", LAST_7)
        val periods = JSONObject()
            .put(
                "dateRanges",
                JSONArray()
                    .put(dateRange("today", "today", TODAY))
                    .put(dateRange("yesterday", "yesterday", YESTERDAY))
                    .put(last7)
                    .put(dateRange("14daysAgo", "8daysAgo", PREV_7)),
            )
            .put("metrics", metrics("activeUsers", "newUsers", "sessions", "screenPageViews"))
        val daily = JSONObject()
            .put("dateRanges", JSONArray().put(last7))
            .put("dimensions", dimensions("date"))
            .put("metrics", metrics("activeUsers"))
            .put("orderBys", orderByDimension("date"))
        val pages = JSONObject()
            .put("dateRanges", JSONArray().put(last7))
            .put("dimensions", dimensions("unifiedScreenName"))
            .put("metrics", metrics("screenPageViews"))
            .put("orderBys", orderByMetricDesc("screenPageViews"))
            .put("limit", limit)
        val countries = JSONObject()
            .put("dateRanges", JSONArray().put(last7))
            .put("dimensions", dimensions("country"))
            .put("metrics", metrics("activeUsers"))
            .put("orderBys", orderByMetricDesc("activeUsers"))
            .put("limit", limit)
        return JSONObject().put("requests", JSONArray().put(periods).put(daily).put(pages).put(countries))
    }

    // ---- response parsers ----

    fun rows(report: JSONObject): List<JSONObject> {
        val arr = report.optJSONArray("rows") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    fun dim(row: JSONObject, i: Int): String =
        row.optJSONArray("dimensionValues")?.optJSONObject(i)?.optString("value").orEmpty()

    fun metric(row: JSONObject, i: Int): Long {
        val v = row.optJSONArray("metricValues")?.optJSONObject(i)?.optString("value") ?: return 0
        return v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong() ?: 0
    }

    private fun headerIndex(report: JSONObject, key: String, name: String): Int {
        val headers = report.optJSONArray(key) ?: return -1
        return (0 until headers.length()).firstOrNull { headers.optJSONObject(it)?.optString("name") == name } ?: -1
    }

    /** First metric of the first row, or 0 when GA returned no rows (no traffic). */
    fun total(report: JSONObject): Long = rows(report).firstOrNull()?.let { metric(it, 0) } ?: 0

    /** Per-minute series, oldest first, gaps filled with 0. */
    fun perMinute(report: JSONObject, minutes: Int = REALTIME_MINUTES): List<Long> {
        val out = LongArray(minutes)
        for (row in rows(report)) {
            val ago = dim(row, 0).toIntOrNull() ?: continue
            if (ago in 0 until minutes) out[minutes - 1 - ago] = metric(row, 0)
        }
        return out.toList()
    }

    fun ranked(report: JSONObject): List<RankedItem> =
        rows(report).map { RankedItem(dim(it, 0).ifBlank { "(not set)" }, metric(it, 0)) }

    /** Splits a multi-date-range report into one [MetricSet] per named range. */
    fun periods(report: JSONObject): Map<String, MetricSet> {
        val idx = headerIndex(report, "dimensionHeaders", "dateRange").takeIf { it >= 0 } ?: 0
        val m = { name: String -> headerIndex(report, "metricHeaders", name) }
        val (users, newUsers, sessions, views) = listOf("activeUsers", "newUsers", "sessions", "screenPageViews").map(m)
        return rows(report).associate { row ->
            val get = { i: Int -> if (i >= 0) metric(row, i) else 0L }
            dim(row, idx) to MetricSet(get(users), get(newUsers), get(sessions), get(views))
        }
    }

    /** Daily series aligned to [dates]; days GA omitted (no traffic) become 0. */
    fun daily(report: JSONObject, dates: List<LocalDate>): List<DailyPoint> {
        val byDate = rows(report).associate { dim(it, 0) to metric(it, 0) }
        return dates.map { DailyPoint(it, byDate[it.format(GA_DATE)] ?: 0) }
    }

    fun summary(batch: JSONObject, today: LocalDate): Summary {
        val reports = batch.optJSONArray("reports") ?: JSONArray()
        val r = { i: Int -> reports.optJSONObject(i) ?: JSONObject() }
        val p = periods(r(0))
        val dates = (7 downTo 1).map { today.minusDays(it.toLong()) }
        return Summary(
            today = p[TODAY] ?: MetricSet(),
            yesterday = p[YESTERDAY] ?: MetricSet(),
            last7 = p[LAST_7] ?: MetricSet(),
            prev7 = p[PREV_7] ?: MetricSet(),
            daily = daily(r(1), dates),
            topPages = ranked(r(2)),
            topCountries = ranked(r(3)),
        )
    }
}
