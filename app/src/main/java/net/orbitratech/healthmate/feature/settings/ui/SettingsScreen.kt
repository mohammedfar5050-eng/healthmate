package net.orbitratech.healthmate.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import net.orbitratech.healthmate.core.designsystem.component.SectionHeader
import net.orbitratech.healthmate.core.util.Formatters
import net.orbitratech.healthmate.core.util.Validators
import net.orbitratech.healthmate.feature.records.model.HealthMetric
import net.orbitratech.healthmate.feature.settings.data.ThemeMode
import net.orbitratech.healthmate.feature.settings.data.UserSettings

private enum class PendingAction { RESTORE, DELETE_ALL }

/** Settings: daily goals, appearance and data management. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    ObserveEvents(viewModel.messages) { snackbar.show(it) }
    SettingsContent(
        settings = settings,
        onUpdateGoal = viewModel::updateGoal,
        onResetGoals = viewModel::resetGoals,
        onThemeChange = viewModel::setThemeMode,
        onRestoreSample = viewModel::restoreSampleData,
        onDeleteAll = viewModel::deleteAllRecords,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: UserSettings,
    onUpdateGoal: (HealthMetric, Int) -> Unit,
    onResetGoals: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onRestoreSample: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    var editingGoal by rememberSaveable { mutableStateOf<HealthMetric?>(null) }
    var pendingAction by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val listColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeader("Daily goals") }
            item {
                Card(colors = cardColors) {
                    HealthMetric.entries.forEachIndexed { index, metric ->
                        if (editingGoal == metric) {
                            GoalEditor(
                                metric = metric,
                                initial = settings.goals.goalFor(metric),
                                onCancel = { editingGoal = null },
                                onSave = {
                                    onUpdateGoal(metric, it)
                                    editingGoal = null
                                },
                            )
                        } else {
                            ListItem(
                                headlineContent = { Text(metric.label) },
                                supportingContent = {
                                    Text("${Formatters.number(settings.goals.goalFor(metric))} ${metric.unit} per day")
                                },
                                leadingContent = { MetricIcon(metric) },
                                trailingContent = { AppIcon(R.drawable.ic_edit, "Edit ${metric.shortLabel} goal") },
                                colors = listColors,
                                modifier = Modifier
                                    .testTag("goal_${metric.name.lowercase()}")
                                    .clickable { editingGoal = metric },
                            )
                        }
                        if (index < HealthMetric.entries.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    TextButton(onClick = onResetGoals) { Text("Reset to recommended goals") }
                }
            }

            item { SectionHeader("Appearance") }
            item {
                Card(colors = cardColors) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { onThemeChange(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                    icon = {
                                        AppIcon(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> R.drawable.ic_brightness_auto
                                                ThemeMode.LIGHT -> R.drawable.ic_light_mode
                                                ThemeMode.DARK -> R.drawable.ic_dark_mode
                                            },
                                            null,
                                            Modifier.size(18.dp),
                                        )
                                    },
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Data") }
            item {
                Card(colors = cardColors) {
                    ListItem(
                        headlineContent = { Text("Restore sample data") },
                        supportingContent = { Text("Replace records with demo data for testing") },
                        leadingContent = { AppIcon(R.drawable.ic_restore, null) },
                        colors = listColors,
                        modifier = Modifier.clickable { pendingAction = PendingAction.RESTORE },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    ListItem(
                        headlineContent = { Text("Delete all records", color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("Permanently remove every record") },
                        leadingContent = {
                            AppIcon(R.drawable.ic_delete_forever, null, tint = MaterialTheme.colorScheme.error)
                        },
                        colors = listColors,
                        modifier = Modifier.clickable { pendingAction = PendingAction.DELETE_ALL },
                    )
                }
            }

            item { SectionHeader("About") }
            item {
                Card(colors = cardColors) {
                    ListItem(
                        headlineContent = { Text("HealthMate") },
                        supportingContent = {
                            Text("Personal Health & Wellness Tracker\nVersion 1.0.0 · Data is stored only on this device.")
                        },
                        leadingContent = {
                            AppIcon(R.drawable.ic_favorite, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = listColors,
                        modifier = Modifier.clickable { showAbout = true },
                    )
                }
            }
        }
    }

    when (pendingAction) {
        PendingAction.RESTORE -> ConfirmDialog(
            title = "Restore sample data?",
            message = "All current records will be replaced with demo records covering the last three weeks.",
            confirmLabel = "Restore",
            icon = R.drawable.ic_restore,
            onConfirm = {
                pendingAction = null
                onRestoreSample()
            },
            onDismiss = { pendingAction = null },
        )
        PendingAction.DELETE_ALL -> ConfirmDialog(
            title = "Delete all records?",
            message = "This permanently removes every health record from this device. This cannot be undone.",
            confirmLabel = "Delete all",
            icon = R.drawable.ic_delete_forever,
            destructive = true,
            onConfirm = {
                pendingAction = null
                onDeleteAll()
            },
            onDismiss = { pendingAction = null },
        )
        null -> Unit
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { AppIcon(R.drawable.ic_favorite, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("HealthMate 1.0.0") },
            text = {
                Text(
                    "Track your daily steps, calories burned and water intake. " +
                        "Records are stored locally in an SQLite database (Room).",
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
        )
    }
}

/**
 * Inline editor shown in place of a goal row: a validated numeric field with
 * Save / Cancel. Editing in place keeps the user in context (no dialog).
 */
@Composable
private fun GoalEditor(metric: HealthMetric, initial: Int, onCancel: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val max = when (metric) {
        HealthMetric.STEPS -> Validators.MAX_STEPS
        HealthMetric.CALORIES -> Validators.MAX_CALORIES
        HealthMetric.WATER -> Validators.MAX_WATER_ML
    }
    val error = Validators.goal(text, max)
    val submit = {
        showError = true
        if (error == null) onSave(Validators.parse(text))
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { value -> text = value.filter { it.isDigit() }.take(6) },
            label = { Text("${metric.shortLabel} goal per day") },
            leadingIcon = { MetricIcon(metric, size = 32.dp) },
            suffix = { Text(metric.unit) },
            isError = showError && error != null,
            supportingText = { Text(if (showError && error != null) error else "Between 1 and ${Formatters.number(max)}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("goal_field"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = submit, modifier = Modifier.testTag("goal_save")) { Text("Save") }
        }
    }
}
