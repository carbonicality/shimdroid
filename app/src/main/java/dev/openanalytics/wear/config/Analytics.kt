package dev.openanalytics.wear.config

import android.content.Context
import androidx.wear.tiles.TileService
import dev.openanalytics.wear.data.AnalyticsClient
import dev.openanalytics.wear.data.StatsSnapshot
import dev.openanalytics.wear.data.TokenProvider
import dev.openanalytics.wear.tile.StatsTileService

/**
 * Process-wide access to the GA client and the cached [StatsSnapshot], shared by
 * the app UI and the tile (so they share one cached access token too).
 */
object Analytics {
    private const val PREFS = "openanalytics"
    private const val KEY_SNAPSHOT = "snapshot"

    private var cached: Pair<AnalyticsConfig, AnalyticsClient>? = null

    /** Loads config (throws ConfigException if missing) and returns a client for it. */
    @Synchronized
    fun client(context: Context): AnalyticsClient {
        val cfg = ConfigLoader.load(context.applicationContext)
        cached?.let { (oldCfg, client) ->
            if (oldCfg.propertyId == cfg.propertyId &&
                oldCfg.serviceAccount.clientEmail == cfg.serviceAccount.clientEmail
            ) return client
        }
        val client = AnalyticsClient(cfg.propertyId, TokenProvider(cfg.serviceAccount))
        cached = cfg to client
        return client
    }

    fun lastSnapshot(context: Context): StatsSnapshot? =
        StatsSnapshot.fromJson(prefs(context).getString(KEY_SNAPSHOT, null))

    fun saveSnapshot(context: Context, snapshot: StatsSnapshot) {
        prefs(context).edit().putString(KEY_SNAPSHOT, snapshot.toJson()).apply()
    }

    /** Asks the system to re-render the tile (e.g. after the app fetched fresh data). */
    fun requestTileUpdate(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(StatsTileService::class.java) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
