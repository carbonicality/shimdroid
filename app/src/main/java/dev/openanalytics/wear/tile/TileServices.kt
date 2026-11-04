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
import dev.openanalytics.wear.data.StatsSnapshot
import java.time.Instant

private const val RESOURCES_VERSION = "1"

private fun resources(): ListenableFuture<ResourceBuilders.Resources> =
    SuspendToFutureAdapter.launchFuture {
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

/** Base for the three analytics tiles; each only supplies its layout. */
abstract class AnalyticsTileService : TileService() {
    protected abstract fun render(
        context: Context,
        device: DeviceParameters,
        snapshot: StatsSnapshot,
        stale: Boolean,
    ): LayoutElementBuilders.LayoutElement

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val device = requestParams.deviceConfiguration
            val root = when (val c = TileData.load(applicationContext)) {
                is TileContent.Stats -> render(applicationContext, device, c.snapshot, c.stale)
                is TileContent.Message -> Tiles.message(applicationContext, device, c.title, c.body)
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(15 * 60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) = resources()
}

/** Tile 1: minute ring, past 30 minutes, right now. */
class ActiveTileService : AnalyticsTileService() {
    override fun render(context: Context, device: DeviceParameters, snapshot: StatsSnapshot, stale: Boolean) =
        Tiles.active(context, device, snapshot, stale)
}

/** Tile 2: today so far vs yesterday. */
class TodayVsYesterdayTileService : AnalyticsTileService() {
    override fun render(context: Context, device: DeviceParameters, snapshot: StatsSnapshot, stale: Boolean) =
        Tiles.todayVsYesterday(context, device, snapshot, stale)
}

/** Tile 3: today's total with an hourly chart. */
class TodayTotalTileService : AnalyticsTileService() {
    override fun render(context: Context, device: DeviceParameters, snapshot: StatsSnapshot, stale: Boolean) =
        Tiles.todayTotal(context, device, snapshot, stale)
}

/**
 * Tile 4: US world clock. No network: it ships an hour of per-minute timeline
 * entries so the system can flip minutes without waking the app.
 */
class UsClockTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val device = requestParams.deviceConfiguration
            val startMs = System.currentTimeMillis() / 60_000L * 60_000L
            val timeline = TimelineBuilders.Timeline.Builder()
            for (i in 0 until 60) {
                val t = startMs + i * 60_000L
                timeline.addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(
                            LayoutElementBuilders.Layout.Builder()
                                .setRoot(Tiles.usClock(applicationContext, device, Instant.ofEpochMilli(t)))
                                .build(),
                        )
                        .setValidity(
                            TimelineBuilders.TimeInterval.Builder()
                                .setStartMillis(t)
                                .setEndMillis(t + 60_000L)
                                .build(),
                        )
                        .build(),
                )
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(55 * 60_000L)
                .setTileTimeline(timeline.build())
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) = resources()
}
