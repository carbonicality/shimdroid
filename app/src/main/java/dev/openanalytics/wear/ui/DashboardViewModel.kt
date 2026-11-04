package dev.openanalytics.wear.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openanalytics.wear.auth.ConsentRequiredException
import dev.openanalytics.wear.auth.GoogleAuth
import dev.openanalytics.wear.auth.NotSignedInException
import dev.openanalytics.wear.config.Analytics
import dev.openanalytics.wear.config.NoPropertyException
import dev.openanalytics.wear.data.AnalyticsClient
import dev.openanalytics.wear.data.GaProperty
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

enum class Stage { SignIn, PickProperty, Dashboard }

data class DashboardState(
    val stage: Stage = Stage.SignIn,
    val email: String? = null,
    /** Properties offered on the picker; null until loaded. */
    val properties: List<GaProperty>? = null,
    val property: GaProperty? = null,
    val realtime: Realtime? = null,
    val summary: Summary? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: LocalTime? = null,
    /** Google wants the user to approve access; the UI launches this then calls [DashboardViewModel.consentHandled]. */
    val consentIntent: Intent? = null,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val ctx get() = getApplication<Application>()
    private var summaryFetchedAtMs = 0L
    private var job: Job? = null
    private var propertiesJob: Job? = null

    init {
        syncStage()
    }

    /** Works out where we are (signed in? property chosen?) from stored state. */
    fun syncStage() {
        val account = GoogleAuth.account(ctx)
        val property = Analytics.selectedProperty(ctx)
        val stage = when {
            account == null -> Stage.SignIn
            property == null -> Stage.PickProperty
            else -> Stage.Dashboard
        }
        val previous = _state.value
        _state.update {
            it.copy(
                stage = stage,
                email = account?.email,
                property = property,
                // Drop numbers that belong to a different property.
                realtime = if (property == previous.property) it.realtime else null,
                summary = if (property == previous.property) it.summary else null,
            )
        }
        if (property != previous.property) summaryFetchedAtMs = 0L
        if (stage == Stage.PickProperty && _state.value.properties == null) loadProperties()
    }

    fun onSignInResult(data: Intent?) {
        try {
            GoogleAuth.handleSignInResult(data)
            _state.update { it.copy(error = null, properties = null) }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
        }
        syncStage()
    }

    fun loadProperties() {
        if (propertiesJob?.isActive == true) return
        propertiesJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            var only: GaProperty? = null
            try {
                val list = AnalyticsClient.listProperties(GoogleAuth.tokenSource(ctx))
                _state.update { it.copy(properties = list) }
                only = list.singleOrNull()
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _state.update { it.copy(loading = false) }
            }
            // Only one property? Pick it for them.
            only?.let { selectProperty(it) }
        }
    }

    fun selectProperty(property: GaProperty) {
        Analytics.selectProperty(ctx, property)
        syncStage()
        refresh(forceSummary = true)
    }

    fun changeProperty() {
        Analytics.selectProperty(ctx, null)
        _state.update { it.copy(properties = null) }
        syncStage()
    }

    fun signOut() {
        Analytics.signOut(ctx)
        _state.value = DashboardState()
        syncStage()
    }

    fun consentHandled() {
        _state.update { it.copy(consentIntent = null) }
    }

    /**
     * Refreshes realtime numbers every call; the slower-moving summary only when
     * [forceSummary] or when it is older than [SUMMARY_TTL_MS].
     */
    fun refresh(forceSummary: Boolean = false) {
        if (_state.value.stage != Stage.Dashboard) return
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val client = Analytics.client(ctx)
                val now = System.currentTimeMillis()
                val wantSummary = forceSummary || _state.value.summary == null || now - summaryFetchedAtMs > SUMMARY_TTL_MS
                val realtime = client.realtime()
                val summary = if (wantSummary) client.summary().also { summaryFetchedAtMs = now } else _state.value.summary
                _state.update {
                    it.copy(realtime = realtime, summary = summary, error = null, updatedAt = LocalTime.now())
                }
                if (summary != null) {
                    Analytics.saveSnapshot(ctx, StatsSnapshot.of(realtime, summary, now))
                    Analytics.requestTileUpdate(ctx)
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun handleError(e: Exception) {
        when (e) {
            is ConsentRequiredException -> _state.update { it.copy(consentIntent = e.intent, error = e.message) }
            is NotSignedInException, is NoPropertyException -> syncStage()
            else -> _state.update { it.copy(error = e.message ?: e.toString()) }
        }
    }

    companion object {
        const val REALTIME_INTERVAL_MS = 60_000L
        const val SUMMARY_TTL_MS = 10 * 60_000L
    }
}
