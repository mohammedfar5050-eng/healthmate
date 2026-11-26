package net.orbitratech.healthmate.feature.dashboard.ui

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.orbitratech.healthmate.core.util.Validators
import net.orbitratech.healthmate.feature.records.data.HealthRecordRepository
import net.orbitratech.healthmate.feature.records.data.RepositoryException
import net.orbitratech.healthmate.feature.records.model.DailySummary
import net.orbitratech.healthmate.feature.records.model.HealthMetric
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import net.orbitratech.healthmate.feature.settings.data.DailyGoals
import net.orbitratech.healthmate.feature.settings.data.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** Everything the Dashboard needs to render itself. */
data class DashboardUiState(
    val today: LocalDate,
    val now: LocalTime = LocalTime.NOON,
    val isLoading: Boolean = true,
    val todaySummary: DailySummary = DailySummary.empty(today),
    val todayEntries: List<HealthRecord> = emptyList(),
    /** Exactly [TREND_DAYS] summaries, oldest first, empty days filled in. */
    val trend: List<DailySummary> = emptyList(),
    val streak: Int = 0,
    val goals: DailyGoals = DailyGoals(),
    val chartMetric: HealthMetric = HealthMetric.STEPS,
    val errorMessage: String? = null,
) {
    /** Average daily value over the trend days that contain data. */
    fun trendAverage(metric: HealthMetric): Int {
        val days = trend.filter { it.hasData }
        return if (days.isEmpty()) 0 else days.map { it.valueOf(metric) }.average().roundToInt()
    }

    fun bestDay(metric: HealthMetric): DailySummary? =
        trend.filter { it.hasData }.maxByOrNull { it.valueOf(metric) }

    fun daysGoalMet(metric: HealthMetric): Int =
        trend.count { it.hasData && it.valueOf(metric) >= goals.goalFor(metric) }

    companion object {
        const val TREND_DAYS = 7
        const val STREAK_LOOKBACK_DAYS = 60
    }
}

sealed interface DashboardEvent {
    data class Message(val text: String, val isError: Boolean = false) : DashboardEvent

    /** [ml] of water was added to the entry [recordId]; the snackbar offers Undo. */
    data class WaterAdded(val ml: Int, val recordId: Long) : DashboardEvent {
        val text: String get() = "Added $ml ml of water"
    }
}

/**
 * ViewModel (MVVM) for the Dashboard: today's totals, today's entries, a
 * 7-day trend and a logging streak, combined from two Room queries and the
 * user's goals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: HealthRecordRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))
    private val chartMetric = MutableStateFlow(HealthMetric.STEPS)

    /** Serialises quick-add water taps so rapid presses never overwrite each other. */
    private val waterMutex = Mutex()

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events: Flow<DashboardEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<DashboardUiState> = today
        .flatMapLatest { today ->
            combine(
                repository.observeDailySummaries(
                    from = today.minusDays(DashboardUiState.STREAK_LOOKBACK_DAYS - 1L),
                    to = today,
                ),
                repository.observeRecords(from = today, to = today),
                settingsRepository.settings,
                chartMetric,
            ) { summaries, todayRecords, settings, metric ->
                val byDate = summaries.associateBy { it.date }
                DashboardUiState(
                    today = today,
                    now = LocalTime.now(clock),
                    isLoading = false,
                    todaySummary = byDate[today] ?: DailySummary.empty(today),
                    todayEntries = todayRecords.asReversed(), // oldest first: Entry 1, Entry 2…
                    trend = (DashboardUiState.TREND_DAYS - 1 downTo 0).map { offset ->
                        val day = today.minusDays(offset.toLong())
                        byDate[day] ?: DailySummary.empty(day)
                    },
                    streak = calculateStreak(byDate, today),
                    goals = settings.goals,
                    chartMetric = metric,
                )
            }.catch {
                emit(
                    DashboardUiState(
                        today = today,
                        isLoading = false,
                        errorMessage = "Could not load your dashboard. Please try again.",
                    ),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(today = LocalDate.now(clock)),
        )

    fun selectChartMetric(metric: HealthMetric) = chartMetric.update { metric }

    /** Re-reads the current date so "today" rolls over after midnight. */
    fun refreshDate() = today.update { LocalDate.now(clock) }

    /**
     * Logs [ml] of water for today.
     *
     * Water from the quick-add buttons is collected in one *water-only* entry
     * per day (steps and calories are 0), so it never changes an activity
     * entry the user typed in. A new water entry is started when there is
     * none yet, or when the current one would exceed the per-entry limit.
     */
    fun quickAddWater(ml: Int) {
        viewModelScope.launch {
            try {
                val recordId = waterMutex.withLock {
                    val day = LocalDate.now(clock)
                    val waterEntry = repository.observeRecords(day, day).first() // newest first
                        .firstOrNull { it.isWaterOnly && it.water + ml <= Validators.MAX_WATER_ML }
                    if (waterEntry == null) {
                        repository.addRecord(HealthRecord(date = day, water = ml))
                    } else {
                        repository.updateRecord(waterEntry.copy(water = waterEntry.water + ml))
                        waterEntry.id
                    }
                }
                _events.send(DashboardEvent.WaterAdded(ml, recordId))
            } catch (e: RepositoryException) {
                _events.send(DashboardEvent.Message(e.message ?: "Could not add water.", isError = true))
            }
        }
    }

    /**
     * Reverses a quick-add (snackbar "Undo"): subtracts the water again and
     * removes the entry if nothing else is left in it.
     */
    fun undoQuickAddWater(event: DashboardEvent.WaterAdded) {
        viewModelScope.launch {
            try {
                waterMutex.withLock {
                    val record = repository.getRecord(event.recordId) ?: return@withLock
                    val remaining = record.copy(water = (record.water - event.ml).coerceAtLeast(0))
                    if (remaining.steps == 0 && remaining.calories == 0 && remaining.water == 0) {
                        repository.deleteRecord(record.id)
                    } else {
                        repository.updateRecord(remaining)
                    }
                }
            } catch (e: RepositoryException) {
                _events.send(DashboardEvent.Message(e.message ?: "Could not undo.", isError = true))
            }
        }
    }

    companion object {
        /**
         * Consecutive days with at least one entry, ending today – or
         * yesterday if nothing has been logged *yet* today, so that an ongoing
         * streak is not broken first thing in the morning.
         */
        fun calculateStreak(byDate: Map<LocalDate, DailySummary>, today: LocalDate): Int {
            fun hasData(day: LocalDate) = byDate[day]?.hasData == true
            var day = if (hasData(today)) today else today.minusDays(1)
            var streak = 0
            while (hasData(day)) {
                streak++
                day = day.minusDays(1)
            }
            return streak
        }
    }
}
