import 'dart:async';

import 'package:healthmate/core/database/sample_data.dart';
import 'package:healthmate/core/utils/date_utils.dart';
import 'package:healthmate/features/health_records/data/models/daily_summary.dart';
import 'package:healthmate/features/health_records/data/models/health_record.dart';
import 'package:healthmate/features/health_records/data/repositories/health_record_repository.dart';

/// In-memory [HealthRecordRepository] used by view-model and widget tests.
///
/// It mirrors the behaviour of the SQLite implementation (ordering, inclusive
/// date filters, daily aggregation and change notifications) without any
/// platform channels, so tests run fast and deterministically.
class FakeHealthRecordRepository implements HealthRecordRepository {
  FakeHealthRecordRepository({
    List<HealthRecord> initial = const [],
    this.sampleToday,
  }) {
    for (final r in initial) {
      _insert(r);
    }
  }

  final DateTime? sampleToday;
  final Map<int, HealthRecord> _store = {};
  final StreamController<void> _changes = StreamController<void>.broadcast();
  int _nextId = 1;

  /// When set, every call throws this exception (to test error handling).
  RepositoryException? failWith;

  List<HealthRecord> get all => _store.values.toList();

  HealthRecord _insert(HealthRecord r) {
    final id = r.id ?? _nextId;
    if (id >= _nextId) _nextId = id + 1;
    final saved = r.copyWith(id: id);
    _store[id] = saved;
    return saved;
  }

  void _check() {
    final f = failWith;
    if (f != null) throw f;
  }

  void _emit() => _changes.add(null);

  bool _inRange(DateTime d, DateTime? from, DateTime? to) {
    final key = AppDateUtils.toDbString(d);
    if (from != null && key.compareTo(AppDateUtils.toDbString(from)) < 0) return false;
    if (to != null && key.compareTo(AppDateUtils.toDbString(to)) > 0) return false;
    return true;
  }

  @override
  Stream<void> get changes => _changes.stream;

  @override
  Future<List<HealthRecord>> getRecords({
    DateTime? from,
    DateTime? to,
    bool newestFirst = true,
  }) async {
    _check();
    final list = _store.values.where((r) => _inRange(r.date, from, to)).toList()
      ..sort((a, b) {
        final c = a.date.compareTo(b.date);
        return c != 0 ? c : a.id!.compareTo(b.id!);
      });
    return newestFirst ? list.reversed.toList() : list;
  }

  @override
  Future<HealthRecord?> getRecord(int id) async {
    _check();
    return _store[id];
  }

  @override
  Future<List<DailySummary>> getDailySummaries({
    required DateTime from,
    required DateTime to,
  }) async {
    _check();
    final byDay = <String, List<HealthRecord>>{};
    for (final r in _store.values.where((r) => _inRange(r.date, from, to))) {
      byDay.putIfAbsent(AppDateUtils.toDbString(r.date), () => []).add(r);
    }
    final keys = byDay.keys.toList()..sort();
    return [
      for (final k in keys)
        DailySummary(
          date: AppDateUtils.fromDbString(k),
          steps: byDay[k]!.fold(0, (s, r) => s + r.steps),
          calories: byDay[k]!.fold(0, (s, r) => s + r.calories),
          water: byDay[k]!.fold(0, (s, r) => s + r.water),
          entryCount: byDay[k]!.length,
        ),
    ];
  }

  @override
  Future<HealthRecord> addRecord(HealthRecord record) async {
    _check();
    final saved = _insert(record);
    _emit();
    return saved;
  }

  @override
  Future<void> updateRecord(HealthRecord record) async {
    _check();
    if (!_store.containsKey(record.id)) {
      throw RepositoryException('This record no longer exists.');
    }
    _store[record.id!] = record;
    _emit();
  }

  @override
  Future<void> deleteRecord(int id) async {
    _check();
    _store.remove(id);
    _emit();
  }

  @override
  Future<void> clearAll() async {
    _check();
    _store.clear();
    _emit();
  }

  @override
  Future<void> resetWithSampleData() async {
    _check();
    _store.clear();
    for (final r in SampleData.generate(today: sampleToday ?? DateTime.now())) {
      _insert(r);
    }
    _emit();
  }

  @override
  void dispose() => _changes.close();
}
