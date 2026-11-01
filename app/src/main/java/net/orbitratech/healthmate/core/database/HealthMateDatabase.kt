package net.orbitratech.healthmate.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import net.orbitratech.healthmate.feature.records.data.HealthRecordDao
import net.orbitratech.healthmate.feature.records.data.HealthRecordEntity
import java.time.Clock

/**
 * The app's embedded SQLite database (`healthmate.db`), managed by Room.
 *
 * Room generates the `CREATE TABLE` statement from [HealthRecordEntity] and
 * validates every SQL query in [HealthRecordDao] at compile time. The exported
 * schema can be found in `app/schemas/`.
 */
@Database(
    entities = [HealthRecordEntity::class],
    version = HealthMateDatabase.VERSION,
    exportSchema = true,
)
abstract class HealthMateDatabase : RoomDatabase() {

    abstract fun healthRecordDao(): HealthRecordDao

    companion object {
        const val NAME = "healthmate.db"
        const val VERSION = 1

        fun create(context: Context, clock: Clock, seedSampleData: Boolean = true): HealthMateDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                HealthMateDatabase::class.java,
                NAME,
            )
            if (seedSampleData) builder.addCallback(SeedCallback(clock))
            return builder.build()
        }
    }

    /**
     * Inserts dummy records the very first time the database file is created,
     * so the dashboard, list and chart have data for testing/demonstration.
     */
    class SeedCallback(private val clock: Clock) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            SampleData.generate(today = java.time.LocalDate.now(clock)).forEach { record ->
                db.execSQL(
                    "INSERT INTO health_records (date, steps, calories, water) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(record.date, record.steps, record.calories, record.water),
                )
            }
        }
    }
}
