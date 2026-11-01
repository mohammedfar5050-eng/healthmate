package net.orbitratech.healthmate.feature.records.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.feature.records.model.DailySummary
import net.orbitratech.healthmate.feature.records.model.HealthRecord

/**
 * Room entity mapped to the `health_records` SQLite table.
 *
 * | column   | type    | notes                                   |
 * |----------|---------|-----------------------------------------|
 * | id       | INTEGER | PRIMARY KEY AUTOINCREMENT               |
 * | date     | TEXT    | ISO-8601 `yyyy-MM-dd`, indexed          |
 * | steps    | INTEGER | steps walked                            |
 * | calories | INTEGER | calories burned (kcal)                  |
 * | water    | INTEGER | water intake (ml)                       |
 *
 * Dates are stored as `yyyy-MM-dd` text because that format sorts correctly
 * as a string, so `ORDER BY date` and `BETWEEN` filters work directly in SQL.
 */
@Entity(
    tableName = HealthRecordEntity.TABLE_NAME,
    indices = [Index(value = ["date"])],
)
data class HealthRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "steps")
    val steps: Int,
    @ColumnInfo(name = "calories")
    val calories: Int,
    @ColumnInfo(name = "water")
    val water: Int,
) {
    companion object {
        const val TABLE_NAME = "health_records"
    }
}

/** Result row of the `GROUP BY date` aggregation query. */
data class DailySummaryRow(
    val date: String,
    val steps: Int,
    val calories: Int,
    val water: Int,
    val entryCount: Int,
)

// ---- Mappers between the database layer and the domain model ----

fun HealthRecordEntity.toDomain() = HealthRecord(
    id = id,
    date = DateUtils.fromDbString(date),
    steps = steps,
    calories = calories,
    water = water,
)

fun HealthRecord.toEntity() = HealthRecordEntity(
    id = id,
    date = DateUtils.toDbString(date),
    steps = steps,
    calories = calories,
    water = water,
)

fun DailySummaryRow.toDomain() = DailySummary(
    date = DateUtils.fromDbString(date),
    steps = steps,
    calories = calories,
    water = water,
    entryCount = entryCount,
)
