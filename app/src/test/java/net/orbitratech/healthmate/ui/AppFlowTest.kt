package net.orbitratech.healthmate.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.orbitratech.healthmate.navigation.HealthMateApp
import net.orbitratech.healthmate.testing.TEST_TODAY
import net.orbitratech.healthmate.testing.TestAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI tests of the main user journeys, run on the JVM with
 * Robolectric. Each test corresponds to a documented test case (TC-xx).
 */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule val compose = createComposeRule()

    private fun launch(container: TestAppContainer = TestAppContainer()): TestAppContainer {
        compose.setContent { HealthMateApp(container) }
        compose.waitForIdle()
        return container
    }

    private fun scrollDashboardTo(text: String) {
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText(text, substring = true))
    }

    private fun openAddForm() {
        compose.onNodeWithTag("fab_log_activity").performClick()
        compose.onNodeWithText("Add Record").assertExists()
    }

    private fun clickSave() = compose.onNodeWithTag("save_button").performClick()

    private fun waitForText(text: String) = compose.waitUntil(3_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun tc01_dashboardShowsTodaysTotals() {
        launch()
        compose.onNodeWithText("Today – 18 August 2026").assertExists()
        compose.onNodeWithText("7,850", substring = true).assertExists()
        compose.onNodeWithText("420", substring = true).assertExists()
        compose.onNodeWithText("2,000", substring = true).assertExists()
        scrollDashboardTo("Last 7 days")
        compose.onNodeWithText("Last 7 days").assertExists()
    }

    @Test
    fun tc02_emptyFormShowsRequiredFieldErrors() {
        val container = launch()
        openAddForm()
        clickSave()
        compose.onNodeWithText("Please enter Steps (use 0 if none)").assertExists()
        compose.onNodeWithText("Please enter Calories (use 0 if none)").assertExists()
        compose.onNodeWithText("Please enter Water intake (use 0 if none)").assertExists()
        compose.onNodeWithText("Add Record").assertExists()
        assertEquals(4, container.healthRecordRepository.all.size)
    }

    @Test
    fun tc03_outOfRangeAndAllZeroValuesAreRejected() {
        val container = launch()
        openAddForm()
        compose.onNodeWithTag("field_steps").performTextReplacement("250000")
        compose.onNodeWithTag("field_calories").performTextReplacement("0")
        compose.onNodeWithTag("field_water").performTextReplacement("0")
        clickSave()
        compose.onNodeWithText("Steps looks too high (max 100,000)").assertExists()

        compose.onNodeWithTag("field_steps").performTextReplacement("0")
        clickSave()
        compose.onNodeWithText("Enter at least one value greater than 0").assertExists()
        assertEquals(4, container.healthRecordRepository.all.size)
    }

    @Test
    fun tc04_addingValidRecordUpdatesDashboard() {
        val container = launch()
        openAddForm()
        compose.onNodeWithTag("field_steps").performTextReplacement("1150")
        compose.onNodeWithTag("field_calories").performTextReplacement("80")
        compose.onNodeWithTag("field_water").performTextReplacement("500")
        clickSave()
        compose.waitForIdle()

        compose.onNodeWithText("Add Record").assertDoesNotExist()
        assertEquals(5, container.healthRecordRepository.all.size)
        // 7,850 + 1,150 = 9,000 steps and 2,000 + 500 = 2,500 ml water.
        compose.onNodeWithText("9,000", substring = true).assertExists()
        compose.onAllNodesWithText("2,500", substring = true).fetchSemanticsNodes().let {
            assertTrue(it.isNotEmpty())
        }
    }

    @Test
    fun tc05_recordsListGroupsAndFiltersByDate() {
        launch()
        compose.onNodeWithTag("nav_records").performClick()
        compose.onNodeWithText("Health Records").assertExists()
        compose.onNodeWithText("Yesterday").assertExists()
        compose.onAllNodesWithContentDescription("Record options").assertCountEquals(4)

        compose.onNodeWithTag("filter_Today").performClick()
        compose.onAllNodesWithContentDescription("Record options").assertCountEquals(2)
        compose.onNodeWithText("2 entries · 1 day").assertExists()

        compose.onNodeWithTag("filter_All").performClick()
        compose.onAllNodesWithContentDescription("Record options").assertCountEquals(4)
    }

    @Test
    fun tc06_editingRecordSavesNewValues() {
        val container = launch()
        compose.onNodeWithTag("nav_records").performClick()
        compose.onNodeWithContentDescription("Steps: 6,400 steps").performClick()
        compose.onNodeWithText("Edit Record").assertExists()

        compose.onNodeWithTag("field_steps").performTextReplacement("6600")
        clickSave()
        compose.waitForIdle()

        compose.onNodeWithText("Edit Record").assertDoesNotExist()
        assertTrue(container.healthRecordRepository.all.any { it.steps == 6600 })
        compose.onNodeWithContentDescription("Steps: 6,600 steps").assertExists()
    }

    @Test
    fun tc07_deleteAsksForConfirmationAndSupportsUndo() {
        val container = launch()
        compose.onNodeWithTag("nav_records").performClick()

        compose.onAllNodesWithContentDescription("Record options")[3].performClick()
        compose.onNodeWithTag("menu_delete").performClick()
        compose.onNodeWithText("Delete record?").assertExists()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("confirm_button").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(3, container.healthRecordRepository.all.size)
        compose.onNodeWithText("Record deleted").assertExists()

        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(4, container.healthRecordRepository.all.size)
    }

    @Test
    fun tc08_quickAddWaterIncreasesTodaysWater() {
        val container = launch()
        fun todayWater() = container.healthRecordRepository.all
            .filter { it.date == TEST_TODAY }
            .sumOf { it.water }
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("quick_water_250"))
        compose.onNodeWithTag("quick_water_250").performClick()
        waitForText("Added 250 ml of water")
        assertEquals(2250, todayWater())
        compose.onNodeWithText("2,250", substring = true).assertExists()

        // Undo from the snackbar takes the water back off.
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(3_000) { todayWater() == 2000 }
    }

    @Test
    fun tc09_dailyGoalCanBeChangedWithValidation() {
        launch()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("goal_water").performClick()
        compose.onNodeWithTag("goal_field").performTextReplacement("0")
        compose.onNodeWithTag("goal_save").performClick()
        compose.onNodeWithText("Goal cannot be less than 1").assertExists()

        compose.onNodeWithTag("goal_field").performTextReplacement("3000")
        compose.onNodeWithTag("goal_save").performClick()
        compose.onNodeWithText("3,000 ml per day").assertExists()
    }

    @Test
    fun tc10_emptyDatabaseShowsHelpfulEmptyStates() {
        launch(TestAppContainer(records = emptyList()))
        scrollDashboardTo("Nothing logged yet today")
        compose.onNodeWithText("Nothing logged yet today", substring = true).assertExists()

        compose.onNodeWithTag("nav_records").performClick()
        compose.onNodeWithText("No records yet").assertExists()
    }
}
