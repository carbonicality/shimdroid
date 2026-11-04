package dev.openanalytics.wear.screenshots

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.renderer.TileRenderer
import com.github.takahirom.roborazzi.captureRoboImage
import dev.openanalytics.wear.tile.TileContent
import dev.openanalytics.wear.tile.tileLayout
import dev.openanalytics.wear.ui.AppActions
import dev.openanalytics.wear.ui.DashboardState
import dev.openanalytics.wear.ui.OpenAnalyticsApp
import dev.openanalytics.wear.ui.OpenAnalyticsTheme
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
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$folder/$name.png")
    }

    private fun app(name: String, state: DashboardState, centerItem: Int = 1) = shoot(name) {
        OpenAnalyticsApp(
            state = state,
            actions = AppActions(),
            listState = rememberScalingLazyListState(initialCenterItemIndex = centerItem),
        )
    }

    @Test fun dashboard1ActiveNow() = app("01-active-now", SampleData.dashboard, centerItem = 1)
    @Test fun dashboard2Today() = app("02-today", SampleData.dashboard, centerItem = 2)
    @Test fun dashboard3Week() = app("03-last-7-days", SampleData.dashboard, centerItem = 3)
    @Test fun dashboard4TopPages() = app("04-top-pages-now", SampleData.dashboard, centerItem = 4)
    @Test fun dashboard5Countries() = app("05-top-countries", SampleData.dashboard, centerItem = 6)
    @Test fun dashboard6Settings() = app("06-refresh-and-settings", SampleData.dashboard, centerItem = 8)
    @Test fun signIn() = app("07-sign-in", SampleData.signIn, centerItem = 1)
    @Test fun pickProperty() = app("08-choose-property", SampleData.picker, centerItem = 2)

    @Test fun tile() = tile("09-tile", TileContent.Stats(SampleData.snapshot, stale = false))
    @Test fun tileOffline() = tile("10-tile-offline", TileContent.Stats(SampleData.snapshot, stale = true))
    @Test fun tileSignedOut() = tile("11-tile-signed-out", TileContent.Message("Not signed in", "Open the app to sign in"))

    private fun tile(name: String, content: TileContent) = shoot(name) {
        val context = LocalContext.current
        val config = LocalConfiguration.current
        val device = DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(config.screenWidthDp)
            .setScreenHeightDp(config.screenHeightDp)
            .setScreenDensity(context.resources.displayMetrics.density)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
            .build()
        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(tileLayout(context, device, content))
            .build()
        val resources = ResourceBuilders.Resources.Builder().setVersion("1").build()
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
