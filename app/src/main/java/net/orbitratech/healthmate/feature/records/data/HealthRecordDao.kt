package net.orbitratech.healthmate.feature.records.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the `health_records` table – the only place where
 * SQL for health records is written.
 *
 * Read queries return [Flow]s: Room re-runs them automatically whenever the
 * table changes, so every screen observing data updates itself after an
 * insert, update or delete without any manual refresh.
 */
@Dao
interface HealthRecordDao {

    // ------------------------------------------------------------ CREATE --

    /** Inserts a record and returns its generated row id. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: HealthRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<HealthRecordEntity>)

    // -------------------------------------------------------------- READ --

    @Query("SELECT * FROM health_records WHERE id = :id")
    suspend fun getById(id: Long): HealthRecordEntity?

    /**
     * Records inside the inclusive date range, newest first. Passing `null`
     * for a bound leaves that side of the range open (used by "All records").
     */
    @Query(
        """
        SELECT * FROM health_records
        WHERE (:from IS NULL OR date >= :from)
          AND (:to IS NULL OR date <= :to)
        ORDER BY date DESC, id DESC
        """,
    )
    fun observeRecords(from: String?, to: String?): Flow<List<HealthRecordEntity>>

    /** Daily totals inside the inclusive range, oldest first. */
    @Query(
        """
        SELECT date,
               SUM(steps)    AS steps,
               SUM(calories) AS calories,
               SUM(water)    AS water,
               COUNT(*)      AS entryCount
        FROM health_records
        WHERE date BETWEEN :from AND :to
        GROUP BY date
        ORDER BY date ASC
        """,
    )
    fun observeDailySummaries(from: String, to: String): Flow<List<DailySummaryRow>>

    @Query("SELECT COUNT(*) FROM health_records")
    suspend fun count(): Int

    // ------------------------------------------------------------ UPDATE --

    /** Updates the row with the same primary key; returns rows changed. */
    @Update
    suspend fun update(record: HealthRecordEntity): Int

    // ------------------------------------------------------------ DELETE --

    @Query("DELETE FROM health_records WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM health_records")
    suspend fun deleteAll()

    /** Replaces all rows atomically (used by "Restore sample data"). */
    @Transaction
    suspend fun replaceAll(records: List<HealthRecordEntity>) {
        deleteAll()
        insertAll(records)
    }
}
