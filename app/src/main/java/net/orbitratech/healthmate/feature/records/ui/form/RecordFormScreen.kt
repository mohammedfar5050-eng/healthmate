package net.orbitratech.healthmate.feature.records.ui.form

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.orbitratech.healthmate.R
import net.orbitratech.healthmate.core.designsystem.component.AppIcon
import net.orbitratech.healthmate.core.designsystem.component.ConfirmDialog
import net.orbitratech.healthmate.core.designsystem.component.LocalAppSnackbar
import net.orbitratech.healthmate.core.designsystem.component.MetricIcon
import net.orbitratech.healthmate.core.designsystem.component.ObserveEvents
import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.core.util.Formatters
import net.orbitratech.healthmate.feature.records.model.HealthMetric
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import net.orbitratech.healthmate.feature.records.ui.list.SingleDatePickerDialog
import net.orbitratech.healthmate.feature.settings.data.DailyGoals

/** Water amounts offered as one-tap presets (ml). */
val WATER_PRESETS = listOf(250, 500, 750)

/**
 * Add / Edit Record screen. One screen serves both modes; the ViewModel is
 * told which record to edit through the navigation argument.
 */
@Composable
fun RecordFormScreen(
    viewModel: RecordFormViewModel,
    onClose: () -> Unit,
    onDeleted: (HealthRecord) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            is RecordFormEvent.Saved -> {
                onClose()
                snackbar.show(event.message)
            }
            is RecordFormEvent.Deleted -> onDeleted(event.record)
            is RecordFormEvent.Error -> snackbar.show(event.message)
        }
    }

    RecordFormContent(
        state = state,
        goals = goals,
        onDateChange = viewModel::onDateChange,
        onStepsChange = viewModel::onStepsChange,
        onCaloriesChange = viewModel::onCaloriesChange,
        onWaterChange = viewModel::onWaterChange,
        onAddWater = viewModel::addWater,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onClose = onClose,
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RecordFormContent(
    state: RecordFormUiState,
    goals: DailyGoals,
    onDateChange: (java.time.LocalDate) -> Unit,
    onStepsChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onWaterChange: (String) -> Unit,
    onAddWater: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val requestClose = {
        if (state.hasUnsavedChanges && !state.isSaving) showDiscardDialog = true else onClose()
    }
    // Ask before discarding unsaved input when the system back gesture is used.
    BackHandler(enabled = state.hasUnsavedChanges && !state.isSaving) { showDiscardDialog = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit Record" else "Add Record") },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        AppIcon(
                            if (state.isEditing) R.drawable.ic_arrow_back else R.drawable.ic_close,
                            contentDescription = if (state.isEditing) "Back" else "Close",
                        )
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !state.isSaving) {
                            AppIcon(R.drawable.ic_delete, "Delete record")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (state.isEditing) {
                    "Update the values for this entry."
                } else {
                    "Log your activity. You can add several entries per day – the dashboard shows the daily total."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DateField(
                date = state.date,
                error = state.dateError,
                onClick = { showDatePicker = true },
            )

            MetricField(
                metric = HealthMetric.STEPS,
                value = state.steps,
                error = state.stepsError,
                goal = goals.steps,
                onValueChange = onStepsChange,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )
            MetricField(
                metric = HealthMetric.CALORIES,
                value = state.calories,
                error = state.caloriesError,
                goal = goals.calories,
                onValueChange = onCaloriesChange,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )
            MetricField(
                metric = HealthMetric.WATER,
                value = state.water,
                error = state.waterError,
                goal = goals.water,
                onValueChange = onWaterChange,
                imeAction = ImeAction.Done,
                onNext = {
                    focusManager.clearFocus()
                    onSave()
                },
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WATER_PRESETS.forEach { ml ->
                    AssistChip(
                        onClick = { onAddWater(ml) },
                        label = { Text("$ml ml") },
                        leadingIcon = {
                            AppIcon(R.drawable.ic_add, null, Modifier.size(18.dp), tint = HealthMetric.WATER.color)
                        },
                    )
                }
            }

            AnimatedVisibility(visible = state.formError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(R.drawable.ic_info, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(state.formError.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSave()
                },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                } else {
                    AppIcon(if (state.isEditing) R.drawable.ic_check else R.drawable.ic_add, null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (state.isEditing) "Save changes" else "Add record")
            }
        }
    }

    if (showDatePicker) {
        SingleDatePickerDialog(
            initial = state.date,
            today = state.today,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                showDatePicker = false
                onDateChange(it)
            },
        )
    }
    if (showDiscardDialog) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "You have unsaved changes. Are you sure you want to leave?",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            destructive = true,
            onConfirm = {
                showDiscardDialog = false
                onClose()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete record?",
            message = "This entry for ${DateUtils.formatLong(state.date)} will be permanently removed.",
            confirmLabel = "Delete",
            icon = R.drawable.ic_delete,
            destructive = true,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/** Read-only text field that opens the date picker when tapped. */
@Composable
private fun DateField(date: java.time.LocalDate, error: String?, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) onClick() }
    }
    OutlinedTextField(
        value = DateUtils.formatMedium(date),
        onValueChange = {},
        readOnly = true,
        label = { Text("Date") },
        leadingIcon = { AppIcon(R.drawable.ic_event, null) },
        trailingIcon = { AppIcon(R.drawable.ic_arrow_drop_down, "Choose date") },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("field_date"),
    )
}

@Composable
private fun MetricField(
    metric: HealthMetric,
    value: String,
    error: String?,
    goal: Int,
    onValueChange: (String) -> Unit,
    onNext: () -> Unit,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(metric.label) },
        placeholder = { Text("0") },
        leadingIcon = { MetricIcon(metric, size = 32.dp) },
        suffix = { Text(metric.unit) },
        isError = error != null,
        supportingText = {
            Text(error ?: "Daily goal: ${Formatters.number(goal)} ${metric.unit}")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onNext() }),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("field_${metric.name.lowercase()}"),
    )
}
