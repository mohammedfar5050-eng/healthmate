package net.orbitratech.healthmate.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.orbitratech.healthmate.MainActivity
import net.orbitratech.healthmate.core.util.DateUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Smoke test of the production wiring: launches the real [MainActivity] with
 * the real Application, Room database (seeded on first creation) and
 * DataStore – no fakes – and performs a full add-record round trip.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun realAppLaunches_showsSeededData_andSavesToSqlite() {
        val today = LocalDate.now()
        waitForText("Today – ${DateUtils.formatLong(today)}")
        // Seeded sample data for today: 5,200 + 2,650 steps.
        waitForText("7,850")

        compose.onNodeWithTag("fab_log_activity").performClick()
        compose.onNodeWithTag("field_steps").performTextReplacement("1150")
        compose.onNodeWithTag("field_calories").performTextReplacement("80")
        compose.onNodeWithTag("field_water").performTextReplacement("500")
        compose.onNodeWithTag("save_button").performClick()

        // Room re-emits the query after the INSERT, so the dashboard updates.
        waitForText("9,000")
        compose.onNodeWithTag("dashboard_list").assertExists()
        compose.onNodeWithText("Add Record").assertDoesNotExist()
    }
}
