package dev.openanalytics.wear.data

import org.json.JSONObject
import java.net.URLEncoder

/** A GA4 property the signed-in user can read. */
data class GaProperty(val id: String, val name: String, val account: String)

internal object Properties {
    /** Flattens an Admin API accountSummaries.list page. */
    fun parse(page: JSONObject): List<GaProperty> {
        val out = mutableListOf<GaProperty>()
        val accounts = page.optJSONArray("accountSummaries") ?: return out
        for (i in 0 until accounts.length()) {
            val acc = accounts.optJSONObject(i) ?: continue
            val accName = acc.optString("displayName")
            val props = acc.optJSONArray("propertySummaries") ?: continue
            for (j in 0 until props.length()) {
                val p = props.optJSONObject(j) ?: continue
                val id = p.optString("property").removePrefix("properties/")
                if (id.isEmpty()) continue
                out += GaProperty(id, p.optString("displayName").ifBlank { id }, accName)
            }
        }
        return out
    }

    fun list(http: Http, tokens: AccessTokenSource): List<GaProperty> {
        val all = mutableListOf<GaProperty>()
        var pageToken: String? = null
        do {
            val url = "${AnalyticsClient.ADMIN_API}/accountSummaries?pageSize=200" +
                (pageToken?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: "")
            val page = tokens.withToken { http.get(url, it) }
            all += parse(page)
            pageToken = page.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        return all
    }
}
