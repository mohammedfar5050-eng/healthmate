import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../../core/utils/date_utils.dart';
import '../../../../core/utils/formatters.dart';
import '../../../../core/widgets/dialogs.dart';
import '../../../../core/widgets/state_views.dart';
import '../../data/models/health_metric.dart';
import '../../data/models/health_record.dart';
import '../viewmodels/date_filter.dart';
import '../viewmodels/health_records_viewmodel.dart';
import '../widgets/date_filter_bar.dart';
import '../widgets/day_group_header.dart';
import '../widgets/record_card.dart';
import 'record_form_screen.dart';

/// Health Record List screen: past records grouped by date, with date
/// filtering (presets, a single date or a range), sorting, edit and delete.
class RecordsScreen extends StatelessWidget {
  const RecordsScreen({super.key});

  static void openForm(BuildContext context, {HealthRecord? record, DateTime? date}) {
    Navigator.of(context).pushNamed(
      RecordFormScreen.routeName,
      arguments: RecordFormArgs(record: record, initialDate: date),
    );
  }

  static Future<void> confirmAndDelete(BuildContext context, HealthRecord record) async {
    final confirmed = await showConfirmDialog(
      context,
      title: 'Delete record?',
      message: 'This entry for ${AppDateUtils.formatLong(record.date)} '
          'will be permanently removed.',
      confirmLabel: 'Delete',
      destructive: true,
      icon: Icons.delete_outline_rounded,
    );
    if (!confirmed || !context.mounted) return;
    await deleteWithUndo(context, record);
  }

  static Future<void> deleteWithUndo(BuildContext context, HealthRecord record) async {
    final viewModel = context.read<HealthRecordsViewModel>();
    final error = await viewModel.deleteRecord(record);
    if (!context.mounted) return;
    if (error != null) {
      showAppSnackBar(context, error, isError: true);
      return;
    }
    showAppSnackBar(
      context,
      'Record deleted',
      action: SnackBarAction(
        label: 'Undo',
        onPressed: () => viewModel.restoreRecord(record),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final vm = context.watch<HealthRecordsViewModel>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Health Records'),
        actions: [
          IconButton(
            tooltip: vm.newestFirst ? 'Showing newest first' : 'Showing oldest first',
            icon: Icon(vm.newestFirst
                ? Icons.arrow_downward_rounded
                : Icons.arrow_upward_rounded),
            onPressed: vm.toggleSortOrder,
          ),
        ],
      ),
      body: Column(
        children: [
          DateFilterBar(
            filter: vm.filter,
            today: vm.today,
            onChanged: vm.setFilter,
          ),
          if (vm.filter.isActive && !vm.isEmpty) _FilterSummary(vm: vm),
          Expanded(child: _buildBody(context, vm)),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'records-fab',
        onPressed: () => openForm(
          context,
          date: vm.filter.type == DateFilterType.singleDate ? vm.filter.start : null,
        ),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Add record'),
      ),
    );
  }

  Widget _buildBody(BuildContext context, HealthRecordsViewModel vm) {
    if (vm.isLoading && !vm.hasLoaded) {
      return const Center(child: CircularProgressIndicator());
    }
    if (vm.errorMessage != null && vm.isEmpty) {
      return ErrorView(message: vm.errorMessage!, onRetry: vm.loadRecords);
    }
    if (vm.isEmpty) {
      return RefreshIndicator(
        onRefresh: vm.loadRecords,
        child: ListView(
          children: [
            EmptyState(
              icon: vm.filter.isActive ? Icons.search_off_rounded : Icons.event_note_rounded,
              title: vm.filter.isActive ? 'No records found' : 'No records yet',
              message: vm.filter.isActive
                  ? 'There are no entries ${vm.filter.phrase}.'
                  : 'Tap "Add record" to log your steps, calories and water.',
              action: vm.filter.isActive
                  ? OutlinedButton.icon(
                      onPressed: vm.clearFilter,
                      icon: const Icon(Icons.filter_alt_off_rounded),
                      label: const Text('Clear filter'),
                    )
                  : null,
            ),
          ],
        ),
      );
    }

    final groups = vm.groups;
    return RefreshIndicator(
      onRefresh: vm.loadRecords,
      child: ListView.builder(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 96),
        itemCount: groups.length,
        itemBuilder: (context, index) {
          final group = groups[index];
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              DayGroupHeader(summary: group.summary, now: vm.today),
              for (var i = 0; i < group.records.length; i++)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: _DismissibleRecord(
                    record: group.records[i],
                    entryLabel: group.records.length > 1
                        ? 'Entry ${vm.newestFirst ? group.records.length - i : i + 1}'
                        : null,
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

/// Totals for the records matching the active filter.
class _FilterSummary extends StatelessWidget {
  const _FilterSummary({required this.vm});

  final HealthRecordsViewModel vm;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final totals = vm.filteredTotals;
    final days = vm.groups.length;
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 4, 16, 0),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: theme.colorScheme.secondaryContainer.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '${vm.records.length} ${vm.records.length == 1 ? 'entry' : 'entries'}'
              ' · $days ${days == 1 ? 'day' : 'days'}',
              style: theme.textTheme.labelLarge,
            ),
          ),
          for (final m in HealthMetric.values) ...[
            Icon(m.icon, size: 16, color: m.color),
            const SizedBox(width: 2),
            Text(AppFormatters.compact(totals.valueOf(m)),
                style: theme.textTheme.labelLarge),
            const SizedBox(width: 10),
          ],
          InkWell(
            borderRadius: BorderRadius.circular(20),
            onTap: vm.clearFilter,
            child: Tooltip(
              message: 'Clear filter',
              child: Icon(Icons.close_rounded,
                  size: 20, color: theme.colorScheme.onSecondaryContainer),
            ),
          ),
        ],
      ),
    );
  }
}

/// Record card that can be swiped away to delete it (with undo).
class _DismissibleRecord extends StatelessWidget {
  const _DismissibleRecord({required this.record, this.entryLabel});

  final HealthRecord record;
  final String? entryLabel;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Dismissible(
      key: ValueKey('record-${record.id}'),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: 24),
        decoration: BoxDecoration(
          color: scheme.errorContainer,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Delete',
                style: TextStyle(
                    color: scheme.onErrorContainer, fontWeight: FontWeight.w600)),
            const SizedBox(width: 8),
            Icon(Icons.delete_outline_rounded, color: scheme.onErrorContainer),
          ],
        ),
      ),
      onDismissed: (_) => RecordsScreen.deleteWithUndo(context, record),
      child: RecordCard(
        record: record,
        entryLabel: entryLabel,
        onTap: () => RecordsScreen.openForm(context, record: record),
        onAction: (action) => switch (action) {
          RecordAction.edit => RecordsScreen.openForm(context, record: record),
          RecordAction.delete => RecordsScreen.confirmAndDelete(context, record),
        },
      ),
    );
  }
}
