package net.orbitratech.healthmate.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.orbitratech.healthmate.feature.dashboard.ui.DashboardEvent
import net.orbitratech.healthmate.feature.dashboard.ui.DashboardViewModel
import net.orbitratech.healthmate.feature.records.data.RepositoryException
import net.orbitratech.healthmate.feature.records.model.HealthMetric
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import net.orbitratech.healthmate.feature.records.ui.form.RecordFormEvent
import net.orbitratech.healthmate.feature.records.ui.form.RecordFormViewModel
import net.orbitratech.healthmate.feature.records.ui.list.DateFilter
import net.orbitratech.healthmate.feature.records.ui.list.RecordsEvent
import net.orbitratech.healthmate.feature.records.ui.list.RecordsViewModel
import net.orbitratech.healthmate.feature.settings.data.ThemeMode
import net.orbitratech.healthmate.feature.settings.ui.SettingsViewModel
import net.orbitratech.healthmate.testing.FakeHealthRecordRepository
import net.orbitratech.healthmate.testing.FakeSettingsRepository
import net.orbitratech.healthmate.testing.MainDispatcherRule
import net.orbitratech.healthmate.testing.TEST_CLOCK
import net.orbitratech.healthmate.testing.TEST_TODAY
import net.orbitratech.healthmate.testing.defaultTestRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeHealthRecordRepository(defaultTestRecords())
    private val settings = FakeSettingsRepository()

    /** Keeps a StateFlow collected (WhileSubscribed) for the test duration. */
    private fun TestScope.keepCollecting(block: suspend () -> Unit) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block() }
    }

    // ---------------------------------------------------------- Records --

    @Test
    fun records_groupedByDate_newestFirst() = runTest {
        val vm = RecordsViewModel(repo, TEST_CLOCK)
        keepCollecting { vm.uiState.collect {} }
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(4, state.entryCount)
        assertEquals(3, state.groups.size)
        assertEquals(TEST_TODAY, state.groups.first().date)
        assertEquals(7850, state.groups.first().summary.steps)
    }

    @Test
    fun records_filterPresetsAndSingleDate() = runTest {
        val vm = RecordsViewModel(repo, TEST_CLOCK)
        keepCollecting { vm.uiState.collect {} }
        vm.setFilter(DateFilter.Today)
        assertEquals(2, vm.uiState.value.entryCount)
        vm.setFilter(DateFilter.SingleDate(TEST_TODAY.minusDays(6)))
        assertEquals(6400, vm.uiState.value.groups.single().records.single().steps)
        vm.setFilter(DateFilter.Last7Days)
        assertEquals(4, vm.uiState.value.entryCount)
        vm.setFilter(DateFilter.range(TEST_TODAY, TEST_TODAY.minusDays(1)))
        assertEquals(3, vm.uiState.value.entryCount)
        vm.clearFilter()
        assertEquals(DateFilter.All, vm.uiState.value.filter)
    }

    @Test
    fun records_rangeFilterNormalisesReversedDates() {
        val f = DateFilter.range(TEST_TODAY, TEST_TODAY.minusDays(3)) as DateFilter.Range
        assertEquals(TEST_TODAY.minusDays(3), f.start)
        assertEquals(DateFilter.SingleDate(TEST_TODAY), DateFilter.range(TEST_TODAY, TEST_TODAY))
    }

    @Test
    fun records_toggleSortOrder() = runTest {
        val vm = RecordsViewModel(repo, TEST_CLOCK)
        keepCollecting { vm.uiState.collect {} }
        vm.toggleSortOrder()
        assertEquals(TEST_TODAY.minusDays(6), vm.uiState.value.groups.first().date)
    }

    @Test
    fun records_deleteThenUndo_restoresSameId() = runTest {
        val vm = RecordsViewModel(repo, TEST_CLOCK)
        keepCollecting { vm.uiState.collect {} }
        val record = vm.uiState.value.groups.first().records.first()
        vm.deleteRecord(record)
        assertEquals(RecordsEvent.Deleted(record), vm.events.first())
        assertEquals(3, vm.uiState.value.entryCount)
        vm.restoreRecord(record)
        assertTrue(repo.all.any { it.id == record.id })
        assertEquals(4, vm.uiState.value.entryCount)
    }

    @Test
    fun records_deleteFailure_emitsError() = runTest {
        val vm = RecordsViewModel(repo, TEST_CLOCK)
        repo.failWith = RepositoryException("Could not delete the record. Please try again.")
        vm.deleteRecord(repo.all.first())
        assertTrue(vm.events.first() is RecordsEvent.Error)
    }

    // -------------------------------------------------------- Dashboard --

    @Test
    fun dashboard_summarisesToday() = runTest {
        val vm = DashboardViewModel(repo, settings, TEST_CLOCK)
        keepCollecting { vm.uiState.collect {} }
        val s = vm.uiState.value
        assertEquals(7850, s.todaySummary.steps)
        assertEquals(420, s.todaySummary.calories)
        assertEquals(2000, s.todaySummary.water)
        assertEquals(2, s.todayEntries.size)
        assertEquals(5000, s.todayEntries.first().steps) // Entry 1 = oldest
    }

    @Test
    fun dashboard_buildsSevenDayTrend_andStats() = runTest {
        val vm = DashboardViewModel(repo, settings, TEST_CLOCK)
        keepCollecting { vm.uiState.collect {} }
        val s = vm.uiState.value
        assertEquals(7, s.trend.size)
        assertEquals(TEST_TODAY.minusDays(6), s.trend.first().date)
        assertEquals(TEST_TODAY, s.trend.last().date)
        assertEquals(3, s.trend.count { it.hasData })
        assertEquals(((7850 + 9100 + 6400) / 3.0).let { Math.round(it).toInt() }, s.trendAverage(HealthMetric.STEPS))
        assertEquals(TEST_TODAY.minusDays(1), s.bestDay(HealthMetric.STEPS)!!.date)
        assertEquals(0, s.daysGoalMet(HealthMetric.STEPS))
        assertEquals(2, s.streak)
    }

    @Test
    fun dashboard_quickAddWater_updatesLatestEntry() = runTest {
        val vm = DashboardViewModel(repo, settings, TEST_CLOCK)
        keepCollecting { vm.uiState.collect {} }
        vm.quickAddWater(250)
        assertEquals(DashboardEvent.Message("Added 250 ml of water"), vm.events.first())
        assertEquals(2250, vm.uiState.value.todaySummary.water)
        assertEquals(2, vm.uiState.value.todayEntries.size)
    }

    @Test
    fun dashboard_quickAddWater_createsEntryWhenTodayEmpty() = runTest {
        val empty = FakeHealthRecordRepository()
        val vm = DashboardViewModel(empty, settings, TEST_CLOCK)
        keepCollecting { vm.uiState.collect {} }
        vm.quickAddWater(500)
        assertEquals(500, vm.uiState.value.todaySummary.water)
        assertEquals(1, vm.uiState.value.todayEntries.size)
    }

    @Test
    fun dashboard_goalsComeFromSettings() = runTest {
        val vm = DashboardViewModel(repo, settings, TEST_CLOCK)
        keepCollecting { vm.uiState.collect {} }
        settings.setGoal(HealthMetric.STEPS, 7000)
        assertEquals(7000, vm.uiState.value.goals.steps)
        assertEquals(2, vm.uiState.value.daysGoalMet(HealthMetric.STEPS)) // 7,850 and 9,100 (not 6,400)
    }

    // ------------------------------------------------------------- Form --

    private fun formVm(recordId: Long? = null) = RecordFormViewModel(
        SavedStateHandle(buildMap { recordId?.let { put(RecordFormViewModel.ARG_RECORD_ID, it) } }),
        repo, settings, TEST_CLOCK,
    )

    @Test
    fun form_emptySubmit_showsRequiredErrors_andDoesNotSave() = runTest {
        val vm = formVm()
        vm.save()
        val s = vm.uiState.value
        assertEquals("Please enter Steps (use 0 if none)", s.stepsError)
        assertEquals("Please enter Calories (use 0 if none)", s.caloriesError)
        assertEquals("Please enter Water intake (use 0 if none)", s.waterError)
        assertEquals(4, repo.all.size)
    }

    @Test
    fun form_allZero_showsCrossFieldError() = runTest {
        val vm = formVm()
        vm.onStepsChange("0"); vm.onCaloriesChange("0"); vm.onWaterChange("0")
        vm.save()
        assertEquals("Enter at least one value greater than 0", vm.uiState.value.formError)
    }

    @Test
    fun form_inputIsSanitisedToDigits() {
        val vm = formVm()
        vm.onStepsChange("12a3-4.5")
        assertEquals("12345", vm.uiState.value.steps)
        vm.onWaterChange("1234567")
        assertEquals("12345", vm.uiState.value.water)
    }

    @Test
    fun form_validSave_insertsRecord() = runTest {
        val vm = formVm()
        vm.onStepsChange("1150"); vm.onCaloriesChange("80"); vm.onWaterChange("500")
        vm.save()
        assertEquals(RecordFormEvent.Saved("Record added for Today"), vm.events.first())
        assertEquals(5, repo.all.size)
    }

    @Test
    fun form_edit_loadsAndUpdatesRecord() = runTest {
        val target = repo.all.first { it.steps == 6400 }
        val vm = formVm(target.id)
        assertTrue(vm.uiState.value.isEditing)
        assertEquals("6400", vm.uiState.value.steps)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        vm.onStepsChange("6600")
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        vm.save()
        assertEquals(RecordFormEvent.Saved("Record updated"), vm.events.first())
        assertEquals(6600, repo.all.first { it.id == target.id }.steps)
    }

    @Test
    fun form_waterPresetsAccumulate() {
        val vm = formVm()
        vm.addWater(250); vm.addWater(500)
        assertEquals("750", vm.uiState.value.water)
        assertNull(vm.uiState.value.waterError)
    }

    // --------------------------------------------------------- Settings --

    @Test
    fun settings_updateGoalAndTheme() = runTest {
        val vm = SettingsViewModel(settings, repo)
        keepCollecting { vm.uiState.collect {} }
        vm.updateGoal(HealthMetric.WATER, 3000)
        vm.setThemeMode(ThemeMode.DARK)
        assertEquals(3000, vm.uiState.value.goals.water)
        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
        vm.updateGoal(HealthMetric.WATER, 0) // ignored
        assertEquals(3000, vm.uiState.value.goals.water)
    }

    @Test
    fun settings_deleteAllAndRestoreSample() = runTest {
        val vm = SettingsViewModel(settings, repo)
        vm.deleteAllRecords()
        assertEquals("All records deleted", vm.messages.first())
        assertTrue(repo.all.isEmpty())
        vm.restoreSampleData()
        assertEquals("Sample data restored", vm.messages.first())
        assertTrue(repo.all.isNotEmpty())
    }
}
