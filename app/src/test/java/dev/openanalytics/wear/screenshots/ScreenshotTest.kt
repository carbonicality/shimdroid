package dev.openanalytics.wear.screenshots

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.TimeSource
import androidx.wear.compose.material.TimeText
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.tiles.renderer.TileRenderer
import com.github.takahirom.roborazzi.captureRoboImage
import dev.openanalytics.wear.tile.TileText
import dev.openanalytics.wear.tile.Tiles
import dev.openanalytics.wear.ui.AppActions
import dev.openanalytics.wear.ui.DashboardState
import dev.openanalytics.wear.ui.OpenAnalyticsApp
import dev.openanalytics.wear.ui.OpenAnalyticsTheme
import dev.openanalytics.wear.ui.Palette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import java.util.concurrent.Executor

// Galaxy Watch 7: 44 mm = 480x480 px, 40 mm = 432x432 px (xhdpi).
private const val GW7_44MM = "w240dp-h240dp-small-notlong-round-watch-xhdpi-keyshidden-nonav"
private const val GW7_40MM = "w216dp-h216dp-small-notlong-round-watch-xhdpi-keyshidden-nonav"

private object FixedTime : TimeSource {
    override val currentTime: String
        @Composable get() = "10:09"
}

/**
 * Renders the app and tile with sample data. Run `./gradlew recordRoborazziDebug`;
 * PNGs land in app/screenshots/.
 */
abstract class ScreenshotBase(private val folder: String) {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            OpenAnalyticsTheme {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Palette.Bg)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$folder/$name.png")
    }

    private fun app(name: String, state: DashboardState, centerItem: Int = 1, page: Int = 0) = shoot(name) {
        OpenAnalyticsApp(
            state = state,
            actions = AppActions(),
            listState = rememberScalingLazyListState(initialCenterItemIndex = centerItem),
            timeText = { TimeText(timeSource = FixedTime) }, // stable images across runs
            initialPage = page,
            fixedTime = SampleData.clockInstant,
        )
    }

    // App, page 1: the minute ring.
    @Test fun ring30() = app("01-ring-past-30-min", SampleData.dashboard)
    @Test fun ring10() = app("02-ring-past-10-min", SampleData.dashboard10Min)
    // App, page 2: the dashboard list.
    @Test fun today() = app("03-today", SampleData.dashboard, centerItem = 1, page = 1)
    @Test fun week() = app("04-last-7-days", SampleData.dashboard, centerItem = 2, page = 1)
    @Test fun pagesNow() = app("05-pages-now", SampleData.dashboard, centerItem = 3, page = 1)
    @Test fun countries() = app("06-countries", SampleData.dashboard, centerItem = 5, page = 1)
    @Test fun settings() = app("07-refresh-and-settings", SampleData.dashboard, centerItem = 7, page = 1)
    @Test fun signIn() = app("08-sign-in", SampleData.signIn, centerItem = 1)
    @Test fun pickProperty() = app("09-choose-property", SampleData.picker, centerItem = 2)

    // Tiles.
    @Test fun tileActive() = tile("10-tile-active") { t, c, d -> t.active(c, d, SampleData.snapshot, stale = false, at = SampleData.clockInstant) }
    @Test fun tileTodayVs() = tile("11-tile-today-vs-yesterday") { t, c, d -> t.todayVsYesterday(c, d, SampleData.snapshot, stale = false, at = SampleData.clockInstant) }
    @Test fun tileTodayTotal() = tile("12-tile-today-total") { t, c, d -> t.todayTotal(c, d, SampleData.snapshot, stale = false, currentHour = 10, at = SampleData.clockInstant) }
    @Test fun tileClock() = tile("13-tile-us-clock") { t, c, d -> t.usClock(c, d, SampleData.clockInstant) }
    @Test fun tileOffline() = tile("14-tile-active-offline") { t, c, d -> t.active(c, d, SampleData.snapshot, stale = true, at = SampleData.clockInstant) }
    @Test fun tileSignedOut() = tile("15-tile-signed-out") { t, c, d -> t.message(c, d, "Not signed in", "Open the app to sign in") }

    private fun tile(
        name: String,
        build: (Tiles, android.content.Context, DeviceParametersBuilders.DeviceParameters) -> LayoutElementBuilders.LayoutElement,
    ) = shoot(name) {
        val context = LocalContext.current
        val config = LocalConfiguration.current
        val device = DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(config.screenWidthDp)
            .setScreenHeightDp(config.screenHeightDp)
            .setScreenDensity(context.resources.displayMetrics.density)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
            .build()
        val fonts = TileText(context)
        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(build(Tiles(fonts), context, device))
            .build()
        val resources = fonts.resources()
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).also { frame ->
                    TileRenderer(ctx, Executor { it.run() }) { }
                        .inflateAsync(layout, resources, frame)
                    shadowOf(Looper.getMainLooper()).idle()
                }
            },
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GW7_44MM)
class GalaxyWatch7Large : ScreenshotBase("galaxy-watch7-44mm")

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GW7_40MM)
class GalaxyWatch7Small : ScreenshotBase("galaxy-watch7-40mm")
