package dev.openanalytics.wear.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/** Read-only client for one GA4 property, via the Analytics Data API. */
class AnalyticsClient(
    propertyId: String,
    private val tokens: AccessTokenSource,
    private val http: Http = Http.Default,
) {
    val propertyId: String = normalizePropertyId(propertyId)

    private val base = "$DATA_API/properties/${this.propertyId}"

    suspend fun realtime(): Realtime = withContext(Dispatchers.IO) {
        coroutineScope {
            val total = async { runRealtime(Reports.realtimeTotal()) }
            val perMinute = async { runRealtime(Reports.realtimePerMinute()) }
            val screens = async { runRealtime(Reports.realtimeTopScreens()) }
            val now = async { runRealtime(Reports.realtimeWindow(1)) }
            Realtime(
                activeUsers = Reports.total(total.await()),
                nowUsers = Reports.total(now.await()),
                perMinute = Reports.perMinute(perMinute.await()),
                topScreens = Reports.ranked(screens.await()),
            )
        }
    }

    /** Unique users over the last [minutes] minutes (1..30). */
    suspend fun activeInLast(minutes: Int): Long = withContext(Dispatchers.IO) {
        Reports.total(runRealtime(Reports.realtimeWindow(minutes)))
    }

    suspend fun summary(today: LocalDate = LocalDate.now()): Summary = withContext(Dispatchers.IO) {
        val res = tokens.withToken { http.postJson("$base:batchRunReports", Reports.summaryBatch(), it) }
        Reports.summary(res, today)
    }

    private fun runRealtime(body: JSONObject) =
        tokens.withToken { http.postJson("$base:runRealtimeReport", body, it) }

    companion object {
        const val DATA_API = "https://analyticsdata.googleapis.com/v1beta"
        const val ADMIN_API = "https://analyticsadmin.googleapis.com/v1beta"

        /** All GA4 properties the signed-in user can read (Admin API). */
        suspend fun listProperties(tokens: AccessTokenSource, http: Http = Http.Default): List<GaProperty> =
            withContext(Dispatchers.IO) { Properties.list(http, tokens) }

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
