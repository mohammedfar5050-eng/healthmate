package net.orbitratech.healthmate.feature.records.ui.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.core.util.Validators
import net.orbitratech.healthmate.feature.records.data.HealthRecordRepository
import net.orbitratech.healthmate.feature.records.data.RepositoryException
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import net.orbitratech.healthmate.feature.settings.data.DailyGoals
import net.orbitratech.healthmate.feature.settings.data.SettingsRepository
import java.time.Clock
import java.time.LocalDate

/**
 * State of the Add/Edit form. Field errors are derived from the current input
 * and only revealed after the first save attempt ([showErrors]), so the user
 * is not shown errors before they have had a chance to type.
 */
data class RecordFormUiState(
    val today: LocalDate,
    val date: LocalDate,
    val steps: String = "",
    val calories: String = "",
    val water: String = "",
    val original: HealthRecord? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showErrors: Boolean = false,
) {
    val dateError: String? get() = if (showErrors) Validators.recordDate(date, today) else null
    val stepsError: String? get() = if (showErrors) Validators.steps(steps) else null
    val caloriesError: String? get() = if (showErrors) Validators.calories(calories) else null
    val waterError: String? get() = if (showErrors) Validators.water(water) else null

    /** Cross-field error: shown when every value is zero. */
    val formError: String?
        get() = if (showErrors && fieldsValid) {
            Validators.atLeastOneActivity(
                Validators.parse(steps), Validators.parse(calories), Validators.parse(water),
            )
        } else {
            null
        }

    private val fieldsValid: Boolean
        get() = Validators.recordDate(date, today) == null &&
            Validators.steps(steps) == null &&
            Validators.calories(calories) == null &&
            Validators.water(water) == null

    val isValid: Boolean
        get() = fieldsValid && Validators.atLeastOneActivity(
            Validators.parse(steps), Validators.parse(calories), Validators.parse(water),
        ) == null

    fun toRecord() = HealthRecord(
        id = original?.id ?: 0,
        date = date,
        steps = Validators.parse(steps),
        calories = Validators.parse(calories),
        water = Validators.parse(water),
    )

    val hasUnsavedChanges: Boolean
        get() = when {
            isLoading -> false
            original == null -> listOf(steps, calories, water).any { it.isNotBlank() }
            else -> toRecord() != original
        }
}

sealed interface RecordFormEvent {
    data class Saved(val message: String) : RecordFormEvent
    data class Deleted(val record: HealthRecord) : RecordFormEvent
    data class Error(val message: String) : RecordFormEvent
}

/** ViewModel (MVVM) for the Add / Edit Record screen. */
class RecordFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: HealthRecordRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val recordId: Long = savedStateHandle.get<Long>(ARG_RECORD_ID) ?: NEW_RECORD
    private val initialDate: LocalDate = savedStateHandle.get<String>(ARG_DATE)
        ?.let { runCatching { DateUtils.fromDbString(it) }.getOrNull() }
        ?: LocalDate.now(clock)

    private val _uiState = MutableStateFlow(
        RecordFormUiState(
            today = LocalDate.now(clock),
            date = initialDate,
            isEditing = recordId != NEW_RECORD,
            isLoading = recordId != NEW_RECORD,
        ),
    )
    val uiState: StateFlow<RecordFormUiState> = _uiState.asStateFlow()

    val goals: StateFlow<DailyGoals> = settingsRepository.settings
        .map { it.goals }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyGoals())

    private val _events = Channel<RecordFormEvent>(Channel.BUFFERED)
    val events: Flow<RecordFormEvent> = _events.receiveAsFlow()

    init {
        if (recordId != NEW_RECORD) loadRecord(recordId)
    }

    private fun loadRecord(id: Long) {
        viewModelScope.launch {
            try {
                val record = repository.getRecord(id)
                if (record == null) {
                    _events.send(RecordFormEvent.Error("This record no longer exists."))
                    _uiState.update { it.copy(isLoading = false, isEditing = false) }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        date = record.date,
                        steps = record.steps.toString(),
                        calories = record.calories.toString(),
                        water = record.water.toString(),
                        original = record,
                        isLoading = false,
                    )
                }
            } catch (e: RepositoryException) {
                _events.send(RecordFormEvent.Error(e.message ?: "Could not load the record."))
            }
        }
    }

    // ------------------------------------------------------------- input --

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }

    fun onStepsChange(value: String) = _uiState.update { it.copy(steps = value.digitsOnly(6)) }

    fun onCaloriesChange(value: String) = _uiState.update { it.copy(calories = value.digitsOnly(5)) }

    fun onWaterChange(value: String) = _uiState.update { it.copy(water = value.digitsOnly(5)) }

    /** Adds a preset amount of water to the water field. */
    fun addWater(ml: Int) = _uiState.update {
        val total = (Validators.parse(it.water) + ml).coerceAtMost(Validators.MAX_WATER_ML)
        it.copy(water = total.toString())
    }

    // ---------------------------------------------------------- commands --

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(showErrors = true) }
        if (!state.isValid) return

        val record = state.toRecord()
        if (state.original != null && record == state.original) {
            viewModelScope.launch { _events.send(RecordFormEvent.Saved("No changes to save")) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (record.isNew) repository.addRecord(record) else repository.updateRecord(record)
                val message = if (record.isNew) {
                    "Record added for ${DateUtils.friendlyLabel(record.date, state.today)}"
                } else {
                    "Record updated"
                }
                _events.send(RecordFormEvent.Saved(message))
            } catch (e: RepositoryException) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(RecordFormEvent.Error(e.message ?: "Could not save the record."))
            }
        }
    }

    fun delete() {
        val original = _uiState.value.original ?: return
        viewModelScope.launch {
            try {
                repository.deleteRecord(original.id)
                _events.send(RecordFormEvent.Deleted(original))
            } catch (e: RepositoryException) {
                _events.send(RecordFormEvent.Error(e.message ?: "Could not delete the record."))
            }
        }
    }

    companion object {
        const val ARG_RECORD_ID = "recordId"
        const val ARG_DATE = "date"
        const val NEW_RECORD = -1L
    }
}

private fun String.digitsOnly(maxLength: Int) = filter { it.isDigit() }.take(maxLength)
