package dev.openanalytics.wear.tile

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dev.openanalytics.wear.auth.ConsentRequiredException
import dev.openanalytics.wear.auth.NotSignedInException
import dev.openanalytics.wear.config.Analytics
import dev.openanalytics.wear.config.NoPropertyException
import dev.openanalytics.wear.data.StatsSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

/**
 * "openAnalytics" tile: realtime active users plus today / 7-day users with
 * change indicators. Tap the numbers to refresh, the chip to open the app.
 */
class StatsTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val forced = requestParams.currentState.lastClickableId == ID_REFRESH
            val content = loadContent(applicationContext, forced)
            val root = tileLayout(applicationContext, requestParams.deviceConfiguration, content)
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture {
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    private suspend fun loadContent(context: Context, forced: Boolean): TileContent {
        val cached = Analytics.lastSnapshot(context)
        val now = System.currentTimeMillis()
        // The app pushes fresh snapshots while open; don't re-fetch those.
        if (!forced && cached != null && now - cached.updatedAtMs < REUSE_MS) {
            return TileContent.Stats(cached, stale = false)
        }
        return try {
            val client = Analytics.client(context)
            val snapshot = withTimeout(FETCH_TIMEOUT_MS) {
                coroutineScope {
                    val rt = async { client.realtime() }
                    val summary = async { client.summary() }
                    StatsSnapshot.of(rt.await(), summary.await(), now)
                }
            }
            Analytics.saveSnapshot(context, snapshot)
            TileContent.Stats(snapshot, stale = false)
        } catch (e: NotSignedInException) {
            TileContent.Message("Not signed in", "Open the app to sign in")
        } catch (e: NoPropertyException) {
            TileContent.Message("No property", "Open the app to choose one")
        } catch (e: ConsentRequiredException) {
            TileContent.Message("Access needed", "Open the app to allow")
        } catch (e: Exception) {
            if (cached != null) TileContent.Stats(cached, stale = true)
            else TileContent.Message("Couldn't load", "Tap to retry")
        }
    }

    companion object {
        const val RESOURCES_VERSION = "1"
        const val ID_REFRESH = "refresh"

        /** How often the system re-requests the tile. */
        private const val FRESHNESS_MS = 15 * 60_000L
        /** Reuse a snapshot this recent instead of fetching. */
        private const val REUSE_MS = 2 * 60_000L
        private const val FETCH_TIMEOUT_MS = 12_000L
    }
}
