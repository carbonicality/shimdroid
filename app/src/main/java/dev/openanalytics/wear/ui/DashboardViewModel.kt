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
import dev.openanalytics.wear.data.REALTIME_MINUTES
import dev.openanalytics.wear.data.Realtime
import dev.openanalytics.wear.data.StatsSnapshot
import dev.openanalytics.wear.data.Summary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** A refresh the user asked for (pull-to-refresh) is running: show the spinner. */
    val refreshing: Boolean = false,
    val error: String? = null,
    val updatedAt: LocalTime? = null,
    /** Google wants the user to approve access; the UI launches this then calls [DashboardViewModel.consentHandled]. */
    val consentIntent: Intent? = null,
    /** Minute-ring selection: unique users over the last [windowMinutes] minutes (1..30). */
    val windowMinutes: Int = REALTIME_MINUTES,
    /** Null while that window is being fetched. */
    val windowUsers: Long? = null,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val ctx get() = getApplication<Application>()
    private var summaryFetchedAtMs = 0L
    private var job: Job? = null
    private var propertiesJob: Job? = null
    private var refreshGen = 0
    private var windowJob: Job? = null
    /** Window size -> users, valid until the next realtime refresh. */
    private val windowCache = mutableMapOf<Int, Long>()

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

    /** Minute ring / bezel moved. */
    fun selectWindow(minutes: Int) {
        val n = minutes.coerceIn(1, REALTIME_MINUTES)
        if (n == _state.value.windowMinutes && _state.value.windowUsers != null) return
        _state.update { it.copy(windowMinutes = n) }
        resolveWindow()
    }

    private fun resolveWindow() {
        val st = _state.value
        val n = st.windowMinutes
        val rt = st.realtime
        val known = when {
            rt == null -> null
            n == REALTIME_MINUTES -> rt.activeUsers
            n == 1 -> rt.nowUsers
            else -> windowCache[n]
        }
        windowJob?.cancel()
        _state.update { it.copy(windowUsers = known) }
        if (known != null || rt == null) return
        windowJob = viewModelScope.launch {
            delay(WINDOW_DEBOUNCE_MS) // let the bezel settle before querying
            try {
                val users = Analytics.client(ctx).activeInLast(n)
                windowCache[n] = users
                if (_state.value.windowMinutes == n) _state.update { it.copy(windowUsers = users) }
            } catch (e: Exception) {
                handleError(e)
            }
        }
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
        if (job?.isActive == true) {
            if (!forceSummary) return // a background tick can wait for the running one
            job?.cancel() // the user asked: start a fresh, complete refresh now
        }
        val gen = ++refreshGen
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, refreshing = forceSummary || it.refreshing) }
            try {
                val client = Analytics.client(ctx)
                val now = System.currentTimeMillis()
                val wantSummary = forceSummary || _state.value.summary == null || now - summaryFetchedAtMs > SUMMARY_TTL_MS
                val realtime = client.realtime()
                val summary = if (wantSummary) client.summary().also { summaryFetchedAtMs = now } else _state.value.summary
                _state.update {
                    it.copy(realtime = realtime, summary = summary, error = null, updatedAt = LocalTime.now())
                }
                windowCache.clear()
                resolveWindow()
                if (summary != null) {
                    Analytics.saveSnapshot(ctx, StatsSnapshot.of(realtime, summary, now))
                    Analytics.requestTileUpdate(ctx)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e)
            } finally {
                // A newer refresh owns the loading flags now.
                if (gen == refreshGen) _state.update { it.copy(loading = false, refreshing = false) }
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
        /** Live numbers while the app is on screen, like GA's realtime view. */
        const val REALTIME_INTERVAL_MS = 20_000L
        /** Today / 7-day totals. */
        const val SUMMARY_TTL_MS = 60_000L
        const val WINDOW_DEBOUNCE_MS = 350L
    }
}
