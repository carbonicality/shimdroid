package dev.openanalytics.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openanalytics.wear.config.Analytics
import dev.openanalytics.wear.data.AnalyticsClient
import dev.openanalytics.wear.data.Realtime
import dev.openanalytics.wear.data.StatsSnapshot
import dev.openanalytics.wear.data.Summary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

data class DashboardState(
    val configError: String? = null,
    val propertyId: String? = null,
    val propertyName: String? = null,
    val realtime: Realtime? = null,
    val summary: Summary? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: LocalTime? = null,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var client: AnalyticsClient? = null
    private var summaryFetchedAtMs = 0L
    private var job: Job? = null

    init {
        reloadConfig()
    }

    /** Re-reads credentials (e.g. after provisioning them while the app is open). */
    fun reloadConfig() {
        client = try {
            val c = Analytics.client(getApplication())
            _state.update { it.copy(configError = null, propertyId = c.propertyId) }
            c
        } catch (e: Exception) {
            _state.update { it.copy(configError = e.message ?: e.toString()) }
            null
        }
        summaryFetchedAtMs = 0L
    }

    /**
     * Refreshes realtime numbers every call; the (slower-moving) summary only when
     * [forceSummary] or when it is older than [SUMMARY_TTL_MS].
     */
    fun refresh(forceSummary: Boolean = false) {
        if (client == null) reloadConfig()
        val c = client ?: return
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val now = System.currentTimeMillis()
                val wantSummary = forceSummary || now - summaryFetchedAtMs > SUMMARY_TTL_MS
                val realtime = c.realtime()
                val summary = if (wantSummary) c.summary().also { summaryFetchedAtMs = now } else _state.value.summary
                val name = _state.value.propertyName ?: c.propertyName()
                _state.update {
                    it.copy(
                        realtime = realtime, summary = summary, propertyName = name,
                        error = null, updatedAt = LocalTime.now(),
                    )
                }
                if (summary != null) {
                    Analytics.saveSnapshot(getApplication(), StatsSnapshot.of(realtime, summary, now))
                    Analytics.requestTileUpdate(getApplication())
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    companion object {
        const val REALTIME_INTERVAL_MS = 60_000L
        const val SUMMARY_TTL_MS = 10 * 60_000L
    }
}
