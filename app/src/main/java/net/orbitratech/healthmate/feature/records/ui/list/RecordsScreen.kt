package net.orbitratech.healthmate.feature.records.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.orbitratech.healthmate.R
import net.orbitratech.healthmate.core.designsystem.component.AppIcon
import net.orbitratech.healthmate.core.designsystem.component.ConfirmDialog
import net.orbitratech.healthmate.core.designsystem.component.EmptyState
import net.orbitratech.healthmate.core.designsystem.component.ErrorState
import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.core.util.Formatters
import net.orbitratech.healthmate.feature.records.model.DailySummary
import net.orbitratech.healthmate.feature.records.model.HealthMetric
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import net.orbitratech.healthmate.feature.records.ui.component.RecordCard
import java.time.LocalDate

/**
 * Health Record List screen: records grouped by day with daily totals, date
 * filtering, sorting, swipe-to-delete and edit/delete actions.
 */
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel,
    onAddRecord: (LocalDate?) -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecordsContent(
        state = state,
        onFilterChange = viewModel::setFilter,
        onClearFilter = viewModel::clearFilter,
        onToggleSort = viewModel::toggleSortOrder,
        onAddRecord = { onAddRecord((state.filter as? DateFilter.SingleDate)?.date) },
        onOpenRecord = onOpenRecord,
        onDeleteRecord = viewModel::deleteRecord,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsContent(
    state: RecordsUiState,
    onFilterChange: (DateFilter) -> Unit,
    onClearFilter: () -> Unit,
    onToggleSort: () -> Unit,
    onAddRecord: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onDeleteRecord: (HealthRecord) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<HealthRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Records") },
                actions = {
                    IconButton(onClick = onToggleSort) {
                        AppIcon(
                            if (state.newestFirst) R.drawable.ic_arrow_downward else R.drawable.ic_arrow_upward,
                            contentDescription = if (state.newestFirst) "Showing newest first" else "Showing oldest first",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRecord,
                modifier = Modifier.testTag("fab_add_record"),
                icon = { AppIcon(R.drawable.ic_add, null) },
                text = { Text("Add record") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            DateFilterBar(filter = state.filter, today = state.today, onFilterChange = onFilterChange)
            if (state.filter.isActive && !state.isEmpty) {
                FilterSummary(state = state, onClear = onClearFilter)
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null -> ErrorState(state.errorMessage)
                state.isEmpty -> EmptyState(
                    icon = if (state.filter.isActive) R.drawable.ic_search_off else R.drawable.ic_event_note,
                    title = if (state.filter.isActive) "No records found" else "No records yet",
                    message = if (state.filter.isActive) {
                        "There are no entries ${state.filter.phrase}."
                    } else {
                        "Tap \"Add record\" to log your steps, calories and water."
                    },
                    action = if (state.filter.isActive) {
                        {
                            OutlinedButton(onClick = onClearFilter) {
                                AppIcon(R.drawable.ic_filter_off, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Clear filter")
                            }
                        }
                    } else {
                        null
                    },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "header-${group.date}") {
                            DayHeader(summary = group.summary, today = state.today)
                        }
                        val count = group.records.size
                        items(group.records, key = { it.id }) { record ->
                            val position = group.records.indexOf(record)
                            SwipeToDeleteRecord(
                                record = record,
                                entryLabel = if (count > 1) {
                                    "Entry ${if (state.newestFirst) count - position else position + 1}"
                                } else {
                                    null
                                },
                                onOpen = { onOpenRecord(record.id) },
                                onRequestDelete = { pendingDelete = record },
                                onSwipeDelete = { onDeleteRecord(record) },
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
private fun DayHeader(summary: DailySummary, today: LocalDate) {
    val label = DateUtils.friendlyLabel(summary.date, today)
    val relative = label == "Today" || label == "Yesterday"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (relative) {
                Text(
                    DateUtils.formatMedium(summary.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (summary.entryCount > 1) {
            Text("Total ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MetricTotals(summary)
        }
    }
}

@Composable
private fun MetricTotals(summary: DailySummary) {
    HealthMetric.entries.forEach { metric ->
        AppIcon(metric.icon, null, tint = metric.color, modifier = Modifier.size(14.dp))
        Text(
            Formatters.compact(summary.valueOf(metric)),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 2.dp, end = 6.dp),
        )
    }
}

@Composable
private fun FilterSummary(state: RecordsUiState, onClear: () -> Unit) {
    val days = state.groups.size
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${state.entryCount} ${if (state.entryCount == 1) "entry" else "entries"} · $days ${if (days == 1) "day" else "days"}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        MetricTotals(state.totals)
        IconButton(onClick = onClear) {
            AppIcon(R.drawable.ic_close, "Clear filter", Modifier.size(20.dp))
        }
    }
}

/** Record card that can be swiped from right to left to delete it. */
@Composable
private fun SwipeToDeleteRecord(
    record: HealthRecord,
    entryLabel: String?,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit,
    onSwipeDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onSwipeDelete() },
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                AppIcon(R.drawable.ic_delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        RecordCard(
            record = record,
            entryLabel = entryLabel,
            onClick = onOpen,
            onEdit = onOpen,
            onDelete = onRequestDelete,
        )
    }
}
