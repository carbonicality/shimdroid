package dev.openanalytics.wear.tile

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dev.openanalytics.wear.config.Analytics
import dev.openanalytics.wear.data.StatsSnapshot
import java.time.Instant

/**
 * Text images for the last few layouts, by resources version. The system asks
 * for resources separately from (and just after) the layout.
 */
private object ResourceCache {
    private const val MAX = 8
    private val map = object : LinkedHashMap<String, ResourceBuilders.Resources>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResourceBuilders.Resources>?) = size > MAX
    }

    @Synchronized fun put(fonts: TileText): String {
        val v = fonts.version()
        map[v] = fonts.resources(v)
        return v
    }

    @Synchronized fun get(version: String): ResourceBuilders.Resources? = map[version]
}

/** Base for the three analytics tiles; each only supplies its layout. */
abstract class AnalyticsTileService : TileService() {
    protected abstract fun Tiles.render(
        context: Context,
        device: DeviceParameters,
        snapshot: StatsSnapshot,
        stale: Boolean,
    ): LayoutElementBuilders.LayoutElement

    private fun build(device: DeviceParameters, content: TileContent): Pair<LayoutElementBuilders.LayoutElement, TileText> {
        val fonts = TileText(applicationContext)
        val tiles = Tiles(fonts)
        val root = when (content) {
            is TileContent.Stats -> tiles.render(applicationContext, device, content.snapshot, content.stale)
            is TileContent.Message -> tiles.message(applicationContext, device, content.title, content.body)
        }
        return root to fonts
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val (root, fonts) = build(requestParams.deviceConfiguration, TileData.load(applicationContext))
            TileBuilders.Tile.Builder()
                .setResourcesVersion(ResourceCache.put(fonts))
                .setFreshnessIntervalMillis(15 * 60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture {
            ResourceCache.get(requestParams.version) ?: run {
                // Process restarted between the two requests: rebuild from the cached snapshot.
                val snapshot = Analytics.lastSnapshot(applicationContext)
                val content = if (snapshot != null) TileContent.Stats(snapshot, stale = false) else TileData.load(applicationContext)
                build(requestParams.deviceConfiguration, content).second.resources(requestParams.version)
            }
        }
}

/** Tile 1: minute ring, past 30 minutes, right now. */
class ActiveTileService : AnalyticsTileService() {
    override fun Tiles.render(context: Context, device: DeviceParameters, snapshot: StatsSnapshot, stale: Boolean) =
        active(context, device, snapshot, stale)
}

/** Tile 2: today so far vs yesterday. */
class TodayVsYesterdayTileService : AnalyticsTileService() {
    override fun Tiles.render(context: Context, device: DeviceParameters, snapshot: StatsSnapshot, stale: Boolean) =
        todayVsYesterday(context, device, snapshot, stale)
}

/** Tile 3: today's total with an hourly chart. */
class TodayTotalTileService : AnalyticsTileService() {
    override fun Tiles.render(context: Context, device: DeviceParameters, snapshot: StatsSnapshot, stale: Boolean) =
        todayTotal(context, device, snapshot, stale)
}

/**
 * Tile 4: US world clock. No network: it ships an hour of per-minute timeline
 * entries (sharing one set of glyph images) so the system can flip minutes
 * without waking the app.
 */
class UsClockTileService : TileService() {
    private fun build(device: DeviceParameters, startMs: Long): Pair<TimelineBuilders.Timeline, TileText> {
        val fonts = TileText(applicationContext)
        val tiles = Tiles(fonts)
        val timeline = TimelineBuilders.Timeline.Builder()
        for (i in 0 until 60) {
            val t = startMs + i * 60_000L
            timeline.addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(tiles.usClock(applicationContext, device, Instant.ofEpochMilli(t)))
                            .build(),
                    )
                    .setValidity(
                        TimelineBuilders.TimeInterval.Builder().setStartMillis(t).setEndMillis(t + 60_000L).build(),
                    )
                    .build(),
            )
        }
        return timeline.build() to fonts
    }

    private fun minuteNow() = System.currentTimeMillis() / 60_000L * 60_000L

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val (timeline, fonts) = build(requestParams.deviceConfiguration, minuteNow())
            TileBuilders.Tile.Builder()
                .setResourcesVersion(ResourceCache.put(fonts))
                .setFreshnessIntervalMillis(55 * 60_000L)
                .setTileTimeline(timeline)
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture {
            ResourceCache.get(requestParams.version)
                ?: build(requestParams.deviceConfiguration, minuteNow()).second.resources(requestParams.version)
        }
}
