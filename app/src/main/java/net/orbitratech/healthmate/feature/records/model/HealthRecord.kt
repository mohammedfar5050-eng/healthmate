package net.orbitratech.healthmate.feature.records.model

import java.time.LocalDate

/**
 * Domain model for one health entry.
 *
 * The UI and ViewModels work with this class (which uses [LocalDate]) rather
 * than with the Room entity, so that database details such as the text date
 * format stay inside the data layer.
 *
 * @property id primary key; `0` means "not saved yet".
 * @property water water intake in millilitres.
 */
data class HealthRecord(
    val id: Long = 0,
    val date: LocalDate,
    val steps: Int = 0,
    val calories: Int = 0,
    val water: Int = 0,
) {
    val isNew: Boolean get() = id == 0L

    fun valueOf(metric: HealthMetric): Int = when (metric) {
        HealthMetric.STEPS -> steps
        HealthMetric.CALORIES -> calories
        HealthMetric.WATER -> water
    }
}

/**
 * Totals for one calendar day. A user may log several entries per day, so the
 * dashboard shows the sum produced by the DAO's `GROUP BY date` query.
 */
data class DailySummary(
    val date: LocalDate,
    val steps: Int = 0,
    val calories: Int = 0,
    val water: Int = 0,
    val entryCount: Int = 0,
) {
    val hasData: Boolean get() = entryCount > 0

    fun valueOf(metric: HealthMetric): Int = when (metric) {
        HealthMetric.STEPS -> steps
        HealthMetric.CALORIES -> calories
        HealthMetric.WATER -> water
    }

    companion object {
        fun empty(date: LocalDate) = DailySummary(date = date)

        /** Sums a list of records into a single summary for [date]. */
        fun of(date: LocalDate, records: List<HealthRecord>) = DailySummary(
            date = date,
            steps = records.sumOf { it.steps },
            calories = records.sumOf { it.calories },
            water = records.sumOf { it.water },
            entryCount = records.size,
        )
    }
}
