package net.orbitratech.healthmate.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.orbitratech.healthmate.feature.records.model.HealthMetric

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** The user's daily targets, shown as progress rings on the dashboard. */
data class DailyGoals(
    val steps: Int = DEFAULT_STEPS,
    val calories: Int = DEFAULT_CALORIES,
    val water: Int = DEFAULT_WATER,
) {
    fun goalFor(metric: HealthMetric): Int = when (metric) {
        HealthMetric.STEPS -> steps
        HealthMetric.CALORIES -> calories
        HealthMetric.WATER -> water
    }

    /** Progress towards the goal as a fraction (may exceed 1). */
    fun progress(metric: HealthMetric, value: Int): Float {
        val goal = goalFor(metric)
        return if (goal <= 0) 0f else value.toFloat() / goal
    }

    fun with(metric: HealthMetric, value: Int): DailyGoals = when (metric) {
        HealthMetric.STEPS -> copy(steps = value)
        HealthMetric.CALORIES -> copy(calories = value)
        HealthMetric.WATER -> copy(water = value)
    }

    companion object {
        const val DEFAULT_STEPS = 10_000
        const val DEFAULT_CALORIES = 500
        const val DEFAULT_WATER = 2_500
    }
}

data class UserSettings(
    val goals: DailyGoals = DailyGoals(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/** Persists lightweight user preferences. Health records live in SQLite. */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setGoal(metric: HealthMetric, value: Int)

    suspend fun resetGoals()

    suspend fun setThemeMode(mode: ThemeMode)
}

/**
 * [SettingsRepository] backed by Jetpack DataStore (Preferences). DataStore is
 * asynchronous and transactional, replacing the older SharedPreferences API.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val STEPS_GOAL = intPreferencesKey("goal_steps")
        val CALORIES_GOAL = intPreferencesKey("goal_calories")
        val WATER_GOAL = intPreferencesKey("goal_water")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    private fun keyFor(metric: HealthMetric) = when (metric) {
        HealthMetric.STEPS -> Keys.STEPS_GOAL
        HealthMetric.CALORIES -> Keys.CALORIES_GOAL
        HealthMetric.WATER -> Keys.WATER_GOAL
    }

    override val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            goals = DailyGoals(
                steps = prefs[Keys.STEPS_GOAL] ?: DailyGoals.DEFAULT_STEPS,
                calories = prefs[Keys.CALORIES_GOAL] ?: DailyGoals.DEFAULT_CALORIES,
                water = prefs[Keys.WATER_GOAL] ?: DailyGoals.DEFAULT_WATER,
            ),
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME_MODE] }
                ?: ThemeMode.SYSTEM,
        )
    }

    override suspend fun setGoal(metric: HealthMetric, value: Int) {
        dataStore.edit { it[keyFor(metric)] = value }
    }

    override suspend fun resetGoals() {
        dataStore.edit { prefs -> HealthMetric.entries.forEach { prefs.remove(keyFor(it)) } }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
