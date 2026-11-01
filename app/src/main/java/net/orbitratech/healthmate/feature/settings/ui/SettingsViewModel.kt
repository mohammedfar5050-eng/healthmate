package net.orbitratech.healthmate.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.orbitratech.healthmate.feature.records.data.HealthRecordRepository
import net.orbitratech.healthmate.feature.records.data.RepositoryException
import net.orbitratech.healthmate.feature.records.model.HealthMetric
import net.orbitratech.healthmate.feature.settings.data.SettingsRepository
import net.orbitratech.healthmate.feature.settings.data.ThemeMode
import net.orbitratech.healthmate.feature.settings.data.UserSettings

/** ViewModel (MVVM) for Settings: goals, theme and data management. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val recordRepository: HealthRecordRepository,
) : ViewModel() {

    val uiState: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun updateGoal(metric: HealthMetric, value: Int) {
        if (value <= 0) return
        viewModelScope.launch { settingsRepository.setGoal(metric, value) }
    }

    fun resetGoals() {
        viewModelScope.launch {
            settingsRepository.resetGoals()
            _messages.send("Goals reset to recommended values")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun restoreSampleData() = runDataAction("Sample data restored") {
        recordRepository.resetWithSampleData()
    }

    fun deleteAllRecords() = runDataAction("All records deleted") { recordRepository.clearAll() }

    private fun runDataAction(success: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            val message = try {
                action()
                success
            } catch (e: RepositoryException) {
                e.message ?: "Something went wrong."
            }
            _messages.send(message)
        }
    }
}

/** Activity-level ViewModel exposing the theme so the whole app can react to it. */
class AppViewModel(settingsRepository: SettingsRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
}
