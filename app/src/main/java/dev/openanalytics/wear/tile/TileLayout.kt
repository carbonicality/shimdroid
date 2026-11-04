package dev.openanalytics.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import dev.openanalytics.wear.data.StatsSnapshot
import dev.openanalytics.wear.ui.MainActivity
import dev.openanalytics.wear.ui.compact
import dev.openanalytics.wear.ui.percentChange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the tile shows. */
sealed interface TileContent {
    data class Stats(val snapshot: StatsSnapshot, val stale: Boolean) : TileContent
    data class Message(val title: String, val body: String) : TileContent
}

private const val ID_OPEN = "open"
private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Same palette as the app (ui/Theme.kt).
private val BG = 0xFF11151C.toInt()
private val ACCENT = 0xFFA9B8FF.toInt()
private val INK = 0xFF141A2B.toInt()
private val UP = 0xFF7FD8B0.toInt()
private val DOWN = 0xFFFF9C9C.toInt()
private val MUTED = 0xFF8D97A9.toInt()
private val ON_SURFACE = 0xFFE6EAF2.toInt()

/**
 * Tile layout: "Active now" + big realtime number, Today / 7 days with deltas,
 * and an Open chip. Tapping the numbers refreshes. Pure function of its inputs,
 * so the screenshot tests can render it directly.
 */
fun tileLayout(context: Context, device: DeviceParameters, content: TileContent): LayoutElementBuilders.LayoutElement {
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
        .setId(StatsTileService.ID_REFRESH)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    val chip = CompactChip.Builder(context, "Open", openApp, device)
        .setChipColors(ChipColors(ACCENT, INK))
        .build()

    val builder = PrimaryLayout.Builder(device).setPrimaryChipContent(chip)

    when (content) {
        is TileContent.Message -> {
            builder.setPrimaryLabelTextContent(text(context, "OPENANALYTICS", Typography.TYPOGRAPHY_CAPTION2, MUTED))
            builder.setContent(
                clickableColumn(refresh) {
                    addContent(text(context, content.title, Typography.TYPOGRAPHY_TITLE3, ON_SURFACE))
                    addContent(text(context, content.body, Typography.TYPOGRAPHY_CAPTION2, MUTED))
                },
            )
        }
        is TileContent.Stats -> {
            val s = content.snapshot
            builder.setPrimaryLabelTextContent(text(context, "ACTIVE NOW", Typography.TYPOGRAPHY_CAPTION2, MUTED))
            builder.setContent(
                clickableColumn(refresh) {
                    addContent(text(context, compact(s.activeUsers), Typography.TYPOGRAPHY_DISPLAY2, ACCENT))
                    addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
                    addContent(
                        LayoutElementBuilders.Row.Builder()
                            .addContent(stat(context, "Today", s.todayUsers, s.yesterdayUsers))
                            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(20f)).build())
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
                    if (content.stale) DOWN else MUTED,
                ),
            )
        }
    }
    val radius = minOf(device.screenWidthDp, device.screenHeightDp) / 2f
    return LayoutElementBuilders.Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(BG))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(radius)).build())
                        .build(),
                )
                .build(),
        )
        .addContent(builder.build())
        .build()
}

/** "TODAY / 1.2k / ↑ 12%" stacked. */
private fun stat(context: Context, label: String, value: Long, previous: Long): LayoutElementBuilders.LayoutElement {
    val pct = percentChange(value, previous)
    val (delta, color) = when {
        pct == null -> "new" to MUTED
        pct > 0 -> "↑ $pct%" to UP
        pct < 0 -> "↓ ${-pct}%" to DOWN
        else -> "± 0%" to MUTED
    }
    return LayoutElementBuilders.Column.Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .addContent(text(context, label.uppercase(), Typography.TYPOGRAPHY_CAPTION3, MUTED))
        .addContent(text(context, compact(value), Typography.TYPOGRAPHY_TITLE3, ON_SURFACE))
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

