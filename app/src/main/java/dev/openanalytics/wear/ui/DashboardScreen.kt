package dev.openanalytics.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TitleCard
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import dev.openanalytics.wear.R
import dev.openanalytics.wear.auth.GoogleAuth
import dev.openanalytics.wear.data.GaProperty
import dev.openanalytics.wear.data.MetricSet
import dev.openanalytics.wear.data.RankedItem
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Everything the UI can ask for. */
class AppActions(
    val signIn: () -> Unit = {},
    val pickProperty: (GaProperty) -> Unit = {},
    val reloadProperties: () -> Unit = {},
    val changeProperty: () -> Unit = {},
    val signOut: () -> Unit = {},
    val refresh: () -> Unit = {},
)

/** Wires the ViewModel, Google sign-in / consent activities and foreground polling to [OpenAnalyticsApp]. */
@Composable
fun DashboardRoute(viewModel: DashboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onSignInResult(it.data)
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.syncStage()
        if (viewModel.state.value.stage == Stage.PickProperty) viewModel.loadProperties()
        else viewModel.refresh(forceSummary = true)
    }

    LaunchedEffect(state.consentIntent) {
        state.consentIntent?.let {
            viewModel.consentHandled()
            consentLauncher.launch(it)
        }
    }

    // Poll while visible; stop in the background (saves battery & API quota).
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.syncStage()
            while (true) {
                viewModel.refresh()
                delay(DashboardViewModel.REALTIME_INTERVAL_MS)
            }
        }
    }

    OpenAnalyticsApp(
        state = state,
        actions = AppActions(
            signIn = { signInLauncher.launch(GoogleAuth.signInIntent(context)) },
            pickProperty = viewModel::selectProperty,
            reloadProperties = viewModel::loadProperties,
            changeProperty = viewModel::changeProperty,
            signOut = viewModel::signOut,
            refresh = { viewModel.refresh(forceSummary = true) },
        ),
    )
}

/** Stateless UI; also rendered by the screenshot tests. */
@Composable
fun OpenAnalyticsApp(
    state: DashboardState,
    actions: AppActions,
    listState: ScalingLazyListState = rememberScalingLazyListState(),
    timeText: @Composable () -> Unit = { TimeText() },
) {
    Scaffold(
        timeText = timeText,
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state.stage) {
                Stage.SignIn -> signInItems(state, actions)
                Stage.PickProperty -> pickPropertyItems(state, actions)
                Stage.Dashboard -> dashboardItems(state, actions)
            }
        }
    }
}

private fun ScalingLazyListScope.header(text: String) {
    item {
        ListHeader {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colors.primary)
        }
    }
}

private fun ScalingLazyListScope.signInItems(state: DashboardState, actions: AppActions) {
    header("openAnalytics")
    item {
        Text(
            "Your Google Analytics stats, on your wrist.",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    state.error?.let { err -> item { MessageCard("Couldn't sign in", err, MaterialTheme.colors.error) } }
    item {
        Chip(
            onClick = actions.signIn,
            label = { Text("Sign in with Google") },
            icon = { AppIcon(R.drawable.ic_account) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        Text(
            "Uses the Google account on this watch. Read-only access.",
            style = MaterialTheme.typography.caption3,
            color = Muted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ScalingLazyListScope.pickPropertyItems(state: DashboardState, actions: AppActions) {
    header("Choose property")
    state.email?.let { email ->
        item { Text(email, style = MaterialTheme.typography.caption3, color = Muted, maxLines = 1) }
    }
    state.error?.let { err -> item { MessageCard("Couldn't load", err, MaterialTheme.colors.error) } }
    val props = state.properties
    when {
        props == null && state.loading -> item { CircularProgressIndicator(Modifier.size(32.dp)) }
        props == null -> item { RetryChip(actions.reloadProperties) }
        props.isEmpty() -> {
            item { MessageCard("No properties", "This account has no GA4 properties it can read.", Muted) }
            item { SignOutChip(actions.signOut) }
        }
        else -> {
            items(props) { p ->
                Chip(
                    onClick = { actions.pickProperty(p) },
                    label = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(p.account.ifBlank { p.id }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { AppIcon(R.drawable.ic_chart) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SignOutChip(actions.signOut) }
        }
    }
}

private fun ScalingLazyListScope.dashboardItems(state: DashboardState, actions: AppActions) {
    header(state.property?.name ?: "openAnalytics")

    state.error?.let { err -> item { MessageCard("Couldn't load", err, MaterialTheme.colors.error) } }

    if (state.realtime == null && state.summary == null && state.loading) {
        item { CircularProgressIndicator(modifier = Modifier.size(32.dp)) }
    }

    state.realtime?.let { rt ->
        item {
            StatCard(title = "Active now", subtitle = "30 min") {
                BigNumber(rt.activeUsers, Up)
                Sparkline(rt.perMinute, Up, Modifier.padding(top = 4.dp))
            }
        }
    }

    state.summary?.let { s ->
        item {
            StatCard(title = "Today", subtitle = "users") {
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber(s.today.activeUsers, Amber)
                    Spacer(Modifier.width(6.dp))
                    Delta(s.today.activeUsers, s.yesterday.activeUsers, "vs yday")
                }
                MetricRow(s.today)
            }
        }
        item {
            StatCard(title = "Last 7 days", subtitle = "users") {
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber(s.last7.activeUsers, Blue)
                    Spacer(Modifier.width(6.dp))
                    Delta(s.last7.activeUsers, s.prev7.activeUsers, "vs prev")
                }
                BarChart(s.daily.map { it.users }, Blue, Modifier.padding(vertical = 4.dp))
                MetricRow(s.last7)
            }
        }
    }

    state.realtime?.topScreens?.takeIf { it.isNotEmpty() }?.let { items ->
        item { RankedCard("Top pages now", items) }
    }
    state.summary?.let { s ->
        if (s.topPages.isNotEmpty()) item { RankedCard("Top pages · 7d", s.topPages) }
        if (s.topCountries.isNotEmpty()) item { RankedCard("Top countries · 7d", s.topCountries) }
    }

    val updatedAt = state.updatedAt
    item {
        Chip(
            onClick = actions.refresh,
            label = { Text(if (state.loading) "Refreshing…" else "Refresh") },
            secondaryLabel = if (updatedAt != null) {
                { Text("Updated ${updatedAt.format(TIME_FMT)}") }
            } else {
                null
            },
            icon = { AppIcon(R.drawable.ic_refresh) },
            colors = ChipDefaults.secondaryChipColors(),
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        Chip(
            onClick = actions.changeProperty,
            label = { Text("Change property") },
            secondaryLabel = { Text(state.property?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon = { AppIcon(R.drawable.ic_chart) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item { SignOutChip(actions.signOut, state.email) }
}

@Composable
private fun AppIcon(res: Int) {
    Icon(painter = painterResource(res), contentDescription = null, modifier = Modifier.size(ChipDefaults.IconSize))
}

@Composable
private fun RetryChip(onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text("Retry") },
        icon = { AppIcon(R.drawable.ic_refresh) },
        colors = ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SignOutChip(onClick: () -> Unit, email: String? = null) {
    Chip(
        onClick = onClick,
        label = { Text("Sign out") },
        secondaryLabel = if (email != null) {
            { Text(email, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        icon = { AppIcon(R.drawable.ic_account) },
        colors = ChipDefaults.childChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    TitleCard(
        onClick = {},
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        time = if (subtitle != null) {
            { Text(subtitle, color = Muted) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun BigNumber(value: Long, color: Color) {
    Text(
        text = compact(value),
        color = color,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
private fun Delta(current: Long, previous: Long, suffix: String) {
    val pct = percentChange(current, previous)
    val (text, color) = when {
        pct == null -> "—" to Muted
        pct > 0 -> "▲ $pct%" to Up
        pct < 0 -> "▼ ${-pct}%" to Down
        else -> "0%" to Muted
    }
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold)
        Text(suffix, color = Muted, style = MaterialTheme.typography.caption3)
    }
}

@Composable
private fun MetricRow(m: MetricSet) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        MiniStat("sess", m.sessions)
        MiniStat("views", m.views)
        MiniStat("new", m.newUsers)
    }
}

@Composable
private fun MiniStat(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(compact(value), style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.caption3, color = Muted)
    }
}

@Composable
private fun RankedCard(title: String, items: List<RankedItem>) {
    StatCard(title = title) {
        Spacer(Modifier.height(2.dp))
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    compact(item.value),
                    style = MaterialTheme.typography.caption2,
                    fontWeight = FontWeight.Bold,
                    color = Amber,
                )
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, message: String, accent: Color) {
    TitleCard(
        onClick = {},
        title = { Text(title, color = accent) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, style = MaterialTheme.typography.caption2)
    }
}
