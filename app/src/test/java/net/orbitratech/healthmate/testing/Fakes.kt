package net.orbitratech.healthmate.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.orbitratech.healthmate.core.database.SampleData
import net.orbitratech.healthmate.di.AppContainer
import net.orbitratech.healthmate.feature.records.data.HealthRecordRepository
import net.orbitratech.healthmate.feature.records.data.RepositoryException
import net.orbitratech.healthmate.feature.records.data.toDomain
import net.orbitratech.healthmate.feature.records.model.DailySummary
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import net.orbitratech.healthmate.feature.records.model.HealthMetric
import net.orbitratech.healthmate.feature.settings.data.SettingsRepository
import net.orbitratech.healthmate.feature.settings.data.ThemeMode
import net.orbitratech.healthmate.feature.settings.data.UserSettings
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The date used throughout the tests – the example date from the brief. */
val TEST_TODAY: LocalDate = LocalDate.of(2026, 8, 18)
val TEST_CLOCK: Clock = Clock.fixed(
    LocalDateTime.of(TEST_TODAY, java.time.LocalTime.of(9, 30)).toInstant(ZoneOffset.UTC),
    ZoneOffset.UTC,
)

/** Today = 7,850 steps / 420 kcal / 2,000 ml spread over two entries. */
fun defaultTestRecords() = listOf(
    HealthRecord(date = TEST_TODAY, steps = 5000, calories = 250, water = 1200),
    HealthRecord(date = TEST_TODAY, steps = 2850, calories = 170, water = 800),
    HealthRecord(date = TEST_TODAY.minusDays(1), steps = 9100, calories = 480, water = 2300),
    HealthRecord(date = TEST_TODAY.minusDays(6), steps = 6400, calories = 300, water = 1900),
)

/**
 * In-memory [HealthRecordRepository] that mirrors Room's behaviour (ordering,
 * inclusive filters, daily aggregation and reactive Flows).
 */
class FakeHealthRecordRepository(initial: List<HealthRecord> = emptyList()) : HealthRecordRepository {

    private val store = MutableStateFlow<Map<Long, HealthRecord>>(emptyMap())
    private var nextId = 1L

    /** When set, every write throws this exception. */
    var failWith: RepositoryException? = null

    val all: List<HealthRecord> get() = store.value.values.toList()

    init {
        initial.forEach { insert(it) }
    }

    private fun insert(record: HealthRecord): Long {
        val id = if (record.id != 0L) record.id else nextId
        nextId = maxOf(nextId, id + 1)
        store.update { it + (id to record.copy(id = id)) }
        return id
    }

    private fun check() {
        failWith?.let { throw it }
    }

    private fun inRange(date: LocalDate, from: LocalDate?, to: LocalDate?) =
        (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))

    override fun observeRecords(from: LocalDate?, to: LocalDate?): Flow<List<HealthRecord>> =
        store.map { map ->
            map.values.filter { inRange(it.date, from, to) }
                .sortedWith(compareByDescending<HealthRecord> { it.date }.thenByDescending { it.id })
        }

    override fun observeDailySummaries(from: LocalDate, to: LocalDate): Flow<List<DailySummary>> =
        store.map { map ->
            map.values.filter { inRange(it.date, from, to) }
                .groupBy { it.date }
                .toSortedMap()
                .map { (date, records) -> DailySummary.of(date, records) }
        }

    override suspend fun getRecord(id: Long): HealthRecord? = store.value[id]

    override suspend fun addRecord(record: HealthRecord): Long {
        check()
        return insert(record)
    }

    override suspend fun updateRecord(record: HealthRecord) {
        check()
        if (!store.value.containsKey(record.id)) throw RepositoryException("This record no longer exists.")
        store.update { it + (record.id to record) }
    }

    override suspend fun deleteRecord(id: Long) {
        check()
        store.update { it - id }
    }

    override suspend fun clearAll() {
        check()
        store.value = emptyMap()
    }

    override suspend fun resetWithSampleData() {
        check()
        store.value = emptyMap()
        SampleData.generate(TEST_TODAY).forEach { insert(it.toDomain()) }
    }
}

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state

    override suspend fun setGoal(metric: HealthMetric, value: Int) =
        state.update { it.copy(goals = it.goals.with(metric, value)) }

    override suspend fun resetGoals() = state.update { it.copy(goals = net.orbitratech.healthmate.feature.settings.data.DailyGoals()) }

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
}

class TestAppContainer(
    records: List<HealthRecord> = defaultTestRecords(),
    settings: UserSettings = UserSettings(),
) : AppContainer {
    override val clock: Clock = TEST_CLOCK
    override val healthRecordRepository = FakeHealthRecordRepository(records)
    override val settingsRepository = FakeSettingsRepository(settings)
}
