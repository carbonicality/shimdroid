package dev.openanalytics.wear.tile

import android.content.Context
import dev.openanalytics.wear.auth.ConsentRequiredException
import dev.openanalytics.wear.auth.NotSignedInException
import dev.openanalytics.wear.config.Analytics
import dev.openanalytics.wear.config.NoPropertyException
import dev.openanalytics.wear.data.StatsSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** What an analytics tile shows. */
sealed interface TileContent {
    data class Stats(val snapshot: StatsSnapshot, val stale: Boolean) : TileContent
    data class Message(val title: String, val body: String) : TileContent
}

/** Shared by all analytics tiles: one fetch serves them all. */
object TileData {
    /** Reuse a snapshot this recent instead of fetching (the app pushes fresh ones while open). */
    private const val REUSE_MS = 2 * 60_000L
    private const val FETCH_TIMEOUT_MS = 12_000L
    private val lock = Mutex()

    suspend fun load(context: Context): TileContent = lock.withLock {
        val cached = Analytics.lastSnapshot(context)
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.updatedAtMs < REUSE_MS) return TileContent.Stats(cached, stale = false)
        try {
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
            else TileContent.Message("Couldn't load", "Check the connection")
        }
    }
}
