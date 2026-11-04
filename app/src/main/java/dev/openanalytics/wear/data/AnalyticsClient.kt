package dev.openanalytics.wear.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Read-only client for one GA4 property, via the Analytics Data API. */
class AnalyticsClient(
    propertyId: String,
    private val tokens: TokenProvider,
    private val http: Http = Http.Default,
) {
    val propertyId: String = normalizePropertyId(propertyId)

    private val base = "$DATA_API/properties/${this.propertyId}"

    suspend fun realtime(): Realtime = withContext(Dispatchers.IO) {
        coroutineScope {
            val total = async { runRealtime(Reports.realtimeTotal()) }
            val perMinute = async { runRealtime(Reports.realtimePerMinute()) }
            val screens = async { runRealtime(Reports.realtimeTopScreens()) }
            Realtime(
                activeUsers = Reports.total(total.await()),
                perMinute = Reports.perMinute(perMinute.await()),
                topScreens = Reports.ranked(screens.await()),
            )
        }
    }

    suspend fun summary(today: LocalDate = LocalDate.now()): Summary = withContext(Dispatchers.IO) {
        val res = http.postJson("$base:batchRunReports", Reports.summaryBatch(), tokens.accessToken())
        Reports.summary(res, today)
    }

    /** Property display name via the Admin API; null if unavailable (API not enabled, etc.). */
    suspend fun propertyName(): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.get("$ADMIN_API/properties/$propertyId", tokens.accessToken())
                .optString("displayName").ifBlank { null }
        }.getOrNull()
    }

    private fun runRealtime(body: org.json.JSONObject) =
        http.postJson("$base:runRealtimeReport", body, tokens.accessToken())

    companion object {
        const val DATA_API = "https://analyticsdata.googleapis.com/v1beta"
        const val ADMIN_API = "https://analyticsadmin.googleapis.com/v1beta"

        /** Accepts "123456789" or "properties/123456789". */
        fun normalizePropertyId(raw: String): String {
            val id = raw.trim().removePrefix("properties/").trim()
            if (id.isEmpty() || !id.all(Char::isDigit)) {
                throw ConfigException("Invalid GA4 property ID \"$raw\" (expected digits, e.g. 123456789)")
            }
            return id
        }
    }
}
