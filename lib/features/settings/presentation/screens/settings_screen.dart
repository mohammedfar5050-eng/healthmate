import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../../core/constants/app_constants.dart';
import '../../../../core/utils/formatters.dart';
import '../../../../core/utils/validators.dart';
import '../../../../core/widgets/dialogs.dart';
import '../../../../core/widgets/state_views.dart';
import '../../../health_records/data/models/health_metric.dart';
import '../../../health_records/presentation/viewmodels/health_records_viewmodel.dart';
import '../../../health_records/presentation/widgets/metric_badge.dart';
import '../viewmodels/settings_viewmodel.dart';

/// Settings: daily goals, appearance and data management.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  static int _maxFor(HealthMetric metric) => switch (metric) {
        HealthMetric.steps => AppConstants.maxSteps,
        HealthMetric.calories => AppConstants.maxCalories,
        HealthMetric.water => AppConstants.maxWaterMl,
      };

  Future<void> _editGoal(BuildContext context, HealthMetric metric) async {
    final settings = context.read<SettingsViewModel>();
    final value = await showDialog<int>(
      context: context,
      builder: (_) => _GoalDialog(
        metric: metric,
        initial: settings.goals.goalFor(metric),
        max: _maxFor(metric),
      ),
    );
    if (value != null) await settings.updateGoal(metric, value);
  }

  Future<void> _resetSample(BuildContext context) async {
    final confirmed = await showConfirmDialog(
      context,
      title: 'Restore sample data?',
      message: 'All current records will be replaced with demo records '
          'covering the last three weeks.',
      confirmLabel: 'Restore',
      icon: Icons.restore_rounded,
    );
    if (!confirmed || !context.mounted) return;
    final error = await context.read<HealthRecordsViewModel>().resetWithSampleData();
    if (!context.mounted) return;
    showAppSnackBar(context, error ?? 'Sample data restored', isError: error != null);
  }

  Future<void> _clearAll(BuildContext context) async {
    final confirmed = await showConfirmDialog(
      context,
      title: 'Delete all records?',
      message: 'This permanently removes every health record from this device. '
          'This cannot be undone.',
      confirmLabel: 'Delete all',
      destructive: true,
      icon: Icons.delete_forever_rounded,
    );
    if (!confirmed || !context.mounted) return;
    final error = await context.read<HealthRecordsViewModel>().clearAll();
    if (!context.mounted) return;
    showAppSnackBar(context, error ?? 'All records deleted', isError: error != null);
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsViewModel>();
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
        children: [
          const SectionHeader(title: 'Daily goals'),
          Card(
            child: Column(
              children: [
                for (final metric in HealthMetric.values) ...[
                  ListTile(
                    key: Key('goal-${metric.name}'),
                    leading: MetricIcon(metric),
                    title: Text(metric.label),
                    subtitle: Text(
                      '${AppFormatters.number(settings.goals.goalFor(metric))} ${metric.unit} per day',
                    ),
                    trailing: const Icon(Icons.edit_outlined),
                    onTap: () => _editGoal(context, metric),
                  ),
                  if (metric != HealthMetric.values.last)
                    const Divider(indent: 72),
                ],
              ],
            ),
          ),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              onPressed: settings.resetGoals,
              child: const Text('Reset to recommended goals'),
            ),
          ),
          const SectionHeader(title: 'Appearance'),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Theme', style: theme.textTheme.titleSmall),
                  const SizedBox(height: 12),
                  SegmentedButton<ThemeMode>(
                    segments: const [
                      ButtonSegment(
                        value: ThemeMode.system,
                        icon: Icon(Icons.brightness_auto_rounded),
                        label: Text('System'),
                      ),
                      ButtonSegment(
                        value: ThemeMode.light,
                        icon: Icon(Icons.light_mode_rounded),
                        label: Text('Light'),
                      ),
                      ButtonSegment(
                        value: ThemeMode.dark,
                        icon: Icon(Icons.dark_mode_rounded),
                        label: Text('Dark'),
                      ),
                    ],
                    selected: {settings.themeMode},
                    onSelectionChanged: (s) => settings.setThemeMode(s.first),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          const SectionHeader(title: 'Data'),
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.restore_rounded),
                  title: const Text('Restore sample data'),
                  subtitle: const Text('Replace records with demo data for testing'),
                  onTap: () => _resetSample(context),
                ),
                const Divider(indent: 72),
                ListTile(
                  leading: Icon(Icons.delete_forever_rounded, color: theme.colorScheme.error),
                  title: Text('Delete all records',
                      style: TextStyle(color: theme.colorScheme.error)),
                  subtitle: const Text('Permanently remove every record'),
                  onTap: () => _clearAll(context),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          const SectionHeader(title: 'About'),
          Card(
            child: ListTile(
              leading: Icon(Icons.favorite_rounded, color: theme.colorScheme.primary),
              title: const Text(AppConstants.appName),
              subtitle: const Text(
                '${AppConstants.appTagline}\nVersion ${AppConstants.appVersion} · '
                'Data is stored only on this device.',
              ),
              isThreeLine: true,
              onTap: () => showAboutDialog(
                context: context,
                applicationName: AppConstants.appName,
                applicationVersion: AppConstants.appVersion,
                applicationIcon: Icon(Icons.favorite_rounded,
                    color: theme.colorScheme.primary, size: 40),
                children: const [
                  Text('Track your daily steps, calories burned and water '
                      'intake. Records are stored locally with SQLite.'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Dialog with a validated numeric field for editing one daily goal.
class _GoalDialog extends StatefulWidget {
  const _GoalDialog({required this.metric, required this.initial, required this.max});

  final HealthMetric metric;
  final int initial;
  final int max;

  @override
  State<_GoalDialog> createState() => _GoalDialogState();
}

class _GoalDialogState extends State<_GoalDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _controller =
      TextEditingController(text: widget.initial.toString());

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    if (_formKey.currentState?.validate() ?? false) {
      Navigator.of(context).pop(Validators.parseInt(_controller.text));
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      icon: Icon(widget.metric.icon, color: widget.metric.color),
      title: Text('${widget.metric.shortLabel} goal'),
      content: Form(
        key: _formKey,
        child: TextFormField(
          key: const Key('goal-field'),
          controller: _controller,
          autofocus: true,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          validator: (v) => Validators.goal(
            v,
            fieldName: 'Goal',
            max: widget.max,
          ),
          onFieldSubmitted: (_) => _submit(),
          decoration: InputDecoration(
            labelText: 'Daily target',
            suffixText: widget.metric.unit,
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(
          style: FilledButton.styleFrom(minimumSize: const Size(64, 40)),
          onPressed: _submit,
          child: const Text('Save'),
        ),
      ],
    );
  }
}
