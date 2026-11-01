import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:healthmate/features/dashboard/presentation/viewmodels/dashboard_viewmodel.dart';
import 'package:healthmate/features/health_records/data/models/health_metric.dart';
import 'package:healthmate/features/health_records/data/models/health_record.dart';
import 'package:healthmate/features/health_records/data/repositories/health_record_repository.dart';
import 'package:healthmate/features/health_records/presentation/viewmodels/date_filter.dart';
import 'package:healthmate/features/health_records/presentation/viewmodels/health_records_viewmodel.dart';
import 'package:healthmate/features/settings/data/settings_repository.dart';
import 'package:healthmate/features/settings/presentation/viewmodels/settings_viewmodel.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../helpers/fake_health_record_repository.dart';

/// Lets pending stream events / microtasks run.
Future<void> pump() => Future<void>.delayed(Duration.zero);

void main() {
  final now = DateTime(2026, 8, 18, 10, 30);
  final today = DateTime(2026, 8, 18);
  DateTime daysAgo(int n) => DateTime(2026, 8, 18 - n);

  late FakeHealthRecordRepository repo;

  setUp(() {
    repo = FakeHealthRecordRepository(initial: [
      HealthRecord(date: today, steps: 5000, calories: 250, water: 1000),
      HealthRecord(date: today, steps: 2850, calories: 170, water: 1000),
      HealthRecord(date: daysAgo(1), steps: 9000, calories: 450, water: 2400),
      HealthRecord(date: daysAgo(2), steps: 12000, calories: 600, water: 2600),
      HealthRecord(date: daysAgo(10), steps: 3000, calories: 150, water: 1200),
    ]);
  });

  group('HealthRecordsViewModel', () {
    late HealthRecordsViewModel vm;

    setUp(() => vm = HealthRecordsViewModel(repo, clock: () => now));
    tearDown(() => vm.dispose());

    test('loads all records grouped by date, newest first', () async {
      await vm.loadRecords();
      expect(vm.records.length, 5);
      expect(vm.groups.length, 4);
      expect(vm.groups.first.date, today);
      expect(vm.groups.first.summary.steps, 7850);
      expect(vm.groups.first.summary.water, 2000);
    });

    test('filters by preset and single date', () async {
      await vm.loadRecords();
      await vm.setFilter(const DateFilter.last7Days());
      expect(vm.records.length, 4);

      await vm.setFilter(DateFilter.singleDate(daysAgo(10)));
      expect(vm.records.single.steps, 3000);

      await vm.setFilter(const DateFilter.today());
      expect(vm.records.length, 2);

      await vm.clearFilter();
      expect(vm.records.length, 5);
    });

    test('range filter normalises reversed dates', () {
      final f = DateFilter.range(today, daysAgo(3));
      final bounds = f.resolve(now);
      expect(bounds.from, daysAgo(3));
      expect(bounds.to, today);
    });

    test('saveRecord inserts and the list refreshes automatically', () async {
      await vm.loadRecords();
      final error = await vm.saveRecord(
        HealthRecord(date: DateTime(2026, 8, 18, 22, 15), water: 500),
      );
      await pump();
      expect(error, isNull);
      expect(vm.records.length, 6);
      expect(repo.all.last.date, today, reason: 'time component removed');
    });

    test('saveRecord updates an existing record', () async {
      await vm.loadRecords();
      final record = vm.records.first;
      await vm.saveRecord(record.copyWith(steps: 1));
      await pump();
      expect(vm.records.firstWhere((r) => r.id == record.id).steps, 1);
    });

    test('delete then undo restores the record with the same id', () async {
      await vm.loadRecords();
      final record = vm.records.first;
      expect(await vm.deleteRecord(record), isNull);
      expect(vm.records.any((r) => r.id == record.id), isFalse);

      expect(await vm.restoreRecord(record), isNull);
      await pump();
      expect(vm.records.any((r) => r.id == record.id), isTrue);
    });

    test('exposes a friendly error message when loading fails', () async {
      repo.failWith = RepositoryException('Could not load records.');
      await vm.loadRecords();
      expect(vm.errorMessage, 'Could not load records.');
      expect(vm.isLoading, isFalse);
    });

    test('toggleSortOrder reverses order', () async {
      await vm.loadRecords();
      await vm.toggleSortOrder();
      expect(vm.newestFirst, isFalse);
      expect(vm.records.first.date, daysAgo(10));
    });
  });

  group('DashboardViewModel', () {
    late DashboardViewModel vm;

    setUp(() => vm = DashboardViewModel(repo, clock: () => now));
    tearDown(() => vm.dispose());

    test("summarises today's totals (matches the brief's example)", () async {
      await vm.load();
      expect(vm.today.steps, 7850);
      expect(vm.today.calories, 420);
      expect(vm.today.water, 2000);
      expect(vm.todayEntries.length, 2);
    });

    test('builds a 7-day trend with empty days filled in', () async {
      await vm.load();
      expect(vm.trend.length, 7);
      expect(vm.trend.last.date, today);
      expect(vm.trend.first.date, daysAgo(6));
      expect(vm.trend.where((d) => d.hasData).length, 3);
      expect(vm.trendAverage(HealthMetric.steps), ((7850 + 9000 + 12000) / 3).round());
      expect(vm.bestDay(HealthMetric.steps)!.date, daysAgo(2));
      expect(vm.daysGoalMet(HealthMetric.steps, 9000), 2);
    });

    test('calculates the logging streak', () async {
      await vm.load();
      expect(vm.streak, 3);
    });

    test('quick add water updates the latest entry for today', () async {
      await vm.load();
      expect(await vm.quickAddWater(250), isNull);
      await pump();
      expect(vm.today.water, 2250);
      expect(vm.todayEntries.length, 2, reason: 'no new entry created');
    });

    test('quick add water creates an entry when today is empty', () async {
      final emptyRepo = FakeHealthRecordRepository();
      final dash = DashboardViewModel(emptyRepo, clock: () => now);
      await dash.load();
      expect(dash.today.hasData, isFalse);
      await dash.quickAddWater(500);
      await pump();
      expect(dash.today.water, 500);
      expect(dash.todayEntries.length, 1);
      dash.dispose();
    });

    test('selectChartMetric notifies listeners', () {
      var notified = 0;
      vm.addListener(() => notified++);
      vm.selectChartMetric(HealthMetric.water);
      expect(vm.chartMetric, HealthMetric.water);
      expect(notified, 1);
    });
  });

  group('SettingsViewModel', () {
    test('loads defaults and persists changes', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final vm = SettingsViewModel(SettingsRepository(prefs));

      expect(vm.goals.steps, 10000);
      expect(vm.themeMode, ThemeMode.system);

      await vm.updateGoal(HealthMetric.water, 3000);
      await vm.setThemeMode(ThemeMode.dark);

      final reloaded = SettingsViewModel(SettingsRepository(prefs));
      expect(reloaded.goals.water, 3000);
      expect(reloaded.themeMode, ThemeMode.dark);
    });
  });
}
