package dev.openanalytics.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
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
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
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
        modifier = Modifier.background(Palette.Bg),
        timeText = timeText,
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.stage) {
                Stage.SignIn -> signInItems(state, actions)
                Stage.PickProperty -> pickPropertyItems(state, actions)
                Stage.Dashboard -> dashboardItems(state, actions)
            }
        }
    }
}

// ---------------------------------------------------------------- screens

private fun ScalingLazyListScope.header(text: String) {
    item {
        ListHeader {
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Palette.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun ScalingLazyListScope.signInItems(state: DashboardState, actions: AppActions) {
    item {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Palette.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_chart), contentDescription = null, tint = Palette.Accent)
        }
    }
    item {
        Text("openAnalytics", color = Palette.OnSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
    item {
        Text(
            "Your Google Analytics, at a glance.",
            color = Palette.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
    state.error?.let { err -> item { Notice("Couldn't sign in", err) } }
    item {
        Chip(
            onClick = actions.signIn,
            label = { Text("Sign in with Google", fontWeight = FontWeight.Medium) },
            icon = { ChipIcon(R.drawable.ic_account) },
            colors = accentChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        Text(
            "Uses this watch's Google account · read-only",
            color = Palette.Muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ScalingLazyListScope.pickPropertyItems(state: DashboardState, actions: AppActions) {
    header("Choose a property")
    state.email?.let { email ->
        item { Text(email, color = Palette.Muted, fontSize = 11.sp, maxLines = 1) }
    }
    state.error?.let { err -> item { Notice("Couldn't load", err) } }
    val props = state.properties
    when {
        props == null && state.loading -> item { Spinner() }
        props == null -> item { FlatChip("Try again", R.drawable.ic_refresh, onClick = actions.reloadProperties) }
        props.isEmpty() -> {
            item { Notice("No properties", "This account can't read any GA4 properties.", Palette.Muted) }
            item { SignOutChip(actions.signOut) }
        }
        else -> {
            items(props) { p ->
                FlatChip(
                    label = p.name,
                    icon = R.drawable.ic_chart,
                    secondary = p.account.ifBlank { p.id },
                    onClick = { actions.pickProperty(p) },
                )
            }
            item { SignOutChip(actions.signOut) }
        }
    }
}

private fun ScalingLazyListScope.dashboardItems(state: DashboardState, actions: AppActions) {
    header(state.property?.name ?: "openAnalytics")

    state.error?.let { err -> item { Notice("Couldn't refresh", err) } }

    if (state.realtime == null && state.summary == null && state.loading) {
        item { Spinner() }
    }

    state.realtime?.let { rt ->
        item {
            Panel {
                Eyebrow("Active now", trailing = "30 min", live = true)
                Figure(rt.activeUsers, color = Palette.Accent, size = 40.sp)
                Sparkline(rt.perMinute, Palette.Accent, Modifier.padding(top = 4.dp))
            }
        }
    }

    state.summary?.let { s ->
        item {
            Panel {
                Eyebrow("Today", trailing = "users")
                HeadlineRow(s.today.activeUsers, s.yesterday.activeUsers, "vs yesterday")
                Hairline()
                MetricRow(s.today)
            }
        }
        item {
            Panel {
                Eyebrow("Last 7 days", trailing = "users")
                HeadlineRow(s.last7.activeUsers, s.prev7.activeUsers, "vs prior week")
                DayBars(s.daily.map { it.date to it.users }, Palette.Accent, Modifier.padding(top = 8.dp))
                Hairline()
                MetricRow(s.last7)
            }
        }
    }

    state.realtime?.topScreens?.takeIf { it.isNotEmpty() }?.let { items ->
        item { RankedPanel("Pages now", items) }
    }
    state.summary?.let { s ->
        if (s.topPages.isNotEmpty()) item { RankedPanel("Top pages · 7d", s.topPages) }
        if (s.topCountries.isNotEmpty()) item { RankedPanel("Countries · 7d", s.topCountries) }
    }

    val updatedAt = state.updatedAt
    item {
        FlatChip(
            label = if (state.loading) "Refreshing…" else "Refresh",
            icon = R.drawable.ic_refresh,
            secondary = updatedAt?.let { "Updated ${it.format(TIME_FMT)}" },
            enabled = !state.loading,
            onClick = actions.refresh,
        )
    }
    item {
        FlatChip(
            label = "Change property",
            icon = R.drawable.ic_chart,
            secondary = state.property?.name,
            onClick = actions.changeProperty,
        )
    }
    item { SignOutChip(actions.signOut, state.email) }
}

// ---------------------------------------------------------------- building blocks

/** Flat rounded surface. No gradients. */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}

/** Small spaced caps label, optional live dot and right-aligned note. */
@Composable
private fun Eyebrow(text: String, trailing: String? = null, live: Boolean = false, color: Color = Palette.Muted) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (live) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Up))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text.uppercase(),
            color = color,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, color = Palette.Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

private val tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun Figure(value: Long, color: Color = Palette.OnSurface, size: TextUnit = 34.sp) {
    Text(
        compact(value),
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.5).sp,
        style = tabular,
        maxLines = 1,
    )
}

@Composable
private fun HeadlineRow(current: Long, previous: Long, comparison: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Figure(current)
        Spacer(Modifier.width(10.dp))
        Column {
            DeltaPill(current, previous)
            Text(comparison, color = Palette.Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun DeltaPill(current: Long, previous: Long) {
    val pct = percentChange(current, previous)
    val (text, color) = when {
        pct == null -> "new" to Palette.Muted
        pct > 0 -> "↑ $pct%" to Palette.Up
        pct < 0 -> "↓ ${-pct}%" to Palette.Down
        else -> "± 0%" to Palette.Muted
    }
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        style = tabular,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

@Composable
private fun Hairline() {
    Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Palette.SurfaceHigh))
}

@Composable
private fun MetricRow(m: MetricSet) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MiniStat("Sessions", m.sessions)
        MiniStat("Views", m.views)
        MiniStat("New", m.newUsers)
    }
}

@Composable
private fun MiniStat(label: String, value: Long) {
    Column {
        Text(compact(value), color = Palette.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = tabular)
        Text(label, color = Palette.Muted, fontSize = 9.sp)
    }
}

@Composable
private fun RankedPanel(title: String, items: List<RankedItem>) {
    val max = items.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Panel {
        Eyebrow(title)
        Spacer(Modifier.height(6.dp))
        items.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.label,
                    color = Palette.OnSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(compact(item.value), color = Palette.Muted, fontSize = 12.sp, style = tabular)
            }
            ShareBar(
                item.value.toFloat() / max,
                if (i == 0) Palette.Accent else Palette.Accent.copy(alpha = 0.55f),
                Modifier.padding(top = 3.dp, bottom = if (i == items.lastIndex) 0.dp else 7.dp),
            )
        }
    }
}

@Composable
private fun Notice(title: String, message: String, accent: Color = Palette.Down) {
    Panel {
        Eyebrow(title, color = accent)
        Text(message, color = Palette.OnSurface, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(28.dp),
        indicatorColor = Palette.Accent,
        trackColor = Palette.SurfaceHigh,
        strokeWidth = 3.dp,
    )
}

@Composable
private fun ChipIcon(res: Int) {
    Icon(painter = painterResource(res), contentDescription = null, modifier = Modifier.size(ChipDefaults.IconSize))
}

@Composable
private fun flatChipColors(): ChipColors = ChipDefaults.chipColors(
    backgroundColor = Palette.Surface,
    contentColor = Palette.OnSurface,
    secondaryContentColor = Palette.Muted,
    iconColor = Palette.Accent,
)

@Composable
private fun accentChipColors(): ChipColors = ChipDefaults.chipColors(
    backgroundColor = Palette.Accent,
    contentColor = Palette.Ink,
    secondaryContentColor = Palette.Ink,
    iconColor = Palette.Ink,
)

@Composable
private fun FlatChip(
    label: String,
    icon: Int,
    secondary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        icon = { ChipIcon(icon) },
        colors = flatChipColors(),
        enabled = enabled,
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
        icon = { ChipIcon(R.drawable.ic_account) },
        colors = ChipDefaults.chipColors(
            backgroundColor = Color.Transparent,
            contentColor = Palette.Muted,
            secondaryContentColor = Palette.Muted,
            iconColor = Palette.Muted,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
