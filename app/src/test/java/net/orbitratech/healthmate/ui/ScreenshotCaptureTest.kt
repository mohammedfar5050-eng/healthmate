package net.orbitratech.healthmate.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.orbitratech.healthmate.core.database.SampleData
import net.orbitratech.healthmate.feature.records.data.toDomain
import net.orbitratech.healthmate.feature.settings.data.ThemeMode
import net.orbitratech.healthmate.feature.settings.data.UserSettings
import net.orbitratech.healthmate.navigation.HealthMateApp
import net.orbitratech.healthmate.testing.TEST_TODAY
import net.orbitratech.healthmate.testing.TestAppContainer
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders key screens to PNG files for documentation and visual review.
 * Skipped by default; run with:
 *
 *     ./gradlew testDebugUnitTest --tests "*ScreenshotCaptureTest*" -PcaptureScreenshots=true
 *
 * Images are written to app/build/screenshots/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotCaptureTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File(System.getProperty("screenshotDir") ?: "build/screenshots")

    @Before
    fun onlyWhenRequested() {
        assumeTrue(System.getProperty("captureScreenshots") == "true")
        outputDir.mkdirs()
    }

    private fun launch(theme: ThemeMode = ThemeMode.LIGHT) {
        val container = TestAppContainer(
            records = SampleData.generate(TEST_TODAY).mapIndexed { i, e -> e.copy(id = i + 1L).toDomain() },
            settings = UserSettings(themeMode = theme),
        )
        compose.setContent { HealthMateApp(container) }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        // Draw the window's view hierarchy (including Compose) onto a bitmap.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(outputDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun dashboard() {
        launch()
        shot("01_dashboard")
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Daily average"))
        shot("02_dashboard_weekly_chart")
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Today's entries"))
        shot("03_dashboard_entries")
    }

    @Test
    fun records() {
        launch()
        compose.onNodeWithTag("nav_records").performClick()
        shot("04_records")
        compose.onNodeWithTag("filter_Last 7 days").performClick()
        shot("05_records_filtered")
    }

    @Test
    fun form() {
        launch()
        compose.onNodeWithTag("fab_log_activity").performClick()
        shot("06_add_record")
        compose.onNodeWithTag("field_steps").performTextReplacement("250000")
        compose.onNodeWithTag("save_button").performClick()
        shot("07_add_record_validation")
    }

    @Test
    fun editAndSettings() {
        launch()
        compose.onNodeWithTag("nav_records").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Steps:", substring = true)[2].performClick()
        shot("08_edit_record")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("nav_settings").performClick()
        shot("09_settings")
        compose.onNodeWithTag("goal_water").performClick()
        shot("10_settings_edit_goal")
    }

    @Test
    fun dark() {
        launch(ThemeMode.DARK)
        shot("11_dashboard_dark")
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Daily average"))
        shot("12_dashboard_dark_chart")
    }
}
