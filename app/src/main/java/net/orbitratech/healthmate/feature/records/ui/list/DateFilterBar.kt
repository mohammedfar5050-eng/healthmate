package net.orbitratech.healthmate.feature.records.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.orbitratech.healthmate.R
import net.orbitratech.healthmate.core.designsystem.component.AppIcon
import net.orbitratech.healthmate.core.util.DateUtils
import java.time.LocalDate

/**
 * Horizontal filter chips: quick presets plus "Pick date" and "Date range",
 * which open the Material 3 date pickers (future dates are disabled).
 */
@Composable
fun DateFilterBar(
    filter: DateFilter,
    today: LocalDate,
    onFilterChange: (DateFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(DateFilter.presets.size) { index ->
            val preset = DateFilter.presets[index]
            FilterChip(
                selected = filter == preset,
                onClick = { onFilterChange(preset) },
                label = { Text(preset.label) },
                modifier = Modifier.testTag("filter_${preset.label}"),
                leadingIcon = if (filter == preset) {
                    { AppIcon(R.drawable.ic_check, null, Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
        item {
            FilterChip(
                selected = filter is DateFilter.SingleDate,
                onClick = { showDatePicker = true },
                label = { Text(if (filter is DateFilter.SingleDate) filter.label else "Pick date") },
                leadingIcon = { AppIcon(R.drawable.ic_calendar, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("filter_pick_date"),
            )
        }
        item {
            FilterChip(
                selected = filter is DateFilter.Range,
                onClick = { showRangePicker = true },
                label = { Text(if (filter is DateFilter.Range) filter.label else "Date range") },
                leadingIcon = { AppIcon(R.drawable.ic_date_range, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("filter_pick_range"),
            )
        }
    }

    if (showDatePicker) {
        SingleDatePickerDialog(
            initial = (filter as? DateFilter.SingleDate)?.date ?: today,
            today = today,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                showDatePicker = false
                onFilterChange(DateFilter.SingleDate(it))
            },
        )
    }
    if (showRangePicker) {
        DateRangePickerDialog(
            initial = (filter as? DateFilter.Range)?.let { it.start to it.end } ?: (today.minusDays(6) to today),
            today = today,
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                showRangePicker = false
                onFilterChange(DateFilter.range(start, end))
            },
        )
    }
}

/** Only allows dates between 2000-01-01 and today. */
@OptIn(ExperimentalMaterial3Api::class)
private class PastDates(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = DateUtils.fromPickerMillis(utcTimeMillis)
        return !date.isAfter(today) && !date.isBefore(DateUtils.EARLIEST_DATE)
    }

    override fun isSelectableYear(year: Int) = year in DateUtils.EARLIEST_DATE.year..today.year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePickerDialog(
    initial: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val selectable = remember(today) { PastDates(today) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DateUtils.toPickerMillis(initial),
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(DateUtils.fromPickerMillis(it)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    initial: Pair<LocalDate, LocalDate>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val selectable = remember(today) { PastDates(today) }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = DateUtils.toPickerMillis(initial.first),
        initialSelectedEndDateMillis = DateUtils.toPickerMillis(initial.second),
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis ?: start
                    if (start != null && end != null) {
                        onConfirm(DateUtils.fromPickerMillis(start), DateUtils.fromPickerMillis(end))
                    }
                },
                enabled = state.selectedStartDateMillis != null,
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.height(500.dp),
            title = { Text("Select date range", modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}
