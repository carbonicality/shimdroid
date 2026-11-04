package dev.openanalytics.wear.ui

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
import dev.openanalytics.wear.data.MetricSet
import dev.openanalytics.wear.data.RankedItem
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Poll while the app is in the foreground; stop when it isn't (saves battery & quota).
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.reloadConfig()
            while (true) {
                viewModel.refresh()
                delay(DashboardViewModel.REALTIME_INTERVAL_MS)
            }
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader {
                    Text(
                        text = state.propertyName ?: "openAnalytics",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colors.primary,
                    )
                }
            }

            val configError = state.configError
            if (configError != null) {
                setupItems(configError, onRetry = { viewModel.reloadConfig(); viewModel.refresh(forceSummary = true) })
                return@ScalingLazyColumn
            }

            state.error?.let { err -> item { MessageCard("Couldn't load", err, MaterialTheme.colors.error) } }

            if (state.realtime == null && state.summary == null) {
                item {
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }

            state.realtime?.let { rt ->
                item {
                    StatCard(title = "Active now", subtitle = "last 30 min") {
                        BigNumber(rt.activeUsers, Up)
                        Sparkline(rt.perMinute, Up, Modifier.padding(top = 4.dp))
                    }
                }
            }

            state.summary?.let { s ->
                item {
                    StatCard(title = "Today", subtitle = "users") {
                        Row(verticalAlignment = Alignment.Bottom) {
                            BigNumber(s.today.activeUsers, MaterialTheme.colors.primary)
                            Spacer(Modifier.width(6.dp))
                            Delta(s.today.activeUsers, s.yesterday.activeUsers, "vs yday")
                        }
                        MetricRow(s.today)
                    }
                }
                item {
                    StatCard(title = "Last 7 days", subtitle = "users") {
                        Row(verticalAlignment = Alignment.Bottom) {
                            BigNumber(s.last7.activeUsers, MaterialTheme.colors.secondary)
                            Spacer(Modifier.width(6.dp))
                            Delta(s.last7.activeUsers, s.prev7.activeUsers, "vs prev")
                        }
                        BarChart(s.daily.map { it.users }, MaterialTheme.colors.secondary, Modifier.padding(vertical = 4.dp))
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
                    onClick = { viewModel.refresh(forceSummary = true) },
                    label = { Text(if (state.loading) "Refreshing…" else "Refresh") },
                    secondaryLabel = if (updatedAt != null) {
                        { Text("Updated ${updatedAt.format(TIME_FMT)}") }
                    } else {
                        null
                    },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(ChipDefaults.IconSize),
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.propertyId?.let { id ->
                item {
                    Text(
                        "Property $id",
                        style = MaterialTheme.typography.caption3,
                        color = Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun ScalingLazyListScope.setupItems(message: String, onRetry: () -> Unit) {
    item { MessageCard("Setup needed", message, MaterialTheme.colors.primary) }
    item {
        MessageCard(
            "How to",
            "Run scripts/provision.sh from your computer with the watch connected via ADB, then tap Retry.",
            Muted,
        )
    }
    item {
        Chip(
            onClick = onRetry,
            label = { Text("Retry") },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
                    color = MaterialTheme.colors.primary,
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
