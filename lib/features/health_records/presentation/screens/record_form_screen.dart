import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../../core/constants/app_constants.dart';
import '../../../../core/utils/date_utils.dart';
import '../../../../core/utils/formatters.dart';
import '../../../../core/utils/validators.dart';
import '../../../../core/widgets/dialogs.dart';
import '../../../settings/presentation/viewmodels/settings_viewmodel.dart';
import '../../data/models/health_metric.dart';
import '../../data/models/health_record.dart';
import '../viewmodels/health_records_viewmodel.dart';
import '../widgets/metric_input_field.dart';

/// Arguments for [RecordFormScreen].
class RecordFormArgs {
  const RecordFormArgs({this.record, this.initialDate});

  /// Record to edit; `null` means "add a new record".
  final HealthRecord? record;

  /// Pre-selected date when adding.
  final DateTime? initialDate;
}

/// Add / Edit Record screen.
///
/// A single screen serves both modes. Input is validated per field
/// (required, numeric, range) and across fields (at least one non-zero
/// value). Leaving with unsaved changes asks for confirmation.
class RecordFormScreen extends StatefulWidget {
  const RecordFormScreen({super.key, this.args = const RecordFormArgs()});

  static const String routeName = '/record-form';

  final RecordFormArgs args;

  @override
  State<RecordFormScreen> createState() => _RecordFormScreenState();
}

class _RecordFormScreenState extends State<RecordFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _dateController;
  late final TextEditingController _stepsController;
  late final TextEditingController _caloriesController;
  late final TextEditingController _waterController;
  final _caloriesFocus = FocusNode();
  final _waterFocus = FocusNode();

  late DateTime _date;
  late final HealthRecord _initial;
  AutovalidateMode _autovalidate = AutovalidateMode.disabled;
  String? _formError;
  bool _saving = false;

  bool get _isEditing => widget.args.record != null;

  @override
  void initState() {
    super.initState();
    final record = widget.args.record;
    _date = AppDateUtils.dateOnly(
      record?.date ?? widget.args.initialDate ?? DateTime.now(),
    );
    _initial = record ?? HealthRecord(date: _date);
    _dateController = TextEditingController(text: AppDateUtils.formatMedium(_date));
    _stepsController = TextEditingController(text: _initialText(record?.steps));
    _caloriesController = TextEditingController(text: _initialText(record?.calories));
    _waterController = TextEditingController(text: _initialText(record?.water));
    for (final c in [_stepsController, _caloriesController, _waterController]) {
      c.addListener(_onChanged);
    }
  }

  String _initialText(int? value) => value == null ? '' : value.toString();

  void _onChanged() {
    // Clear the cross-field error as soon as the user edits a value and
    // rebuild so the "unsaved changes" guard is up to date.
    setState(() => _formError = null);
  }

  HealthRecord _currentRecord() => HealthRecord(
        id: widget.args.record?.id,
        date: _date,
        steps: Validators.parseInt(_stepsController.text),
        calories: Validators.parseInt(_caloriesController.text),
        water: Validators.parseInt(_waterController.text),
      );

  bool get _hasUnsavedChanges {
    if (!_isEditing) {
      return [_stepsController, _caloriesController, _waterController]
          .any((c) => c.text.trim().isNotEmpty);
    }
    return _currentRecord() != _initial;
  }

  @override
  void dispose() {
    _dateController.dispose();
    _stepsController.dispose();
    _caloriesController.dispose();
    _waterController.dispose();
    _caloriesFocus.dispose();
    _waterFocus.dispose();
    super.dispose();
  }

  // -------------------------------------------------------------- actions --

  Future<void> _pickDate() async {
    final today = AppDateUtils.dateOnly(DateTime.now());
    final picked = await showDatePicker(
      context: context,
      helpText: 'Select the activity date',
      initialDate: _date.isAfter(today) ? today : _date,
      firstDate: AppConstants.earliestRecordDate,
      lastDate: today,
    );
    if (picked != null) {
      setState(() {
        _date = AppDateUtils.dateOnly(picked);
        _dateController.text = AppDateUtils.formatMedium(_date);
      });
    }
  }

  void _addWater(int ml) {
    final current = Validators.parseInt(_waterController.text);
    final next = (current + ml).clamp(0, AppConstants.maxWaterMl);
    _waterController.text = next.toString();
  }

  Future<void> _save() async {
    FocusScope.of(context).unfocus();
    setState(() => _autovalidate = AutovalidateMode.onUserInteraction);

    final fieldsValid = _formKey.currentState?.validate() ?? false;
    final record = _currentRecord();
    final crossFieldError = fieldsValid
        ? Validators.atLeastOneActivity(record.steps, record.calories, record.water)
        : null;
    setState(() => _formError = crossFieldError);
    if (!fieldsValid || crossFieldError != null) return;

    if (_isEditing && record == _initial) {
      Navigator.of(context).pop();
      return;
    }

    setState(() => _saving = true);
    final viewModel = context.read<HealthRecordsViewModel>();
    final navigator = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final error = await viewModel.saveRecord(record);
    if (!mounted) return;
    setState(() => _saving = false);

    if (error != null) {
      showAppSnackBar(context, error, isError: true);
      return;
    }
    navigator.pop(true);
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Text(_isEditing
            ? 'Record updated'
            : 'Record added for ${AppDateUtils.friendlyLabel(record.date)}'),
      ));
  }

  Future<void> _delete() async {
    final record = widget.args.record;
    if (record == null) return;
    final confirmed = await showConfirmDialog(
      context,
      title: 'Delete record?',
      message: 'This entry for ${AppDateUtils.formatLong(record.date)} '
          'will be permanently removed.',
      confirmLabel: 'Delete',
      destructive: true,
      icon: Icons.delete_outline_rounded,
    );
    if (!confirmed || !mounted) return;

    final viewModel = context.read<HealthRecordsViewModel>();
    final navigator = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final error = await viewModel.deleteRecord(record);
    if (!mounted) return;
    if (error != null) {
      showAppSnackBar(context, error, isError: true);
      return;
    }
    navigator.pop(true);
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: const Text('Record deleted'),
        action: SnackBarAction(
          label: 'Undo',
          onPressed: () => viewModel.restoreRecord(record),
        ),
      ));
  }

  Future<void> _onPopInvoked(bool didPop, Object? result) async {
    if (didPop) return;
    final navigator = Navigator.of(context);
    final discard = await showConfirmDialog(
      context,
      title: 'Discard changes?',
      message: 'You have unsaved changes. Are you sure you want to leave?',
      confirmLabel: 'Discard',
      cancelLabel: 'Keep editing',
      destructive: true,
    );
    if (discard && mounted) navigator.pop();
  }

  // ---------------------------------------------------------------- build --

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final goals = context.watch<SettingsViewModel>().goals;

    String goalHelper(HealthMetric m) =>
        'Daily goal: ${AppFormatters.number(goals.goalFor(m))} ${m.unit}';

    return PopScope(
      canPop: !_hasUnsavedChanges || _saving,
      onPopInvokedWithResult: _onPopInvoked,
      child: Scaffold(
        appBar: AppBar(
          title: Text(_isEditing ? 'Edit Record' : 'Add Record'),
          actions: [
            if (_isEditing)
              IconButton(
                tooltip: 'Delete record',
                icon: const Icon(Icons.delete_outline_rounded),
                onPressed: _saving ? null : _delete,
              ),
          ],
        ),
        body: SafeArea(
          child: Form(
            key: _formKey,
            autovalidateMode: _autovalidate,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
              children: [
                Text(
                  _isEditing
                      ? 'Update the values for this entry.'
                      : 'Log your activity. You can add several entries per day '
                          '– the dashboard shows the daily total.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 20),
                TextFormField(
                  key: const Key('field-date'),
                  controller: _dateController,
                  readOnly: true,
                  onTap: _pickDate,
                  validator: (_) => Validators.recordDate(_date),
                  decoration: const InputDecoration(
                    labelText: 'Date',
                    prefixIcon: Icon(Icons.event_rounded),
                    suffixIcon: Icon(Icons.arrow_drop_down_rounded),
                  ),
                ),
                const SizedBox(height: 16),
                MetricInputField(
                  metric: HealthMetric.steps,
                  controller: _stepsController,
                  validator: Validators.steps,
                  helperText: goalHelper(HealthMetric.steps),
                  onFieldSubmitted: (_) => _caloriesFocus.requestFocus(),
                ),
                const SizedBox(height: 16),
                MetricInputField(
                  metric: HealthMetric.calories,
                  controller: _caloriesController,
                  focusNode: _caloriesFocus,
                  validator: Validators.calories,
                  helperText: goalHelper(HealthMetric.calories),
                  maxDigits: 5,
                  onFieldSubmitted: (_) => _waterFocus.requestFocus(),
                ),
                const SizedBox(height: 16),
                MetricInputField(
                  metric: HealthMetric.water,
                  controller: _waterController,
                  focusNode: _waterFocus,
                  validator: Validators.water,
                  helperText: goalHelper(HealthMetric.water),
                  maxDigits: 5,
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _save(),
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  children: [
                    for (final ml in AppConstants.waterPresets)
                      ActionChip(
                        avatar: Icon(
                          Icons.add_rounded,
                          size: 18,
                          color: HealthMetric.water.color,
                        ),
                        label: Text('$ml ml'),
                        onPressed: () => _addWater(ml),
                      ),
                  ],
                ),
                AnimatedSwitcher(
                  duration: const Duration(milliseconds: 200),
                  child: _formError == null
                      ? const SizedBox(height: 16)
                      : Padding(
                          key: const Key('form-error'),
                          padding: const EdgeInsets.only(top: 16),
                          child: Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: theme.colorScheme.errorContainer,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Row(
                              children: [
                                Icon(Icons.info_outline_rounded,
                                    color: theme.colorScheme.onErrorContainer),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Text(
                                    _formError!,
                                    style: TextStyle(
                                      color: theme.colorScheme.onErrorContainer,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                ),
                const SizedBox(height: 24),
                FilledButton.icon(
                  key: const Key('save-button'),
                  onPressed: _saving ? null : _save,
                  icon: _saving
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2.5),
                        )
                      : Icon(_isEditing ? Icons.check_rounded : Icons.add_rounded),
                  label: Text(_isEditing ? 'Save changes' : 'Add record'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
