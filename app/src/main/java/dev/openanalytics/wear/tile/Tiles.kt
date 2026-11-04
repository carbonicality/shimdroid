package dev.openanalytics.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import dev.openanalytics.wear.data.REALTIME_MINUTES
import dev.openanalytics.wear.data.RingGeometry
import dev.openanalytics.wear.data.StatsSnapshot
import dev.openanalytics.wear.ui.MainActivity
import dev.openanalytics.wear.ui.full
import dev.openanalytics.wear.ui.percentChange
import dev.openanalytics.wear.ui.signedPercent
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The four tiles, in the app's "Paper, dark" style. Pure functions of their
 * inputs (text is drawn in DM Sans via [fonts]), so the screenshot tests
 * render them directly. After building a layout, hand `fonts.resources()` to
 * the system.
 */
class Tiles(private val fonts: TileText) {
    // Same palette as ui/Theme.kt.
    private val BG = 0xFF1D1C1A.toInt()
    private val HAIRLINE = 0xFF3A3833.toInt()
    private val TRACK = 0xFF2B2A27.toInt()
    private val BAR_MUTED = 0xFF45423C.toInt()
    private val ON_SURFACE = 0xFFEDE9E1.toInt()
    private val MUTED = 0xFF9F9B92.toInt()
    private val ACCENT = 0xFF8EA8FF.toInt()
    private val UP = 0xFF7FD3A2.toInt()
    private val DOWN = 0xFFF29A8C.toInt()

    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ------------------------------------------------------------ tile 1: minute ring

    fun active(context: Context, device: DeviceParameters, s: StatsSnapshot, stale: Boolean): LayoutElement {
        val max = (s.perMinute.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val tickDeg = 1.6f
        val arc = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(RingGeometry.START_DEG - tickDeg / 2))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .setVerticalAlign(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
        for (m in 0 until REALTIME_MINUTES) {
            val v = s.perMinute.getOrElse(REALTIME_MINUTES - 1 - m) { 0L }
            if (m > 0) {
                arc.addContent(
                    LayoutElementBuilders.ArcSpacer.Builder()
                        .setLength(degrees(RingGeometry.STEP_DEG - tickDeg))
                        .setThickness(dp(1f))
                        .build(),
                )
            }
            arc.addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(degrees(tickDeg))
                    .setThickness(dp(5f + 13f * (v / max)))
                    .setColor(argb(if (m == 0) ON_SURFACE else ACCENT))
                    // Square ends: round caps turn short arcs into blobs.
                    .setStrokeCap(
                        LayoutElementBuilders.StrokeCapProp.Builder()
                            .setValue(LayoutElementBuilders.STROKE_CAP_BUTT)
                            .build(),
                    )
                    .build(),
            )
        }

        val centre = column(
            text("Past 30 min", 11f, MUTED, medium = true),
            text(full(s.activeUsers), 50f, ON_SURFACE, medium = true),
            text("unique users", 10f, MUTED),
            gap(8f),
            LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(dot(ON_SURFACE))
                .addContent(hgap(6f))
                .addContent(text("${full(s.nowUsers)} right now", 12f, ON_SURFACE))
                .build(),
            gap(6f),
            status(s, stale),
        )
        val content = LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setPadding(padding(7f)).build())
            .addContent(arc.build())
            .addContent(centre)
            .build()
        return frame(context, device, content)
    }

    // ------------------------------------------------------------ tile 2: today vs yesterday

    fun todayVsYesterday(context: Context, device: DeviceParameters, s: StatsSnapshot, stale: Boolean): LayoutElement {
        val pct = percentChange(s.todayUsers, s.yesterdayUsers)
        val deltaColor = when {
            pct == null || pct == 0 -> MUTED
            pct > 0 -> UP
            else -> DOWN
        }
        val top = maxOf(s.todayUsers, s.yesterdayUsers).coerceAtLeast(1)
        val body = LayoutElementBuilders.Column.Builder()
            .setWidth(dp(184f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(text("Today vs yesterday", 13f, ON_SURFACE, semi = true))
            .addContent(gap(5f)).addContent(hairline(184f)).addContent(gap(8f))
            .addContent(text(signedPercent(s.todayUsers, s.yesterdayUsers) ?: "new", 40f, deltaColor, medium = true))
            .addContent(text("users, today so far vs all of yesterday", 9f, MUTED))
            .addContent(gap(10f))
            .addContent(barRow("Today", s.todayUsers, top, ACCENT))
            .addContent(gap(6f))
            .addContent(barRow("Yesterday", s.yesterdayUsers, top, BAR_MUTED))
            .addContent(gap(8f))
            .addContent(status(s, stale))
            .build()
        return frame(context, device, body)
    }

    private fun barRow(label: String, value: Long, top: Long, color: Int): LayoutElement {
        val trackW = 84f
        val fill = (trackW * value / top).coerceAtLeast(2f)
        return LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(fixedWidth(52f, text(label, 10f, MUTED)))
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(trackW)).setHeight(dp(8f))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                    .setModifiers(bg(TRACK))
                    .addContent(rect(fill, 8f, color))
                    .build(),
            )
            .addContent(hgap(6f))
            .addContent(text(full(value), 11f, ON_SURFACE, medium = true))
            .build()
    }

    // ------------------------------------------------------------ tile 3: today's total

    fun todayTotal(
        context: Context,
        device: DeviceParameters,
        s: StatsSnapshot,
        stale: Boolean,
        currentHour: Int = LocalTime.now().hour,
    ): LayoutElement {
        val max = (s.hourly.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val barW = 5.5f
        val barGap = 1.8f
        val chartW = 24 * barW + 23 * barGap
        val bars = LayoutElementBuilders.Row.Builder()
            .setHeight(dp(44f))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
        for (h in 0 until 24) {
            if (h > 0) bars.addContent(hgap(barGap))
            val v = s.hourly.getOrElse(h) { 0L }
            bars.addContent(
                when {
                    h > currentHour -> rect(barW, 2f, TRACK)
                    else -> rect(barW, maxOf(2f, 44f * v / max), if (h == currentHour) ACCENT else BAR_MUTED)
                },
            )
        }
        val axis = LayoutElementBuilders.Row.Builder()
        listOf("00", "06", "12", "18", "24").forEachIndexed { i, label ->
            if (i > 0) axis.addContent(hgap((chartW - 5 * 12f) / 4))
            axis.addContent(fixedWidth(12f, text(label, 8f, MUTED)))
        }
        val body = LayoutElementBuilders.Column.Builder()
            .setWidth(dp(chartW))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(spread(chartW, text("Today", 13f, ON_SURFACE, semi = true), text("total users", 10f, MUTED)))
            .addContent(gap(5f)).addContent(hairline(chartW)).addContent(gap(4f))
            .addContent(text(full(s.todayUsers), 40f, ON_SURFACE, medium = true))
            .addContent(gap(2f))
            .addContent(bars.build())
            .addContent(rect(chartW, 1f, ON_SURFACE))
            .addContent(gap(2f))
            .addContent(axis.build())
            .addContent(gap(6f))
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .addContent(text(full(s.todaySessions), 12f, ON_SURFACE, medium = true))
                    .addContent(hgap(3f)).addContent(text("sessions", 10f, MUTED))
                    .addContent(hgap(12f))
                    .addContent(text(full(s.todayViews), 12f, ON_SURFACE, medium = true))
                    .addContent(hgap(3f)).addContent(text("views", 10f, MUTED))
                    .build(),
            )
            .addContent(gap(6f))
            .addContent(status(s, stale))
            .build()
        return frame(context, device, body)
    }

    // ------------------------------------------------------------ tile 4: US world clock

    data class Zone(val city: String, val label: String, val id: ZoneId)

    companion object {
        val US_ZONES = listOf(
            Zone("New York", "Eastern · ET", ZoneId.of("America/New_York")),
            Zone("Chicago", "Central · CT", ZoneId.of("America/Chicago")),
            Zone("Denver", "Mountain · MT", ZoneId.of("America/Denver")),
            Zone("Los Angeles", "Pacific · PT", ZoneId.of("America/Los_Angeles")),
        )
    }

    private val H_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.US)
    private val AMPM: DateTimeFormatter = DateTimeFormatter.ofPattern("a", Locale.US)
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    fun usClock(context: Context, device: DeviceParameters, at: Instant): LayoutElement {
        // Smaller watches (e.g. Galaxy Watch 7 40 mm, ~216 dp) get a tighter layout.
        val small = device.screenWidthDp < 225
        val w = if (small) 152f else 172f
        val rowGap = if (small) 3f else 5f
        val body = LayoutElementBuilders.Column.Builder()
            .setWidth(dp(w))
            .addContent(
                spread(w, text("USA", 13f, ON_SURFACE, semi = true), text(at.atZone(US_ZONES[0].id).format(DATE), 10f, MUTED)),
            )
            .addContent(gap(5f)).addContent(hairline(w))
        for ((i, z) in US_ZONES.withIndex()) {
            val t = at.atZone(z.id)
            val day = t.hour in 7..18
            body.addContent(gap(rowGap))
            body.addContent(
                LayoutElementBuilders.Row.Builder()
                    .setWidth(dp(w))
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(if (day) dot(ACCENT) else ring(MUTED))
                    .addContent(hgap(7f))
                    .addContent(
                        fixedWidth(
                            if (small) 74f else 90f,
                            LayoutElementBuilders.Column.Builder()
                                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                                .addContent(text(z.city, 12f, ON_SURFACE, medium = true))
                                .addContent(text(z.label, 9f, MUTED))
                                .build(),
                        ),
                    )
                    .addContent(fixedWidth(if (small) 42f else 48f, fonts.glyphs(t.format(H_MM), if (small) 16f else 18f, ON_SURFACE, TileText.Weight.Medium), end = true))
                    .addContent(hgap(3f))
                    .addContent(text(t.format(AMPM), 9f, MUTED))
                    .build(),
            )
            body.addContent(gap(rowGap))
            if (i < US_ZONES.lastIndex) body.addContent(rect(w, 1f, TRACK))
        }
        return frame(context, device, body.build())
    }

    // ------------------------------------------------------------ messages

    fun message(context: Context, device: DeviceParameters, title: String, body: String): LayoutElement =
        frame(
            context, device,
            column(
                text("OPENANALYTICS", 10f, MUTED, medium = true),
                gap(10f),
                text(title, 17f, ON_SURFACE, semi = true),
                gap(4f),
                text(body, 12f, MUTED),
            ),
        )

    // ------------------------------------------------------------ building blocks

    /** Round graphite background; the whole tile opens the app. */
    private fun frame(context: Context, device: DeviceParameters, content: LayoutElement): LayoutElement {
        val radius = minOf(device.screenWidthDp, device.screenHeightDp) / 2f
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
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
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(open)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(BG))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(radius)).build())
                            .build(),
                    )
                    .build(),
            )
            .addContent(content)
            .build()
    }

    private fun status(s: StatsSnapshot, stale: Boolean): LayoutElement {
        val t = HHMM.format(Instant.ofEpochMilli(s.updatedAtMs).atZone(ZoneId.systemDefault()))
        return text(if (stale) "offline · $t" else "updated $t", 9f, if (stale) DOWN else MUTED)
    }

    private fun text(value: String, size: Float, color: Int, medium: Boolean = false, semi: Boolean = false): LayoutElement =
        fonts.text(
            value, size, color,
            when {
                semi -> TileText.Weight.SemiBold
                medium -> TileText.Weight.Medium
                else -> TileText.Weight.Regular
            },
        )

    private fun column(vararg items: LayoutElement): LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .apply { items.forEach { addContent(it) } }
            .build()

    private fun gap(h: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()
    private fun hgap(w: Float) = LayoutElementBuilders.Spacer.Builder().setWidth(dp(w)).build()

    private fun bg(color: Int) = ModifiersBuilders.Modifiers.Builder()
        .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(color)).build())
        .build()

    private fun rect(w: Float, h: Float, color: Int): LayoutElement =
        LayoutElementBuilders.Box.Builder().setWidth(dp(w)).setHeight(dp(h)).setModifiers(bg(color)).build()

    private fun hairline(w: Float) = rect(w, 1f, HAIRLINE)

    private fun dot(color: Int): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(6f)).setHeight(dp(6f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(color))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(3f)).build())
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun ring(color: Int): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(7f)).setHeight(dp(7f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBorder(ModifiersBuilders.Border.Builder().setWidth(dp(1.5f)).setColor(argb(color)).build())
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(BG))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(3.5f)).build())
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun padding(all: Float) = ModifiersBuilders.Padding.Builder().setAll(dp(all)).build()

    private fun fixedWidth(w: Float, content: LayoutElement, end: Boolean = false): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(w))
            .setHorizontalAlignment(
                if (end) LayoutElementBuilders.HORIZONTAL_ALIGN_END else LayoutElementBuilders.HORIZONTAL_ALIGN_START,
            )
            .addContent(content)
            .build()

    /** Left and right content pushed to the two ends of a [w]-wide row. */
    private fun spread(w: Float, left: LayoutElement, right: LayoutElement): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(w))
            .addContent(fixedWidth(w, left))
            .addContent(fixedWidth(w, right, end = true))
            .build()
}
