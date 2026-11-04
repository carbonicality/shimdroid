package dev.openanalytics.wear.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal JSON-over-HTTP transport. An interface so tests can fake it. */
interface Http {
    fun request(method: String, url: String, headers: Map<String, String>, body: String?): JSONObject

    fun postJson(url: String, body: JSONObject, bearer: String): JSONObject =
        request(
            "POST", url,
            mapOf("Authorization" to "Bearer $bearer", "Content-Type" to "application/json; charset=utf-8"),
            body.toString(),
        )

    fun get(url: String, bearer: String): JSONObject =
        request("GET", url, mapOf("Authorization" to "Bearer $bearer"), null)

    fun postForm(url: String, params: Map<String, String>): JSONObject =
        request(
            "POST", url,
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            params.entries.joinToString("&") { (k, v) ->
                URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
            },
        )

    companion object Default : Http {
        private const val TIMEOUT_MS = 20_000

        override fun request(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String?,
        ): JSONObject {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw ApiException(status, describeError(status, text))
                return if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (e: IOException) {
                throw IOException("Network error: ${e.message ?: e.javaClass.simpleName}", e)
            } finally {
                conn.disconnect()
            }
        }

        internal fun describeError(status: Int, text: String): String {
            val parsed = runCatching { JSONObject(text) }.getOrNull()
            val msg = parsed?.let { root ->
                when (val err = root.opt("error")) {
                    is JSONObject -> err.optString("message")
                    is String -> root.optString("error_description").ifBlank { err }
                    else -> null
                }
            }?.takeIf { it.isNotBlank() } ?: text.take(200).ifBlank { "no details" }
            val hint = when (status) {
                401 -> " (sign in again)"
                403 -> " (your account needs at least Viewer access to this property, and the " +
                    "Google Analytics Data + Admin APIs must be enabled in your Cloud project)"
                429 -> " (GA quota exhausted, try again later)"
                else -> ""
            }
            return "HTTP $status: $msg$hint"
        }
    }
}
