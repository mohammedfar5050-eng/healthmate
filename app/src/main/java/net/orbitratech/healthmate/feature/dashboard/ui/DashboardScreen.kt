package net.orbitratech.healthmate.feature.dashboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.orbitratech.healthmate.R
import net.orbitratech.healthmate.core.designsystem.component.AppIcon
import net.orbitratech.healthmate.core.designsystem.component.ConfirmDialog
import net.orbitratech.healthmate.core.designsystem.component.ErrorState
import net.orbitratech.healthmate.core.designsystem.component.LocalAppSnackbar
import net.orbitratech.healthmate.core.designsystem.component.ObserveEvents
import net.orbitratech.healthmate.core.designsystem.component.SectionHeader
import net.orbitratech.healthmate.core.designsystem.component.SingleLineText
import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.core.util.Formatters
import net.orbitratech.healthmate.feature.dashboard.ui.component.MetricProgressCard
import net.orbitratech.healthmate.feature.dashboard.ui.component.WeeklyBarChart
import net.orbitratech.healthmate.feature.records.model.HealthMetric
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import net.orbitratech.healthmate.feature.records.ui.component.RecordCard
import net.orbitratech.healthmate.feature.records.ui.form.WATER_PRESETS

/** Dashboard: a summary of today's activity plus weekly insights. */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onLogActivity: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onDeleteRecord: (HealthRecord) -> Unit,
    onSeeAllToday: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    ObserveEvents(viewModel.events) { event ->
        when (event) {
            is DashboardEvent.Message -> snackbar.show(event.text)
            is DashboardEvent.WaterAdded -> snackbar.show(event.text, actionLabel = "Undo") {
                viewModel.undoQuickAddWater(event)
            }
        }
    }
    DashboardContent(
        state = state,
        onSelectMetric = viewModel::selectChartMetric,
        onQuickAddWater = viewModel::quickAddWater,
        onLogActivity = onLogActivity,
        onOpenRecord = onOpenRecord,
        onDeleteRecord = onDeleteRecord,
        onSeeAllToday = onSeeAllToday,
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onSelectMetric: (HealthMetric) -> Unit,
    onQuickAddWater: (Int) -> Unit,
    onLogActivity: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onDeleteRecord: (HealthRecord) -> Unit,
    onSeeAllToday: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<HealthRecord?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The FAB shrinks to an icon once the user scrolls, so it covers less content.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onLogActivity,
                expanded = fabExpanded,
                modifier = Modifier.testTag("fab_log_activity"),
                icon = { AppIcon(R.drawable.ic_add, if (fabExpanded) null else "Log activity") },
                text = { Text("Log activity") },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.errorMessage != null -> ErrorState(state.errorMessage, Modifier.padding(padding))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .testTag("dashboard_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") { Header(state) }
                item(key = "activity_title") { SectionHeader("Today's activity", Modifier.padding(top = 4.dp)) }
                HealthMetric.entries.forEach { metric ->
                    item(key = metric.name) {
                        MetricProgressCard(
                            metric = metric,
                            value = state.todaySummary.valueOf(metric),
                            goal = state.goals.goalFor(metric),
                            onClick = {
                                // Show this metric's 7-day trend.
                                onSelectMetric(metric)
                                scope.launch { listState.animateScrollToItem(WEEKLY_CHART_INDEX) }
                            },
                            footer = if (metric == HealthMetric.WATER) {
                                { QuickAddWater(onQuickAddWater) }
                            } else {
                                null
                            },
                        )
                    }
                }
                item(key = "weekly") { WeeklyCard(state, onSelectMetric) }
                item(key = "entries_title") {
                    SectionHeader("Today's entries", Modifier.padding(top = 8.dp)) {
                        if (state.todayEntries.isNotEmpty()) {
                            TextButton(onClick = onSeeAllToday) { Text("See all") }
                        }
                    }
                }
                if (state.todayEntries.isEmpty()) {
                    item(key = "nothing_logged") { NothingLoggedCard() }
                } else {
                    state.todayEntries.forEachIndexed { index, record ->
                        item(key = "entry-${record.id}") {
                            RecordCard(
                                record = record,
                                entryLabel = if (state.todayEntries.size > 1) "Entry ${index + 1}" else null,
                                onClick = { onOpenRecord(record.id) },
                                onEdit = { onOpenRecord(record.id) },
                                onDelete = { pendingDelete = record },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        ConfirmDialog(
            title = "Delete record?",
            message = "This entry for ${DateUtils.formatLong(record.date)} will be permanently removed.",
            confirmLabel = "Delete",
            icon = R.drawable.ic_delete,
            destructive = true,
            onConfirm = {
                pendingDelete = null
                onDeleteRecord(record)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Position of the "Last 7 days" card: header, section title, then one card per metric. */
private val WEEKLY_CHART_INDEX = 2 + HealthMetric.entries.size

@Composable
private fun Header(state: DashboardUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${DateUtils.greeting(state.now)} 👋",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (state.streak > 0) StreakBadge(state.streak)
        }
        Spacer(Modifier.height(4.dp))
        // The title gets the full width so the date stays on one line on narrow phones.
        Text(
            "Today – ${DateUtils.formatLong(state.today)}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    val days = "$streak day${if (streak == 1) "" else "s"}"
    Text(
        "🔥 $days",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = "Logging streak: $days" },
    )
}

/**
 * One-tap water buttons shown inside the Water Intake card, right next to the
 * total they change. Each tap is confirmed by a snackbar with Undo.
 */
@Composable
private fun QuickAddWater(onQuickAddWater: (Int) -> Unit) {
    val water = HealthMetric.WATER
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_local_drink, null, Modifier.size(18.dp), tint = water.color)
            Spacer(Modifier.width(6.dp))
            Text(
                "Quick add water",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WATER_PRESETS.forEach { ml ->
                FilledTonalButton(
                    onClick = { onQuickAddWater(ml) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_water_$ml")
                        .semantics { contentDescription = "Add $ml ml of water" },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = water.color.copy(alpha = 0.12f),
                        contentColor = water.color,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    SingleLineText("+$ml ml", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun WeeklyCard(state: DashboardUiState, onSelectMetric: (HealthMetric) -> Unit) {
    val metric = state.chartMetric
    val goal = state.goals.goalFor(metric)
    val best = state.bestDay(metric)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Last 7 days",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                GoalLegend(metric, goal)
            }
            Spacer(Modifier.height(12.dp))
            MetricSelector(selected = metric, onSelect = onSelectMetric)
            Spacer(Modifier.height(16.dp))
            WeeklyBarChart(days = state.trend, metric = metric, goal = goal)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))
            Row {
                Stat(
                    "Daily average",
                    Formatters.number(state.trendAverage(metric)),
                    if (metric == HealthMetric.STEPS) "" else metric.unit,
                    Modifier.weight(1f),
                )
                Stat(
                    "Best day",
                    best?.let { DateUtils.formatWeekday(it.date) } ?: "–",
                    best?.let { Formatters.compact(it.valueOf(metric)) } ?: "",
                    Modifier.weight(1f),
                )
                Stat(
                    "Goal reached",
                    state.daysGoalMet(metric).toString(),
                    "/ ${DashboardUiState.TREND_DAYS} days",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** Explains the chart's dashed line, e.g. "- - Goal 10,000 steps". */
@Composable
private fun GoalLegend(metric: HealthMetric, goal: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 2.dp)) {
            drawLine(
                color = metric.color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "Goal ${Formatters.number(goal)} ${metric.unit}",
            style = MaterialTheme.typography.labelMedium,
            color = metric.color,
        )
    }
}

/**
 * Compact segmented control for the chart metric. Each segment shares the
 * width equally and its label shrinks instead of wrapping on narrow screens.
 */
@Composable
private fun MetricSelector(selected: HealthMetric, onSelect: (HealthMetric) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HealthMetric.entries.forEach { metric ->
            val isSelected = metric == selected
            val container by animateColorAsState(
                if (isSelected) metric.color.copy(alpha = 0.16f) else Color.Transparent,
                label = "segment",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(container)
                    .selectable(selected = isSelected, onClick = { onSelect(metric) }, role = Role.Tab)
                    .padding(horizontal = 8.dp)
                    .testTag("chart_metric_${metric.name.lowercase()}"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    metric.icon,
                    null,
                    Modifier.size(18.dp),
                    tint = if (isSelected) metric.color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                SingleLineText(
                    metric.shortLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                withStyle(MaterialTheme.typography.titleMedium.toSpanStyle()) { append(value) }
                if (unit.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) { append(" $unit") }
                }
            },
            textAlign = TextAlign.Center,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NothingLoggedCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_edit_calendar, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Nothing logged yet today. Tap \"Log activity\" to add your first entry.")
        }
    }
}
