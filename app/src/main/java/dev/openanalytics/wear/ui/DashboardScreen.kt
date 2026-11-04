package dev.openanalytics.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.HierarchicalFocusCoordinator
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.OutlinedChip
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import dev.openanalytics.wear.R
import dev.openanalytics.wear.auth.GoogleAuth
import dev.openanalytics.wear.data.GaProperty
import dev.openanalytics.wear.data.MetricSet
import dev.openanalytics.wear.data.REALTIME_MINUTES
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
    val selectWindow: (Int) -> Unit = {},
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
            selectWindow = viewModel::selectWindow,
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
    initialPage: Int = 0,
) {
    if (state.stage == Stage.Dashboard) {
        DashboardPager(state, actions, listState, timeText, initialPage)
        return
    }
    Scaffold(
        modifier = Modifier.background(Palette.Bg),
        timeText = timeText,
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        PaperList(listState) {
            if (state.stage == Stage.SignIn) signInItems(state, actions) else pickPropertyItems(state, actions)
        }
    }
}

@Composable
private fun PaperList(listState: ScalingLazyListState, content: ScalingLazyListScope.() -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Palette.Bg),
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 0),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

// ---------------------------------------------------------------- dashboard: ring page + list page

@Composable
private fun DashboardPager(
    state: DashboardState,
    actions: AppActions,
    listState: ScalingLazyListState,
    timeText: @Composable () -> Unit,
    initialPage: Int,
) {
    val pager = rememberPagerState(initialPage = initialPage) { 2 }
    val indicator = remember(pager) {
        object : PageIndicatorState {
            override val pageOffset get() = pager.currentPageOffsetFraction
            override val selectedPage get() = pager.currentPage
            override val pageCount get() = 2
        }
    }
    Scaffold(
        modifier = Modifier.background(Palette.Bg),
        timeText = timeText,
        positionIndicator = { if (pager.currentPage == 1) PositionIndicator(scalingLazyListState = listState) },
        pageIndicator = {
            HorizontalPageIndicator(
                pageIndicatorState = indicator,
                selectedColor = Palette.OnSurface,
                unselectedColor = Palette.BarMuted,
            )
        },
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            HierarchicalFocusCoordinator(requiresFocus = { pager.currentPage == page }) {
                if (page == 0) RingPage(state, actions) else DashboardList(state, actions, listState)
            }
        }
    }
}

/** Page 1: the minute ring. Bezel / crown or a tap on a tick picks how far back to count. */
@Composable
fun RingPage(state: DashboardState, actions: AppActions) {
    val haptics = LocalHapticFeedback.current
    val minutes by rememberUpdatedState(state.windowMinutes)
    var acc by remember { mutableFloatStateOf(0f) }
    val focus = rememberActiveFocusRequester()
    val rt = state.realtime

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .onRotaryScrollEvent { e ->
                acc += e.verticalScrollPixels
                var n = minutes
                while (acc >= ROTARY_STEP_PX) { n++; acc -= ROTARY_STEP_PX }
                while (acc <= -ROTARY_STEP_PX) { n--; acc += ROTARY_STEP_PX }
                n = n.coerceIn(1, REALTIME_MINUTES)
                if (n != minutes) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    actions.selectWindow(n)
                }
                true
            }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        if (rt == null) {
            if (state.loading) Spinner() else state.error?.let { Text(it, color = Palette.Down, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp)) }
            return@Box
        }
        MinuteRing(rt.perMinute, state.windowMinutes, onSelect = actions.selectWindow)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 44.dp)) {
            Text(
                if (state.windowMinutes == 1) "Right now" else "Past ${state.windowMinutes} min",
                color = Palette.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            )
            Text(
                state.windowUsers?.let(::full) ?: "–",
                color = Palette.OnSurface,
                fontSize = 54.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-2).sp,
                style = tabular,
                maxLines = 1,
            )
            Text("unique users", color = Palette.Muted, fontSize = 10.sp)
            Hairline(Modifier.width(140.dp).padding(top = 10.dp, bottom = 7.dp))
            Row(Modifier.width(140.dp)) {
                PairStat("Now", rt.nowUsers, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(30.dp).background(Palette.Hairline))
                PairStat("Past 30 min", rt.activeUsers, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PairStat(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Palette.Muted, fontSize = 9.sp)
        Text(full(value), color = Palette.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium, style = tabular)
    }
}

/** Page 2: everything else. */
@Composable
fun DashboardList(state: DashboardState, actions: AppActions, listState: ScalingLazyListState) {
    PaperList(listState) { dashboardItems(state, actions) }
}

private fun ScalingLazyListScope.dashboardItems(state: DashboardState, actions: AppActions) {
    item {
        Text(
            state.property?.name ?: "openAnalytics",
            color = Palette.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
    state.error?.let { err -> item { Notice("Couldn't refresh", err) } }
    if (state.summary == null && state.loading) item { Spinner() }

    state.summary?.let { s ->
        item {
            Section("Today", trailing = "users") {
                Text(full(s.today.activeUsers), color = Palette.OnSurface, fontSize = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp, style = tabular)
                DeltaLine(s.today.activeUsers, s.yesterday.activeUsers, "vs yesterday")
                MetricRow(s.today)
            }
        }
        item {
            Section("Last 7 days", trailing = "users") {
                Text(full(s.last7.activeUsers), color = Palette.OnSurface, fontSize = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp, style = tabular)
                DeltaLine(s.last7.activeUsers, s.prev7.activeUsers, "vs prior week")
                DayBars(s.daily.map { it.date to it.users }, Modifier.padding(top = 8.dp))
                MetricRow(s.last7)
            }
        }
    }
    state.realtime?.topScreens?.takeIf { it.isNotEmpty() }?.let { item { Ranked("Pages now", it) } }
    state.summary?.let { s ->
        if (s.topPages.isNotEmpty()) item { Ranked("Top pages · 7 days", s.topPages) }
        if (s.topCountries.isNotEmpty()) item { Ranked("Countries · 7 days", s.topCountries) }
    }

    item {
        PaperChip(
            label = if (state.loading) "Refreshing…" else "Refresh",
            icon = R.drawable.ic_refresh,
            secondary = state.updatedAt?.let { "Updated ${it.format(TIME_FMT)}" },
            enabled = !state.loading,
            onClick = actions.refresh,
        )
    }
    item {
        PaperChip("Change property", R.drawable.ic_chart, secondary = state.property?.name, onClick = actions.changeProperty)
    }
    item { SignOutChip(actions.signOut, state.email) }
}

// ---------------------------------------------------------------- sign in / pick property

private fun ScalingLazyListScope.signInItems(state: DashboardState, actions: AppActions) {
    item {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Palette.Track), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_chart), contentDescription = null, tint = Palette.Accent)
        }
    }
    item { Text("openAnalytics", color = Palette.OnSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    item {
        Text(
            "Your Google Analytics, at a glance.",
            color = Palette.Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
    state.error?.let { err -> item { Notice("Couldn't sign in", err) } }
    item {
        Chip(
            onClick = actions.signIn,
            label = { Text("Sign in with Google", fontWeight = FontWeight.SemiBold) },
            icon = { ChipIcon(R.drawable.ic_account) },
            colors = ChipDefaults.chipColors(
                backgroundColor = Palette.OnSurface,
                contentColor = Palette.Bg,
                iconColor = Palette.Bg,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        Text(
            "Uses this watch's Google account · read-only",
            color = Palette.Muted, fontSize = 10.sp, textAlign = TextAlign.Center,
        )
    }
}

private fun ScalingLazyListScope.pickPropertyItems(state: DashboardState, actions: AppActions) {
    item {
        Text("Choose a property", color = Palette.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
    }
    state.email?.let { email -> item { Text(email, color = Palette.Muted, fontSize = 11.sp, maxLines = 1) } }
    state.error?.let { err -> item { Notice("Couldn't load", err) } }
    val props = state.properties
    when {
        props == null && state.loading -> item { Spinner() }
        props == null -> item { PaperChip("Try again", R.drawable.ic_refresh, onClick = actions.reloadProperties) }
        props.isEmpty() -> {
            item { Notice("No properties", "This account can't read any GA4 properties.", Palette.Muted) }
            item { SignOutChip(actions.signOut) }
        }
        else -> {
            items(props) { p ->
                PaperChip(p.name, R.drawable.ic_chart, secondary = p.account.ifBlank { p.id }, onClick = { actions.pickProperty(p) })
            }
            item { SignOutChip(actions.signOut) }
        }
    }
}

// ---------------------------------------------------------------- building blocks

private const val ROTARY_STEP_PX = 36f
private val tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun Hairline(modifier: Modifier = Modifier.fillMaxWidth()) {
    Box(modifier) { Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Hairline)) }
}

/** Paper section: title + note over a hairline, then content. No card, no fill. */
@Composable
private fun Section(
    title: String,
    trailing: String? = null,
    titleColor: Color = Palette.OnSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) Text(trailing, color = Palette.Muted, fontSize = 11.sp)
        }
        Hairline(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 6.dp))
        content()
    }
}

@Composable
private fun DeltaLine(current: Long, previous: Long, comparison: String) {
    val pct = percentChange(current, previous)
    val color = when {
        pct == null || pct == 0 -> Palette.Muted
        pct > 0 -> Palette.Up
        else -> Palette.Down
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(signedPercent(current, previous) ?: "new", color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, style = tabular)
        Spacer(Modifier.width(5.dp))
        Text(comparison, color = Palette.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun MetricRow(m: MetricSet) {
    Hairline(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth()) {
        MiniStat("Sessions", m.sessions, Modifier.weight(1f))
        MiniStat("Views", m.views, Modifier.weight(1f))
        MiniStat("New", m.newUsers, Modifier.weight(1f))
    }
}

@Composable
private fun MiniStat(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(compact(value), color = Palette.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = tabular)
        Text(label, color = Palette.Muted, fontSize = 9.sp)
    }
}

@Composable
private fun Ranked(title: String, items: List<RankedItem>) {
    val max = items.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Section(title) {
        items.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 0.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.label, color = Palette.OnSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(full(item.value), color = Palette.Muted, fontSize = 12.sp, style = tabular)
            }
            ShareBar(item.value.toFloat() / max, if (i == 0) Palette.Accent else Palette.BarMuted, Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun Notice(title: String, message: String, accent: Color = Palette.Down) {
    Section(title, titleColor = accent) {
        Text(message, color = Palette.OnSurface, fontSize = 12.sp)
    }
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(26.dp),
        indicatorColor = Palette.OnSurface,
        trackColor = Palette.Track,
        strokeWidth = 2.dp,
    )
}

@Composable
private fun ChipIcon(res: Int) {
    Icon(painter = painterResource(res), contentDescription = null, modifier = Modifier.size(ChipDefaults.IconSize))
}

/** Outlined, hairline-bordered chip: Paper's button style. */
@Composable
private fun PaperChip(
    label: String,
    icon: Int,
    secondary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        icon = { ChipIcon(icon) },
        colors = ChipDefaults.outlinedChipColors(
            contentColor = Palette.OnSurface,
            secondaryContentColor = Palette.Muted,
            iconColor = Palette.Accent,
        ),
        border = ChipDefaults.outlinedChipBorder(borderColor = Palette.Hairline),
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
