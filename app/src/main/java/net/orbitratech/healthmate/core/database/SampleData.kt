package net.orbitratech.healthmate.core.database

import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.feature.records.data.HealthRecordEntity
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Generates realistic dummy records for initial testing.
 *
 * Records are created relative to [today], so the dashboard always has data no
 * matter when the app is installed. A fixed random seed keeps the output
 * deterministic, which makes it usable in tests.
 */
object SampleData {

    /** Number of days (including today) that receive sample data. */
    const val DAYS = 21

    fun generate(today: LocalDate, seed: Int = 42): List<HealthRecordEntity> {
        val random = Random(seed)
        val records = mutableListOf<HealthRecordEntity>()

        for (offset in DAYS - 1 downTo 0) {
            val date = DateUtils.toDbString(today.minusDays(offset.toLong()))

            // Leave a couple of gaps so the history looks natural.
            if (offset == 9 || offset == 16) continue

            if (offset == 0) {
                // Today: a partially completed day (7,850 steps, 420 kcal, 2,000 ml).
                records += HealthRecordEntity(date = date, steps = 5200, calories = 280, water = 1250)
                records += HealthRecordEntity(date = date, steps = 2650, calories = 140, water = 750)
                continue
            }

            val steps = 4000 + random.nextInt(9000) // 4,000 – 12,999
            val calories = (steps * (0.04 + random.nextDouble() * 0.02)).roundToInt()
            val water = 1500 + random.nextInt(9) * 150 // 1,500 – 2,700 ml

            if (offset % 4 == 0) {
                // Every few days the activity is split into two separate entries.
                val morningSteps = (steps * 0.4).roundToInt()
                val morningCalories = (calories * 0.4).roundToInt()
                records += HealthRecordEntity(
                    date = date, steps = morningSteps, calories = morningCalories, water = 750,
                )
                records += HealthRecordEntity(
                    date = date,
                    steps = steps - morningSteps,
                    calories = calories - morningCalories,
                    water = water - 750,
                )
            } else {
                records += HealthRecordEntity(date = date, steps = steps, calories = calories, water = water)
            }
        }
        return records
    }
}
