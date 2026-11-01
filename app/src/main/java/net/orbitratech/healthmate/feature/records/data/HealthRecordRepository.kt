package net.orbitratech.healthmate.feature.records.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.orbitratech.healthmate.core.database.SampleData
import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.feature.records.model.DailySummary
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import java.time.Clock
import java.time.LocalDate

/**
 * Contract between the ViewModels and the data layer.
 *
 * ViewModels depend on this interface rather than on Room directly, which
 * keeps them unit-testable (tests inject an in-memory fake) and isolates the
 * rest of the app from the storage technology.
 */
interface HealthRecordRepository {
    /** Records in the inclusive range (null = unbounded), newest first. */
    fun observeRecords(from: LocalDate? = null, to: LocalDate? = null): Flow<List<HealthRecord>>

    /** Per-day totals in the inclusive range, oldest first (days without data are omitted). */
    fun observeDailySummaries(from: LocalDate, to: LocalDate): Flow<List<DailySummary>>

    suspend fun getRecord(id: Long): HealthRecord?

    /** Inserts [record] (keeping its id if it has one) and returns the row id. */
    suspend fun addRecord(record: HealthRecord): Long

    suspend fun updateRecord(record: HealthRecord)

    suspend fun deleteRecord(id: Long)

    suspend fun clearAll()

    suspend fun resetWithSampleData()
}

/** Thrown when an operation cannot be completed, with a user-friendly message. */
class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Room / SQLite implementation of [HealthRecordRepository]. */
class OfflineHealthRecordRepository(
    private val dao: HealthRecordDao,
    private val clock: Clock,
) : HealthRecordRepository {

    override fun observeRecords(from: LocalDate?, to: LocalDate?): Flow<List<HealthRecord>> =
        dao.observeRecords(from?.let(DateUtils::toDbString), to?.let(DateUtils::toDbString))
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeDailySummaries(from: LocalDate, to: LocalDate): Flow<List<DailySummary>> =
        dao.observeDailySummaries(DateUtils.toDbString(from), DateUtils.toDbString(to))
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getRecord(id: Long): HealthRecord? = guard("load the record") {
        dao.getById(id)?.toDomain()
    }

    override suspend fun addRecord(record: HealthRecord): Long = guard("save the record") {
        dao.insert(record.toEntity())
    }

    override suspend fun updateRecord(record: HealthRecord) = guard("update the record") {
        if (dao.update(record.toEntity()) == 0) {
            throw RepositoryException("This record no longer exists.")
        }
    }

    override suspend fun deleteRecord(id: Long) = guard("delete the record") {
        dao.deleteById(id)
        Unit
    }

    override suspend fun clearAll() = guard("delete all records") { dao.deleteAll() }

    override suspend fun resetWithSampleData() = guard("restore the sample data") {
        dao.replaceAll(SampleData.generate(today = LocalDate.now(clock)))
    }

    /** Converts low-level database errors into a [RepositoryException]. */
    private inline fun <T> guard(action: String, block: () -> T): T = try {
        block()
    } catch (e: RepositoryException) {
        throw e
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw RepositoryException("Could not $action. Please try again.", e)
    }
}
