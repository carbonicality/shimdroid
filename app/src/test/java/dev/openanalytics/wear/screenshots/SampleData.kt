package dev.openanalytics.wear.screenshots

import dev.openanalytics.wear.data.DailyPoint
import dev.openanalytics.wear.data.GaProperty
import dev.openanalytics.wear.data.MetricSet
import dev.openanalytics.wear.data.RankedItem
import dev.openanalytics.wear.data.Realtime
import dev.openanalytics.wear.data.StatsSnapshot
import dev.openanalytics.wear.data.Summary
import dev.openanalytics.wear.ui.DashboardState
import dev.openanalytics.wear.ui.Stage
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Plausible fake numbers for screenshots. */
object SampleData {
    val property = GaProperty("312345678", "openanalytics.dev", "My Company")

    val realtime = Realtime(
        activeUsers = 47,
        perMinute = listOf(
            1, 2, 1, 3, 2, 2, 4, 3, 5, 4, 3, 6, 5, 4, 7,
            6, 5, 8, 6, 7, 9, 8, 6, 7, 10, 9, 8, 11, 9, 12,
        ).map { it.toLong() },
        topScreens = listOf(
            RankedItem("Home", 18),
            RankedItem("Pricing", 9),
            RankedItem("Blog: Launch week recap", 7),
            RankedItem("Docs · Getting started", 5),
            RankedItem("Sign up", 3),
        ),
    )

    private val today = LocalDate.of(2026, 11, 4)

    val summary = Summary(
        today = MetricSet(activeUsers = 1_284, newUsers = 512, sessions = 1_730, views = 4_962),
        yesterday = MetricSet(activeUsers = 1_140, newUsers = 470, sessions = 1_521, views = 4_410),
        last7 = MetricSet(activeUsers = 8_420, newUsers = 3_310, sessions = 11_902, views = 33_870),
        prev7 = MetricSet(activeUsers = 8_910, newUsers = 3_502, sessions = 12_340, views = 35_105),
        daily = listOf(1_060, 1_190, 1_320, 1_280, 1_105, 1_045, 1_420)
            .mapIndexed { i, v -> DailyPoint(today.minusDays(7L - i), v.toLong()) },
        topPages = listOf(
            RankedItem("Home", 12_400),
            RankedItem("Pricing", 4_870),
            RankedItem("Docs · Getting started", 3_920),
            RankedItem("Blog: Launch week recap", 2_610),
            RankedItem("Changelog", 1_380),
        ),
        topCountries = listOf(
            RankedItem("United States", 3_120),
            RankedItem("United Kingdom", 1_410),
            RankedItem("Germany", 980),
            RankedItem("India", 860),
            RankedItem("Canada", 540),
        ),
    )

    val dashboard = DashboardState(
        stage = Stage.Dashboard,
        email = "you@gmail.com",
        property = property,
        realtime = realtime,
        summary = summary,
        updatedAt = LocalTime.of(10, 9),
    )

    val picker = DashboardState(
        stage = Stage.PickProperty,
        email = "you@gmail.com",
        properties = listOf(
            property,
            GaProperty("298765432", "Marketing site", "My Company"),
            GaProperty("287654321", "Android app", "Side projects"),
        ),
    )

    val signIn = DashboardState(stage = Stage.SignIn)

    val snapshot = StatsSnapshot.of(
        realtime, summary,
        today.atTime(10, 9).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )
}
