package net.orbitratech.healthmate.feature.dashboard.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.orbitratech.healthmate.R
import net.orbitratech.healthmate.core.designsystem.component.AppIcon
import net.orbitratech.healthmate.core.designsystem.component.ConfirmDialog
import net.orbitratech.healthmate.core.designsystem.component.ErrorState
import net.orbitratech.healthmate.core.designsystem.component.LocalAppSnackbar
import net.orbitratech.healthmate.core.designsystem.component.ObserveEvents
import net.orbitratech.healthmate.core.designsystem.component.SectionHeader
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

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onLogActivity,
                modifier = Modifier.testTag("fab_log_activity"),
                icon = { AppIcon(R.drawable.ic_add, null) },
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
                modifier = Modifier
                    .padding(padding)
                    .testTag("dashboard_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Header(state) }
                item { SectionHeader("Today's activity", Modifier.padding(top = 8.dp)) }
                HealthMetric.entries.forEach { metric ->
                    item(key = metric.name) {
                        MetricProgressCard(
                            metric = metric,
                            value = state.todaySummary.valueOf(metric),
                            goal = state.goals.goalFor(metric),
                            onClick = { onSelectMetric(metric) },
                        )
                    }
                }
                item { QuickWaterCard(state, onQuickAddWater) }
                item { WeeklyCard(state, onSelectMetric) }
                item {
                    SectionHeader("Today's entries", Modifier.padding(top = 8.dp)) {
                        if (state.todayEntries.isNotEmpty()) {
                            TextButton(onClick = onSeeAllToday) { Text("See all") }
                        }
                    }
                }
                if (state.todayEntries.isEmpty()) {
                    item { NothingLoggedCard() }
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

@Composable
private fun Header(state: DashboardUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${DateUtils.greeting(state.now)} 👋",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Today – ${DateUtils.formatLong(state.today)}",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (state.streak > 0) {
            Text(
                "🔥 ${state.streak} day${if (state.streak == 1) "" else "s"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun QuickWaterCard(state: DashboardUiState, onQuickAddWater: (Int) -> Unit) {
    val water = HealthMetric.WATER
    Card(colors = CardDefaults.cardColors(containerColor = water.color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_local_drink, null, tint = water.color)
                Spacer(Modifier.width(8.dp))
                Text("Quick add water", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${Formatters.percent(state.goals.progress(water, state.todaySummary.water))} of goal",
                    style = MaterialTheme.typography.labelMedium,
                    color = water.color,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WATER_PRESETS.forEach { ml ->
                    OutlinedButton(
                        onClick = { onQuickAddWater(ml) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_water_$ml"),
                        border = BorderStroke(1.dp, water.color.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = water.color),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) { Text("+$ml ml") }
                }
            }
        }
    }
}

@Composable
private fun WeeklyCard(state: DashboardUiState, onSelectMetric: (HealthMetric) -> Unit) {
    val metric = state.chartMetric
    val best = state.bestDay(metric)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                HealthMetric.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = m == metric,
                        onClick = { onSelectMetric(m) },
                        shape = SegmentedButtonDefaults.itemShape(index, HealthMetric.entries.size),
                        icon = { AppIcon(m.icon, null, Modifier.size(18.dp)) },
                        label = { Text(m.shortLabel, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            WeeklyBarChart(days = state.trend, metric = metric, goal = state.goals.goalFor(metric))
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
