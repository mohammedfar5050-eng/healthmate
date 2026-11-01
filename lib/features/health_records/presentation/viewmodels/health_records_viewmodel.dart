import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../../../core/utils/date_utils.dart';
import '../../data/models/daily_summary.dart';
import '../../data/models/health_record.dart';
import '../../data/repositories/health_record_repository.dart';
import 'date_filter.dart';

/// Records belonging to one calendar day, used to render a grouped list.
class RecordGroup {
  RecordGroup(this.date, this.records);

  final DateTime date;
  final List<HealthRecord> records;

  DailySummary get summary => DailySummary(
        date: date,
        steps: records.fold(0, (sum, r) => sum + r.steps),
        calories: records.fold(0, (sum, r) => sum + r.calories),
        water: records.fold(0, (sum, r) => sum + r.water),
        entryCount: records.length,
      );
}

/// ViewModel (MVVM) for the Health Records feature.
///
/// Holds the UI state for the records list (loaded records, active date
/// filter, sort order, loading/error flags) and exposes the CRUD commands used
/// by the list and the add/edit form. Widgets observe it through Provider and
/// rebuild whenever [notifyListeners] is called.
class HealthRecordsViewModel extends ChangeNotifier {
  HealthRecordsViewModel(this._repository, {DateTime Function()? clock})
      : _clock = clock ?? DateTime.now {
    // Reload whenever data is changed anywhere in the app (e.g. quick-add
    // water on the dashboard) so this list never shows stale data.
    _subscription = _repository.changes.listen((_) => loadRecords(silent: true));
  }

  final HealthRecordRepository _repository;
  final DateTime Function() _clock;
  late final StreamSubscription<void> _subscription;

  List<HealthRecord> _records = const [];
  DateFilter _filter = const DateFilter.all();
  bool _newestFirst = true;
  bool _isLoading = false;
  bool _hasLoaded = false;
  String? _errorMessage;
  bool _disposed = false;

  /// Incremented on every load so that responses from older, slower queries
  /// are discarded instead of overwriting newer results.
  int _loadToken = 0;

  // ------------------------------------------------------------ getters --

  List<HealthRecord> get records => List.unmodifiable(_records);
  DateFilter get filter => _filter;
  bool get newestFirst => _newestFirst;
  bool get isLoading => _isLoading;
  bool get hasLoaded => _hasLoaded;
  String? get errorMessage => _errorMessage;
  bool get isEmpty => _records.isEmpty;
  DateTime get today => AppDateUtils.dateOnly(_clock());

  /// Records grouped by day, preserving the current sort order.
  List<RecordGroup> get groups {
    final result = <RecordGroup>[];
    for (final record in _records) {
      if (result.isNotEmpty &&
          AppDateUtils.isSameDay(result.last.date, record.date)) {
        result.last.records.add(record);
      } else {
        result.add(RecordGroup(record.date, [record]));
      }
    }
    return result;
  }

  /// Totals across all records matching the current filter.
  DailySummary get filteredTotals => RecordGroup(today, _records).summary;

  // ------------------------------------------------------------ queries --

  /// Loads records matching the current filter from the database.
  ///
  /// When [silent] is true the existing list stays visible while refreshing
  /// (no loading spinner), which avoids flicker after small updates.
  Future<void> loadRecords({bool silent = false}) async {
    final token = ++_loadToken;
    if (!silent) {
      _isLoading = true;
      _errorMessage = null;
      _notify();
    }
    try {
      final bounds = _filter.resolve(_clock());
      final result = await _repository.getRecords(
        from: bounds.from,
        to: bounds.to,
        newestFirst: _newestFirst,
      );
      if (token != _loadToken) return; // a newer load superseded this one
      _records = result;
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

  Future<void> setFilter(DateFilter filter) async {
    if (filter == _filter) return;
    _filter = filter;
    await loadRecords();
  }

  Future<void> clearFilter() => setFilter(const DateFilter.all());

  Future<void> toggleSortOrder() async {
    _newestFirst = !_newestFirst;
    await loadRecords(silent: true);
  }

  Future<HealthRecord?> getRecord(int id) => _repository.getRecord(id);

  // ----------------------------------------------------------- commands --
  // Commands return `null` on success or a user-facing error message.

  /// Inserts a new record or updates an existing one (when it has an id).
  Future<String?> saveRecord(HealthRecord record) async {
    try {
      final normalised = record.copyWith(date: AppDateUtils.dateOnly(record.date));
      if (normalised.id == null) {
        await _repository.addRecord(normalised);
      } else {
        await _repository.updateRecord(normalised);
      }
      return null;
    } on RepositoryException catch (e) {
      return e.message;
    }
  }

  Future<String?> deleteRecord(HealthRecord record) async {
    final id = record.id;
    if (id == null) return 'This record has not been saved yet.';
    // Optimistic update: remove immediately so the list feels responsive.
    final previous = _records;
    _records = _records.where((r) => r.id != id).toList();
    _notify();
    try {
      await _repository.deleteRecord(id);
      return null;
    } on RepositoryException catch (e) {
      _records = previous;
      _notify();
      return e.message;
    }
  }

  /// Re-inserts a record that was just deleted (used by the "Undo" action).
  Future<String?> restoreRecord(HealthRecord record) async {
    try {
      await _repository.addRecord(record);
      return null;
    } on RepositoryException catch (e) {
      return e.message;
    }
  }

  Future<String?> clearAll() async {
    try {
      await _repository.clearAll();
      return null;
    } on RepositoryException catch (e) {
      return e.message;
    }
  }

  Future<String?> resetWithSampleData() async {
    try {
      await _repository.resetWithSampleData();
      return null;
    } on RepositoryException catch (e) {
      return e.message;
    }
  }

  // ------------------------------------------------------------ helpers --

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
