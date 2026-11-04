package dev.openanalytics.wear.data

/** Supplies OAuth access tokens with the analytics.readonly scope. */
interface AccessTokenSource {
    /** Blocking; call off the main thread. */
    fun token(): String

    /** Drops a token the API rejected so the next [token] call fetches a fresh one. */
    fun invalidate(token: String)
}

const val ANALYTICS_READONLY_SCOPE = "https://www.googleapis.com/auth/analytics.readonly"

/** Runs [call] with a token, retrying once with a fresh token if the API says 401. */
internal fun <T> AccessTokenSource.withToken(call: (String) -> T): T {
    val t = token()
    return try {
        call(t)
    } catch (e: ApiException) {
        if (e.status != 401) throw e
        invalidate(t)
        call(token())
    }
}
