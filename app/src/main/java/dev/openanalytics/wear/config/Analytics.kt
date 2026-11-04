package dev.openanalytics.wear.config

import android.content.Context
import androidx.wear.tiles.TileService
import dev.openanalytics.wear.auth.GoogleAuth
import dev.openanalytics.wear.data.AnalyticsClient
import dev.openanalytics.wear.data.ConfigException
import dev.openanalytics.wear.data.GaProperty
import dev.openanalytics.wear.data.StatsSnapshot
import dev.openanalytics.wear.tile.ActiveTileService
import dev.openanalytics.wear.tile.TodayTotalTileService
import dev.openanalytics.wear.tile.TodayVsYesterdayTileService

class NoPropertyException : ConfigException("Choose a GA4 property in the app")

/**
 * Shared state for the app UI and the tile: the chosen property, the cached
 * [StatsSnapshot], and a GA client for the signed-in account.
 */
object Analytics {
    private const val PREFS = "openanalytics"
    private const val KEY_SNAPSHOT = "snapshot"
    private const val KEY_PROPERTY_ID = "propertyId"
    private const val KEY_PROPERTY_NAME = "propertyName"

    /** Throws NotSignedInException / NoPropertyException when setup isn't finished. */
    fun client(context: Context): AnalyticsClient {
        val tokens = GoogleAuth.tokenSource(context)
        val property = selectedProperty(context) ?: throw NoPropertyException()
        return AnalyticsClient(property.id, tokens)
    }

    fun selectedProperty(context: Context): GaProperty? {
        val p = prefs(context)
        val id = p.getString(KEY_PROPERTY_ID, null) ?: return null
        return GaProperty(id, p.getString(KEY_PROPERTY_NAME, null) ?: id, "")
    }

    fun selectProperty(context: Context, property: GaProperty?) {
        prefs(context).edit().apply {
            if (property == null) {
                remove(KEY_PROPERTY_ID); remove(KEY_PROPERTY_NAME)
            } else {
                putString(KEY_PROPERTY_ID, property.id); putString(KEY_PROPERTY_NAME, property.name)
            }
            remove(KEY_SNAPSHOT) // numbers belonged to the old property
        }.apply()
        requestTileUpdate(context)
    }

    fun lastSnapshot(context: Context): StatsSnapshot? =
        StatsSnapshot.fromJson(prefs(context).getString(KEY_SNAPSHOT, null))

    fun saveSnapshot(context: Context, snapshot: StatsSnapshot) {
        prefs(context).edit().putString(KEY_SNAPSHOT, snapshot.toJson()).apply()
    }

    fun signOut(context: Context) {
        GoogleAuth.signOut(context)
        selectProperty(context, null)
    }

    /** Asks the system to re-render the analytics tiles (e.g. after the app fetched fresh data). */
    fun requestTileUpdate(context: Context) {
        val updater = runCatching { TileService.getUpdater(context) }.getOrNull() ?: return
        for (tile in listOf(ActiveTileService::class.java, TodayVsYesterdayTileService::class.java, TodayTotalTileService::class.java)) {
            runCatching { updater.requestUpdate(tile) }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
