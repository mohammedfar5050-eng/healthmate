import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../../core/constants/app_constants.dart';
import '../../../../core/utils/date_utils.dart';
import '../../../../core/utils/formatters.dart';
import '../../../../core/widgets/dialogs.dart';
import '../../../../core/widgets/state_views.dart';
import '../../../health_records/data/models/health_metric.dart';
import '../../../health_records/presentation/screens/records_screen.dart';
import '../../../health_records/presentation/widgets/record_card.dart';
import '../../../settings/data/daily_goals.dart';
import '../../../settings/presentation/viewmodels/settings_viewmodel.dart';
import '../viewmodels/dashboard_viewmodel.dart';
import '../widgets/metric_progress_card.dart';
import '../widgets/weekly_trend_chart.dart';

/// Dashboard screen: a summary of today's activity plus weekly insights.
class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key, this.onViewTodayRecords});

  /// Called by "See all" to jump to the records tab filtered on today.
  final VoidCallback? onViewTodayRecords;

  @override
  Widget build(BuildContext context) {
    final vm = context.watch<DashboardViewModel>();
    final goals = context.watch<SettingsViewModel>().goals;

    Widget body;
    if (vm.isLoading && !vm.hasLoaded) {
      body = const Center(child: CircularProgressIndicator());
    } else if (vm.errorMessage != null && !vm.hasLoaded) {
      body = ErrorView(message: vm.errorMessage!, onRetry: vm.load);
    } else {
      body = RefreshIndicator(
        onRefresh: vm.load,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
          children: [
            _Header(vm: vm),
            const SizedBox(height: 16),
            const SectionHeader(title: "Today's activity"),
            for (final metric in HealthMetric.values) ...[
              MetricProgressCard(
                metric: metric,
                value: vm.today.valueOf(metric),
                goal: goals.goalFor(metric),
                onTap: () => vm.selectChartMetric(metric),
              ),
              const SizedBox(height: 10),
            ],
            const SizedBox(height: 6),
            _QuickWaterCard(vm: vm, goals: goals),
            const SizedBox(height: 20),
            _WeeklyCard(vm: vm, goals: goals),
            const SizedBox(height: 20),
            _TodayEntries(vm: vm, onSeeAll: onViewTodayRecords),
          ],
        ),
      );
    }

    return Scaffold(
      body: SafeArea(child: body),
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'dashboard-fab',
        onPressed: () => RecordsScreen.openForm(context, date: vm.todayDate),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Log activity'),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.vm});

  final DashboardViewModel vm;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 12, left: 4, right: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${AppDateUtils.greeting(vm.now)} 👋',
                  style: theme.textTheme.bodyLarge?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Today – ${AppDateUtils.formatLong(vm.todayDate)}',
                  key: const Key('dashboard-date'),
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
          if (vm.streak > 0)
            Tooltip(
              message: 'Consecutive days with at least one entry',
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: theme.colorScheme.tertiaryContainer,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  '🔥 ${vm.streak} day${vm.streak == 1 ? '' : 's'}',
                  style: theme.textTheme.labelLarge?.copyWith(
                    color: theme.colorScheme.onTertiaryContainer,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _QuickWaterCard extends StatelessWidget {
  const _QuickWaterCard({required this.vm, required this.goals});

  final DashboardViewModel vm;
  final DailyGoals goals;

  Future<void> _add(BuildContext context, int ml) async {
    final error = await vm.quickAddWater(ml);
    if (!context.mounted) return;
    showAppSnackBar(
      context,
      error ?? 'Added $ml ml of water 💧',
      isError: error != null,
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final water = HealthMetric.water;
    return Card(
      color: water.color.withValues(alpha: 0.08),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.local_drink_rounded, color: water.color),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Quick add water',
                    style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
                Text(
                  '${AppFormatters.percent(goals.progress(water, vm.today.water))} of goal',
                  style: theme.textTheme.labelMedium?.copyWith(color: water.color),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                for (final ml in AppConstants.waterPresets) ...[
                  Expanded(
                    child: OutlinedButton(
                      key: Key('quick-water-$ml'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: water.color,
                        side: BorderSide(color: water.color.withValues(alpha: 0.4)),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      onPressed: () => _add(context, ml),
                      child: Text('+$ml ml'),
                    ),
                  ),
                  if (ml != AppConstants.waterPresets.last) const SizedBox(width: 8),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _WeeklyCard extends StatelessWidget {
  const _WeeklyCard({required this.vm, required this.goals});

  final DashboardViewModel vm;
  final DailyGoals goals;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final metric = vm.chartMetric;
    final best = vm.bestDay(metric);
    final goal = goals.goalFor(metric);

    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Last 7 days',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            SegmentedButton<HealthMetric>(
              showSelectedIcon: false,
              segments: [
                for (final m in HealthMetric.values)
                  ButtonSegment(
                    value: m,
                    icon: Icon(m.icon, size: 18),
                    label: Text(m.shortLabel),
                  ),
              ],
              selected: {metric},
              onSelectionChanged: (s) => vm.selectChartMetric(s.first),
            ),
            const SizedBox(height: 20),
            WeeklyTrendChart(days: vm.trend, metric: metric, goal: goal),
            const SizedBox(height: 12),
            const Divider(),
            const SizedBox(height: 12),
            Row(
              children: [
                _Stat(
                  label: 'Daily average',
                  value: AppFormatters.number(vm.trendAverage(metric)),
                  unit: metric.shortUnit,
                ),
                _Stat(
                  label: 'Best day',
                  value: best == null ? '–' : AppDateUtils.formatWeekday(best.date),
                  unit: best == null ? '' : AppFormatters.compact(best.valueOf(metric)),
                ),
                _Stat(
                  label: 'Goal reached',
                  value: '${vm.daysGoalMet(metric, goal)}',
                  unit: '/ ${AppConstants.trendDays} days',
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value, required this.unit});

  final String label;
  final String value;
  final String unit;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Expanded(
      child: Column(
        children: [
          Text.rich(
            TextSpan(
              children: [
                TextSpan(
                  text: value,
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
                ),
                if (unit.isNotEmpty)
                  TextSpan(
                    text: ' $unit',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _TodayEntries extends StatelessWidget {
  const _TodayEntries({required this.vm, this.onSeeAll});

  final DashboardViewModel vm;
  final VoidCallback? onSeeAll;

  @override
  Widget build(BuildContext context) {
    final entries = vm.todayEntries;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionHeader(
          title: "Today's entries",
          trailing: entries.isEmpty || onSeeAll == null
              ? null
              : TextButton(onPressed: onSeeAll, child: const Text('See all')),
        ),
        if (entries.isEmpty)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Row(
                children: [
                  Icon(Icons.edit_calendar_rounded,
                      color: Theme.of(context).colorScheme.primary),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Text('Nothing logged yet today. '
                        'Tap "Log activity" to add your first entry.'),
                  ),
                ],
              ),
            ),
          )
        else
          for (var i = 0; i < entries.length; i++)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: RecordCard(
                record: entries[i],
                entryLabel: entries.length > 1 ? 'Entry ${i + 1}' : null,
                onTap: () => RecordsScreen.openForm(context, record: entries[i]),
                onAction: (action) => switch (action) {
                  RecordAction.edit =>
                    RecordsScreen.openForm(context, record: entries[i]),
                  RecordAction.delete =>
                    RecordsScreen.confirmAndDelete(context, entries[i]),
                },
              ),
            ),
      ],
    );
  }
}
