import 'dart:async';

import '../datasources/health_record_local_datasource.dart';
import '../models/daily_summary.dart';
import '../models/health_record.dart';

/// Contract between the presentation layer and the data layer.
///
/// View models depend on this abstraction rather than on sqflite directly,
/// which keeps them testable (tests inject an in-memory fake) and would allow
/// the storage technology to change without touching the UI.
abstract class HealthRecordRepository {
  /// Emits an event every time data changes, so independent view models
  /// (e.g. the dashboard and the records list) stay in sync automatically.
  Stream<void> get changes;

  Future<List<HealthRecord>> getRecords({
    DateTime? from,
    DateTime? to,
    bool newestFirst = true,
  });

  Future<HealthRecord?> getRecord(int id);

  Future<List<DailySummary>> getDailySummaries({
    required DateTime from,
    required DateTime to,
  });

  /// Inserts [record] and returns it with its generated id.
  Future<HealthRecord> addRecord(HealthRecord record);

  Future<void> updateRecord(HealthRecord record);

  Future<void> deleteRecord(int id);

  Future<void> clearAll();

  Future<void> resetWithSampleData();

  void dispose();
}

/// Thrown when a repository operation fails, carrying a user-friendly message.
class RepositoryException implements Exception {
  RepositoryException(this.message, [this.cause]);

  final String message;
  final Object? cause;

  @override
  String toString() => 'RepositoryException: $message'
      '${cause == null ? '' : ' ($cause)'}';
}

/// SQLite-backed implementation using [HealthRecordLocalDataSource].
class SqfliteHealthRecordRepository implements HealthRecordRepository {
  SqfliteHealthRecordRepository(this._dataSource);

  final HealthRecordLocalDataSource _dataSource;
  final StreamController<void> _changes = StreamController<void>.broadcast();

  @override
  Stream<void> get changes => _changes.stream;

  void _notifyChanged() {
    if (!_changes.isClosed) _changes.add(null);
  }

  /// Wraps low-level database errors in a [RepositoryException].
  Future<T> _guard<T>(String action, Future<T> Function() body) async {
    try {
      return await body();
    } on RepositoryException {
      rethrow;
    } catch (e) {
      throw RepositoryException('Could not $action. Please try again.', e);
    }
  }

  @override
  Future<List<HealthRecord>> getRecords({
    DateTime? from,
    DateTime? to,
    bool newestFirst = true,
  }) =>
      _guard('load records', () {
        return _dataSource.getAll(from: from, to: to, newestFirst: newestFirst);
      });

  @override
  Future<HealthRecord?> getRecord(int id) =>
      _guard('load the record', () => _dataSource.getById(id));

  @override
  Future<List<DailySummary>> getDailySummaries({
    required DateTime from,
    required DateTime to,
  }) =>
      _guard('load the summary', () {
        return _dataSource.getDailySummaries(from: from, to: to);
      });

  @override
  Future<HealthRecord> addRecord(HealthRecord record) =>
      _guard('save the record', () async {
        final id = await _dataSource.insert(record);
        _notifyChanged();
        return record.copyWith(id: id);
      });

  @override
  Future<void> updateRecord(HealthRecord record) =>
      _guard('update the record', () async {
        final changed = await _dataSource.update(record);
        if (changed == 0) {
          throw RepositoryException('This record no longer exists.');
        }
        _notifyChanged();
      });

  @override
  Future<void> deleteRecord(int id) => _guard('delete the record', () async {
        await _dataSource.delete(id);
        _notifyChanged();
      });

  @override
  Future<void> clearAll() => _guard('delete all records', () async {
        await _dataSource.deleteAll();
        _notifyChanged();
      });

  @override
  Future<void> resetWithSampleData() =>
      _guard('restore the sample data', () async {
        await _dataSource.resetWithSampleData();
        _notifyChanged();
      });

  @override
  void dispose() => _changes.close();
}
