package dev.openanalytics.wear.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DataLayerTest {

    @Test
    fun normalizesPropertyId() {
        assertEquals("123456789", AnalyticsClient.normalizePropertyId(" properties/123456789 "))
        assertEquals("42", AnalyticsClient.normalizePropertyId("42"))
    }

    @Test(expected = ConfigException::class)
    fun rejectsMeasurementId() {
        AnalyticsClient.normalizePropertyId("G-ABC123")
    }

    private fun row(dims: List<String>, metrics: List<String>) = JSONObject()
        .put("dimensionValues", JSONArray().apply { dims.forEach { put(JSONObject().put("value", it)) } })
        .put("metricValues", JSONArray().apply { metrics.forEach { put(JSONObject().put("value", it)) } })

    private fun headers(vararg names: String) = JSONArray().apply { names.forEach { put(JSONObject().put("name", it)) } }

    @Test
    fun parsesPerMinuteOldestFirstWithGaps() {
        val report = JSONObject().put(
            "rows",
            JSONArray().put(row(listOf("00"), listOf("5"))).put(row(listOf("02"), listOf("3"))),
        )
        val series = Reports.perMinute(report)
        assertEquals(REALTIME_MINUTES, series.size)
        assertEquals(5L, series.last())
        assertEquals(0L, series[REALTIME_MINUTES - 2])
        assertEquals(3L, series[REALTIME_MINUTES - 3])
    }

    @Test
    fun emptyReportMeansZero() {
        assertEquals(0L, Reports.total(JSONObject()))
        assertEquals(emptyList<RankedItem>(), Reports.ranked(JSONObject()))
    }

    @Test
    fun parsesSummaryBatch() {
        val periods = JSONObject()
            .put("dimensionHeaders", headers("dateRange"))
            .put("metricHeaders", headers("activeUsers", "newUsers", "sessions", "screenPageViews"))
            .put(
                "rows",
                JSONArray()
                    .put(row(listOf("today"), listOf("10", "4", "12", "30")))
                    .put(row(listOf("yesterday"), listOf("20", "5", "25", "60")))
                    .put(row(listOf("last7"), listOf("140", "50", "170", "400")))
                    .put(row(listOf("prev7"), listOf("100", "40", "120", "300"))),
            )
        val daily = JSONObject().put(
            "rows",
            JSONArray().put(row(listOf("20261028"), listOf("21"))).put(row(listOf("20261103"), listOf("19"))),
        )
        val pages = JSONObject().put("rows", JSONArray().put(row(listOf("Home"), listOf("250"))))
        val countries = JSONObject().put("rows", JSONArray().put(row(listOf("United Kingdom"), listOf("90"))))
        val batch = JSONObject().put("reports", JSONArray().put(periods).put(daily).put(pages).put(countries))

        val s = Reports.summary(batch, LocalDate.of(2026, 11, 4))
        assertEquals(MetricSet(10, 4, 12, 30), s.today)
        assertEquals(20L, s.yesterday.activeUsers)
        assertEquals(140L, s.last7.activeUsers)
        assertEquals(100L, s.prev7.activeUsers)
        assertEquals(7, s.daily.size)
        assertEquals(LocalDate.of(2026, 10, 28), s.daily.first().date)
        assertEquals(21L, s.daily.first().users)
        assertEquals(19L, s.daily.last().users)
        assertEquals(0L, s.daily[3].users)
        assertEquals(listOf(RankedItem("Home", 250)), s.topPages)
        assertEquals("United Kingdom", s.topCountries.single().label)
    }

    @Test
    fun summaryRequestUsesAtMostFourDateRanges() {
        val reqs = Reports.summaryBatch().getJSONArray("requests")
        assertEquals(4, reqs.length())
        for (i in 0 until reqs.length()) assertTrue(reqs.getJSONObject(i).getJSONArray("dateRanges").length() <= 4)
    }

    @Test
    fun parsesAccountSummaries() {
        val page = JSONObject().put(
            "accountSummaries",
            JSONArray()
                .put(
                    JSONObject().put("displayName", "My Company").put(
                        "propertySummaries",
                        JSONArray()
                            .put(JSONObject().put("property", "properties/111").put("displayName", "Website"))
                            .put(JSONObject().put("property", "properties/222").put("displayName", "App")),
                    ),
                )
                .put(JSONObject().put("displayName", "Empty account")),
        )
        assertEquals(
            listOf(GaProperty("111", "Website", "My Company"), GaProperty("222", "App", "My Company")),
            Properties.parse(page),
        )
    }

    @Test
    fun listsPropertiesAcrossPages() {
        val http = object : Http {
            override fun request(method: String, url: String, headers: Map<String, String>, body: String?): JSONObject {
                val acc = { id: String ->
                    JSONArray().put(
                        JSONObject().put("displayName", "A").put(
                            "propertySummaries",
                            JSONArray().put(JSONObject().put("property", "properties/$id").put("displayName", "P$id")),
                        ),
                    )
                }
                return if ("pageToken=" in url) JSONObject().put("accountSummaries", acc("2"))
                else JSONObject().put("accountSummaries", acc("1")).put("nextPageToken", "next")
            }
        }
        val tokens = object : AccessTokenSource {
            override fun token() = "t"
            override fun invalidate(token: String) {}
        }
        assertEquals(listOf("1", "2"), Properties.list(http, tokens).map { it.id })
    }

    @Test
    fun retriesOnceWithFreshTokenAfter401() {
        val issued = ArrayDeque(listOf("stale", "fresh"))
        val invalidated = mutableListOf<String>()
        val tokens = object : AccessTokenSource {
            override fun token() = issued.removeFirst()
            override fun invalidate(token: String) { invalidated += token }
        }
        val used = mutableListOf<String>()
        val result = tokens.withToken { t ->
            used += t
            if (t == "stale") throw ApiException(401, "expired") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("stale", "fresh"), used)
        assertEquals(listOf("stale"), invalidated)
    }

    @Test(expected = ApiException::class)
    fun doesNotRetryOtherErrors() {
        val tokens = object : AccessTokenSource {
            override fun token() = "t"
            override fun invalidate(token: String) = error("should not invalidate")
        }
        tokens.withToken<Unit> { throw ApiException(403, "forbidden") }
    }

    @Test
    fun errorMessagesIncludeHints() {
        val msg = Http.describeError(403, """{"error":{"code":403,"message":"User does not have sufficient permissions"}}""")
        assertTrue(msg.startsWith("HTTP 403: User does not have sufficient permissions"))
        assertTrue(msg.contains("Viewer access"))
        val oauth = Http.describeError(400, """{"error":"invalid_grant","error_description":"Invalid JWT Signature."}""")
        assertEquals("HTTP 400: Invalid JWT Signature.", oauth)
    }
}

class StatsSnapshotTest {
    @Test
    fun roundTripsThroughJson() {
        val s = StatsSnapshot(7, 120, 100, 340, 900, 1000, 1_762_000_000_000)
        assertEquals(s, StatsSnapshot.fromJson(s.toJson()))
    }

    @Test
    fun badJsonGivesNull() {
        assertEquals(null, StatsSnapshot.fromJson(null))
        assertEquals(null, StatsSnapshot.fromJson("{}"))
        assertEquals(null, StatsSnapshot.fromJson("not json"))
    }

    @Test
    fun builtFromReports() {
        val rt = Realtime(5, List(REALTIME_MINUTES) { 0L }, emptyList())
        val sum = Summary(
            MetricSet(10, 1, 2, 30), MetricSet(8), MetricSet(70), MetricSet(60),
            emptyList(), emptyList(), emptyList(),
        )
        assertEquals(StatsSnapshot(5, 10, 8, 30, 70, 60, 42), StatsSnapshot.of(rt, sum, 42))
    }
}
