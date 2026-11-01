package net.orbitratech.healthmate.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.orbitratech.healthmate.core.database.HealthMateDatabase
import net.orbitratech.healthmate.core.database.SampleData
import net.orbitratech.healthmate.feature.records.data.HealthRecordDao
import net.orbitratech.healthmate.feature.records.data.HealthRecordEntity
import net.orbitratech.healthmate.feature.records.data.OfflineHealthRecordRepository
import net.orbitratech.healthmate.feature.records.data.RepositoryException
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Integration tests for the Room/SQLite layer, run on the JVM against a real
 * in-memory SQLite database provided by Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class HealthRecordDaoTest {

    private val today = LocalDate.of(2026, 8, 18)
    private val clock = Clock.fixed(today.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private lateinit var db: HealthMateDatabase
    private lateinit var dao: HealthRecordDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HealthMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.healthRecordDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(date: String, steps: Int = 0, calories: Int = 0, water: Int = 0) =
        HealthRecordEntity(date = date, steps = steps, calories = calories, water = water)

    @Test
    fun schema_hasRequiredColumns() {
        val cursor = db.openHelper.readableDatabase.query("PRAGMA table_info(health_records)")
        val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
        cursor.close()
        assertEquals(listOf("id", "date", "steps", "calories", "water"), columns)
    }

    @Test
    fun insert_thenReadBackById() = runTest {
        val id = dao.insert(entity("2026-08-18", steps = 7850, calories = 420, water = 2000))
        val saved = dao.getById(id)!!
        assertEquals(7850, saved.steps)
        assertEquals(420, saved.calories)
        assertEquals(2000, saved.water)
        assertEquals("2026-08-18", saved.date)
    }

    @Test
    fun update_changesOnlyTargetRow() = runTest {
        val a = dao.insert(entity("2026-08-18", steps = 100))
        val b = dao.insert(entity("2026-08-18", steps = 200))
        val changed = dao.update(entity("2026-08-18", steps = 999).copy(id = a))
        assertEquals(1, changed)
        assertEquals(999, dao.getById(a)!!.steps)
        assertEquals(200, dao.getById(b)!!.steps)
    }

    @Test
    fun delete_removesRow() = runTest {
        val id = dao.insert(entity("2026-08-18", water = 250))
        assertEquals(1, dao.deleteById(id))
        assertNull(dao.getById(id))
    }

    @Test
    fun observeRecords_filtersByInclusiveRange_newestFirst() = runTest {
        dao.insert(entity("2026-08-10", steps = 1000))
        dao.insert(entity("2026-08-15", steps = 2000))
        dao.insert(entity("2026-08-18", steps = 3000))
        dao.insert(entity("2026-08-18", steps = 4000))

        assertEquals(listOf(4000, 3000, 2000, 1000), dao.observeRecords(null, null).first().map { it.steps })
        assertEquals(3, dao.observeRecords("2026-08-15", "2026-08-18").first().size)
        assertEquals(2, dao.observeRecords("2026-08-18", "2026-08-18").first().size)
    }

    @Test
    fun observeDailySummaries_sumsEntriesPerDay() = runTest {
        dao.insert(entity("2026-08-17", steps = 9000, water = 2400))
        dao.insert(entity("2026-08-18", steps = 5000, calories = 250, water = 1000))
        dao.insert(entity("2026-08-18", steps = 2850, calories = 170, water = 1000))

        val summaries = dao.observeDailySummaries("2026-08-01", "2026-08-18").first()
        assertEquals(2, summaries.size)
        val todayRow = summaries.last()
        assertEquals("2026-08-18", todayRow.date)
        assertEquals(7850, todayRow.steps)
        assertEquals(420, todayRow.calories)
        assertEquals(2000, todayRow.water)
        assertEquals(2, todayRow.entryCount)
    }

    @Test
    fun seedCallback_insertsSampleDataOnCreate() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seeded = Room.inMemoryDatabaseBuilder(context, HealthMateDatabase::class.java)
            .addCallback(HealthMateDatabase.SeedCallback(clock))
            .allowMainThreadQueries()
            .build()
        assertEquals(SampleData.generate(today).size, seeded.healthRecordDao().count())
        seeded.close()
    }

    @Test
    fun repository_mapsDomainModels_andResetsSampleData() = runTest {
        val repo = OfflineHealthRecordRepository(dao, clock)
        val id = repo.addRecord(HealthRecord(date = today, steps = 10, water = 250))
        assertEquals(today, repo.getRecord(id)!!.date)

        repo.resetWithSampleData()
        assertEquals(SampleData.generate(today).size, dao.count())
    }

    @Test
    fun repository_updatingMissingRecord_throwsFriendlyError() = runTest {
        val repo = OfflineHealthRecordRepository(dao, clock)
        val error = runCatching { repo.updateRecord(HealthRecord(id = 999, date = today)) }.exceptionOrNull()
        assertTrue(error is RepositoryException)
        assertEquals("This record no longer exists.", error!!.message)
    }
}
