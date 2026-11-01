import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../../../core/constants/app_constants.dart';
import '../../../../core/utils/date_utils.dart';
import '../../../health_records/data/models/daily_summary.dart';
import '../../../health_records/data/models/health_metric.dart';
import '../../../health_records/data/models/health_record.dart';
import '../../../health_records/data/repositories/health_record_repository.dart';

/// ViewModel (MVVM) for the Dashboard feature.
///
/// Produces the "at a glance" view of the user's health: today's totals,
/// today's individual entries, a 7-day trend and a logging streak. It also
/// offers the quick-add water command.
class DashboardViewModel extends ChangeNotifier {
  DashboardViewModel(this._repository, {DateTime Function()? clock})
      : _clock = clock ?? DateTime.now {
    _subscription = _repository.changes.listen((_) => load(silent: true));
  }

  final HealthRecordRepository _repository;
  final DateTime Function() _clock;
  late final StreamSubscription<void> _subscription;

  DailySummary? _today;
  List<HealthRecord> _todayEntries = const [];
  List<DailySummary> _trend = const [];
  int _streak = 0;
  HealthMetric _chartMetric = HealthMetric.steps;
  bool _isLoading = false;
  bool _hasLoaded = false;
  String? _errorMessage;
  bool _disposed = false;
  int _loadToken = 0;

  // ------------------------------------------------------------ getters --

  DateTime get now => _clock();
  DateTime get todayDate => AppDateUtils.dateOnly(_clock());
  DailySummary get today => _today ?? DailySummary.empty(todayDate);
  List<HealthRecord> get todayEntries => List.unmodifiable(_todayEntries);

  /// Exactly [AppConstants.trendDays] summaries, oldest first, with empty
  /// days filled in so the chart always shows a full week.
  List<DailySummary> get trend => List.unmodifiable(_trend);

  /// Consecutive days (ending today, or yesterday if nothing is logged yet
  /// today) that contain at least one record.
  int get streak => _streak;

  HealthMetric get chartMetric => _chartMetric;
  bool get isLoading => _isLoading;
  bool get hasLoaded => _hasLoaded;
  String? get errorMessage => _errorMessage;

  /// Average daily value of [metric] over the trend days that have data.
  int trendAverage(HealthMetric metric) {
    final days = _trend.where((d) => d.hasData).toList();
    if (days.isEmpty) return 0;
    final total = days.fold<int>(0, (sum, d) => sum + d.valueOf(metric));
    return (total / days.length).round();
  }

  int trendTotal(HealthMetric metric) =>
      _trend.fold<int>(0, (sum, d) => sum + d.valueOf(metric));

  /// Day with the highest value of [metric] in the trend window.
  DailySummary? bestDay(HealthMetric metric) {
    DailySummary? best;
    for (final day in _trend) {
      if (!day.hasData) continue;
      if (best == null || day.valueOf(metric) > best.valueOf(metric)) {
        best = day;
      }
    }
    return best;
  }

  /// Number of trend days on which the [goal] for [metric] was reached.
  int daysGoalMet(HealthMetric metric, int goal) =>
      _trend.where((d) => d.hasData && d.valueOf(metric) >= goal).length;

  // ----------------------------------------------------------- commands --

  Future<void> load({bool silent = false}) async {
    final token = ++_loadToken;
    if (!silent) {
      _isLoading = true;
      _errorMessage = null;
      _notify();
    }
    try {
      final today = todayDate;
      final trendStart =
          DateTime(today.year, today.month, today.day - (AppConstants.trendDays - 1));
      final streakStart = DateTime(
          today.year, today.month, today.day - (AppConstants.streakLookbackDays - 1));

      final results = await Future.wait([
        _repository.getDailySummaries(from: streakStart, to: today),
        _repository.getRecords(from: today, to: today, newestFirst: false),
      ]);
      if (token != _loadToken) return;

      final summaries = results[0] as List<DailySummary>;
      final byDate = {
        for (final s in summaries) AppDateUtils.toDbString(s.date): s,
      };

      _today = byDate[AppDateUtils.toDbString(today)] ?? DailySummary.empty(today);
      _todayEntries = results[1] as List<HealthRecord>;
      _trend = [
        for (final day in AppDateUtils.daysInRange(trendStart, today))
          byDate[AppDateUtils.toDbString(day)] ?? DailySummary.empty(day),
      ];
      _streak = _calculateStreak(byDate, today);
      _errorMessage = null;
    } on RepositoryException catch (e) {
      if (token != _loadToken) return;
      _errorMessage = e.message;
    } finally {
      if (token == _loadToken) {
        _isLoading = false;
        _hasLoaded = true;
        _notify();
      }
    }
  }

  void selectChartMetric(HealthMetric metric) {
    if (metric == _chartMetric) return;
    _chartMetric = metric;
    _notify();
  }

  /// Adds [ml] of water to today's most recent entry, or creates a new entry
  /// for today if none exists. Returns `null` on success or an error message.
  Future<String?> quickAddWater(int ml) async {
    if (ml <= 0) return 'Amount must be greater than 0';
    try {
      final today = todayDate;
      final entries = await _repository.getRecords(from: today, to: today);
      if (entries.isEmpty) {
        await _repository.addRecord(HealthRecord(date: today, water: ml));
      } else {
        final latest = entries.first; // newest first
        final updated = latest.water + ml;
        if (updated > AppConstants.maxWaterMl) {
          return 'That would exceed ${AppConstants.maxWaterMl} ml for one entry';
        }
        await _repository.updateRecord(latest.copyWith(water: updated));
      }
      return null;
    } on RepositoryException catch (e) {
      return e.message;
    }
  }

  // ------------------------------------------------------------ helpers --

  int _calculateStreak(Map<String, DailySummary> byDate, DateTime today) {
    bool hasData(DateTime d) => byDate[AppDateUtils.toDbString(d)]?.hasData ?? false;

    var day = today;
    // Not logging *yet* today should not break an ongoing streak.
    if (!hasData(day)) day = DateTime(day.year, day.month, day.day - 1);

    var streak = 0;
    while (hasData(day)) {
      streak++;
      day = DateTime(day.year, day.month, day.day - 1);
    }
    return streak;
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _subscription.cancel();
    super.dispose();
  }
}
