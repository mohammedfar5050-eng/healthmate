package net.orbitratech.healthmate.feature.records.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.orbitratech.healthmate.feature.records.data.HealthRecordRepository
import net.orbitratech.healthmate.feature.records.data.RepositoryException
import net.orbitratech.healthmate.feature.records.model.DailySummary
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import java.time.Clock
import java.time.LocalDate

/** Records belonging to one calendar day (a section of the list). */
data class RecordGroup(val date: LocalDate, val records: List<HealthRecord>) {
    val summary: DailySummary get() = DailySummary.of(date, records)
}

/** Everything the Records screen needs to render itself. */
data class RecordsUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val filter: DateFilter = DateFilter.All,
    val newestFirst: Boolean = true,
    val groups: List<RecordGroup> = emptyList(),
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val entryCount: Int get() = groups.sumOf { it.records.size }
    val totals: DailySummary get() = DailySummary.of(today, groups.flatMap { it.records })
}

/** One-off events the screen reacts to (e.g. showing a snackbar with Undo). */
sealed interface RecordsEvent {
    data class Deleted(val record: HealthRecord) : RecordsEvent
    data class Error(val message: String) : RecordsEvent
}

/**
 * ViewModel (MVVM) for the Health Records list.
 *
 * The UI state is *derived* reactively: whenever the filter, the sort order or
 * the underlying table changes, Room emits new rows and [uiState] is rebuilt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(
    private val repository: HealthRecordRepository,
    private val clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow<DateFilter>(DateFilter.All)
    private val newestFirst = MutableStateFlow(true)
    private val today = MutableStateFlow(LocalDate.now(clock))

    private val _events = Channel<RecordsEvent>(Channel.BUFFERED)
    val events: Flow<RecordsEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<RecordsUiState> =
        combine(filter, newestFirst, today, ::Triple)
            .flatMapLatest { (filter, newestFirst, today) ->
                val (from, to) = filter.bounds(today)
                repository.observeRecords(from, to)
                    .map { records ->
                        val ordered = if (newestFirst) records else records.asReversed()
                        RecordsUiState(
                            today = today,
                            isLoading = false,
                            filter = filter,
                            newestFirst = newestFirst,
                            groups = ordered.groupConsecutiveByDate(),
                        )
                    }
                    .catch {
                        emit(
                            RecordsUiState(
                                today = today,
                                isLoading = false,
                                filter = filter,
                                newestFirst = newestFirst,
                                errorMessage = "Could not load records. Please try again.",
                            ),
                        )
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RecordsUiState(today = LocalDate.now(clock)),
            )

    fun setFilter(newFilter: DateFilter) = filter.update { newFilter }

    fun clearFilter() = setFilter(DateFilter.All)

    fun toggleSortOrder() = newestFirst.update { !it }

    /** Re-reads the current date (called when the app returns to the foreground). */
    fun refreshDate() = today.update { LocalDate.now(clock) }

    fun deleteRecord(record: HealthRecord) {
        viewModelScope.launch {
            try {
                repository.deleteRecord(record.id)
                _events.send(RecordsEvent.Deleted(record))
            } catch (e: RepositoryException) {
                _events.send(RecordsEvent.Error(e.message ?: "Could not delete the record."))
            }
        }
    }

    /** Re-inserts a just-deleted record with its original id ("Undo"). */
    fun restoreRecord(record: HealthRecord) {
        viewModelScope.launch {
            try {
                repository.addRecord(record)
            } catch (e: RepositoryException) {
                _events.send(RecordsEvent.Error(e.message ?: "Could not restore the record."))
            }
        }
    }
}

/** Splits an ordered list into consecutive same-day groups. */
internal fun List<HealthRecord>.groupConsecutiveByDate(): List<RecordGroup> {
    val groups = mutableListOf<RecordGroup>()
    var current = mutableListOf<HealthRecord>()
    for (record in this) {
        if (current.isNotEmpty() && current.first().date != record.date) {
            groups += RecordGroup(current.first().date, current)
            current = mutableListOf()
        }
        current += record
    }
    if (current.isNotEmpty()) groups += RecordGroup(current.first().date, current)
    return groups
}
