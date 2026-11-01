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
     * Adds [ml] of water to today's most recent entry, or creates a new
     * entry for today when nothing has been logged yet.
     */
    fun quickAddWater(ml: Int) {
        viewModelScope.launch {
            try {
                val day = LocalDate.now(clock)
                val latest = repository.observeRecords(day, day).first().firstOrNull() // newest first
                if (latest == null) {
                    repository.addRecord(HealthRecord(date = day, water = ml))
                } else {
                    val total = latest.water + ml
                    if (total > Validators.MAX_WATER_ML) {
                        _events.send(
                            DashboardEvent.Message(
                                "That would exceed ${Validators.MAX_WATER_ML} ml for one entry",
                                isError = true,
                            ),
                        )
                        return@launch
                    }
                    repository.updateRecord(latest.copy(water = total))
                }
                _events.send(DashboardEvent.Message("Added $ml ml of water"))
            } catch (e: RepositoryException) {
                _events.send(DashboardEvent.Message(e.message ?: "Could not add water.", isError = true))
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
