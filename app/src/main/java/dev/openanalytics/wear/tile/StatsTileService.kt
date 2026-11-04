package dev.openanalytics.wear.tile

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dev.openanalytics.wear.config.Analytics
import dev.openanalytics.wear.data.ConfigException
import dev.openanalytics.wear.data.StatsSnapshot
import dev.openanalytics.wear.ui.MainActivity
import dev.openanalytics.wear.ui.compact
import dev.openanalytics.wear.ui.percentChange
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "openAnalytics" tile: realtime active users plus today / 7-day users with
 * change indicators. Tap the numbers to refresh, the chip to open the app.
 */
class StatsTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val forced = requestParams.currentState.lastClickableId == ID_REFRESH
            val content = loadContent(applicationContext, forced)
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MS)
                .setTileTimeline(
                    TimelineBuilders.Timeline.fromLayoutElement(
                        layout(applicationContext, requestParams.deviceConfiguration, content),
                    ),
                )
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture {
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    private sealed interface Content {
        data class Stats(val snapshot: StatsSnapshot, val stale: Boolean) : Content
        data class Message(val title: String, val body: String) : Content
    }

    private suspend fun loadContent(context: Context, forced: Boolean): Content {
        val cached = Analytics.lastSnapshot(context)
        val now = System.currentTimeMillis()
        // The app pushes fresh snapshots while open; don't re-fetch those.
        if (!forced && cached != null && now - cached.updatedAtMs < REUSE_MS) {
            return Content.Stats(cached, stale = false)
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
            Content.Stats(snapshot, stale = false)
        } catch (e: ConfigException) {
            Content.Message("Setup needed", "Open openAnalytics")
        } catch (e: Exception) {
            if (cached != null) Content.Stats(cached, stale = true)
            else Content.Message("Couldn't load", "Tap to retry")
        }
    }

    private fun layout(context: Context, device: DeviceParameters, content: Content): LayoutElementBuilders.LayoutElement {
        val openApp = ModifiersBuilders.Clickable.Builder()
            .setId(ID_OPEN)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
        val refresh = ModifiersBuilders.Clickable.Builder()
            .setId(ID_REFRESH)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val chip = CompactChip.Builder(context, "Open", openApp, device)
            .setChipColors(ChipColors(AMBER, BLACK))
            .build()

        val builder = PrimaryLayout.Builder(device).setPrimaryChipContent(chip)

        when (content) {
            is Content.Message -> {
                builder.setPrimaryLabelTextContent(text(context, "openAnalytics", Typography.TYPOGRAPHY_CAPTION1, AMBER))
                builder.setContent(
                    clickableColumn(refresh) {
                        addContent(text(context, content.title, Typography.TYPOGRAPHY_TITLE3, WHITE))
                        addContent(text(context, content.body, Typography.TYPOGRAPHY_CAPTION2, MUTED))
                    },
                )
            }
            is Content.Stats -> {
                val s = content.snapshot
                builder.setPrimaryLabelTextContent(text(context, "Active now", Typography.TYPOGRAPHY_CAPTION1, AMBER))
                builder.setContent(
                    clickableColumn(refresh) {
                        addContent(text(context, compact(s.activeUsers), Typography.TYPOGRAPHY_DISPLAY2, GREEN))
                        addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
                        addContent(
                            LayoutElementBuilders.Row.Builder()
                                .addContent(stat(context, "Today", s.todayUsers, s.yesterdayUsers))
                                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(16f)).build())
                                .addContent(stat(context, "7 days", s.last7Users, s.prev7Users))
                                .build(),
                        )
                    },
                )
                val time = TIME_FMT.format(Instant.ofEpochMilli(s.updatedAtMs).atZone(ZoneId.systemDefault()))
                builder.setSecondaryLabelTextContent(
                    text(
                        context,
                        if (content.stale) "offline · $time" else "updated $time",
                        Typography.TYPOGRAPHY_CAPTION3,
                        if (content.stale) RED else MUTED,
                    ),
                )
            }
        }
        return builder.build()
    }

    /** "Today / 1.2k / ▲ 12%" stacked. */
    private fun stat(context: Context, label: String, value: Long, previous: Long): LayoutElementBuilders.LayoutElement {
        val pct = percentChange(value, previous)
        val (delta, color) = when {
            pct == null -> "—" to MUTED
            pct > 0 -> "▲ $pct%" to GREEN
            pct < 0 -> "▼ ${-pct}%" to RED
            else -> "0%" to MUTED
        }
        return LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(context, label, Typography.TYPOGRAPHY_CAPTION3, MUTED))
            .addContent(text(context, compact(value), Typography.TYPOGRAPHY_TITLE3, WHITE))
            .addContent(text(context, delta, Typography.TYPOGRAPHY_CAPTION3, color))
            .build()
    }

    private fun clickableColumn(
        clickable: ModifiersBuilders.Clickable,
        block: LayoutElementBuilders.Column.Builder.() -> Unit,
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .apply(block)
            .build()

    private fun text(context: Context, value: String, typography: Int, color: Int) =
        Text.Builder(context, value)
            .setTypography(typography)
            .setColor(argb(color))
            .setMaxLines(1)
            .build()

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val ID_REFRESH = "refresh"
        private const val ID_OPEN = "open"

        /** How often the system re-requests the tile. */
        private const val FRESHNESS_MS = 15 * 60_000L
        /** Reuse a snapshot this recent instead of fetching. */
        private const val REUSE_MS = 2 * 60_000L
        private const val FETCH_TIMEOUT_MS = 12_000L

        private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private val AMBER = 0xFFF9AB00.toInt()
        private val GREEN = 0xFF81C995.toInt()
        private val RED = 0xFFF28B82.toInt()
        private val MUTED = 0xFFBDC1C6.toInt()
        private val WHITE = 0xFFFFFFFF.toInt()
        private val BLACK = 0xFF000000.toInt()
    }
}
