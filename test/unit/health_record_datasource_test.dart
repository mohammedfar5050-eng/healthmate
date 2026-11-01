import 'package:flutter_test/flutter_test.dart';
import 'package:healthmate/core/database/database_helper.dart';
import 'package:healthmate/core/database/sample_data.dart';
import 'package:healthmate/features/health_records/data/datasources/health_record_local_datasource.dart';
import 'package:healthmate/features/health_records/data/models/health_record.dart';
import 'package:healthmate/features/health_records/data/repositories/health_record_repository.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

/// Integration tests for the SQLite layer, executed against a real in-memory
/// SQLite database through sqflite_common_ffi.
void main() {
  sqfliteFfiInit();

  final today = DateTime(2026, 8, 18);
  late DatabaseHelper helper;
  late HealthRecordLocalDataSource dataSource;

  DatabaseHelper createHelper({bool seed = false}) => DatabaseHelper(
        factory: databaseFactoryFfi,
        path: inMemoryDatabasePath,
        seedSampleData: seed,
        clock: () => today,
      );

  setUp(() {
    helper = createHelper();
    dataSource = HealthRecordLocalDataSource(helper);
  });

  tearDown(() => helper.close());

  group('Schema', () {
    test('creates health_records table with the required columns', () async {
      final db = await helper.database;
      final columns = await db.rawQuery('PRAGMA table_info(health_records)');
      final names = columns.map((c) => c['name']).toList();
      expect(names, ['id', 'date', 'steps', 'calories', 'water']);

      final pk = columns.firstWhere((c) => c['name'] == 'id');
      expect(pk['pk'], 1);
    });

    test('rejects negative values through CHECK constraints', () async {
      final db = await helper.database;
      expect(
        () => db.insert('health_records', {
          'date': '2026-08-18',
          'steps': -5,
          'calories': 0,
          'water': 0,
        }),
        throwsA(isA<DatabaseException>()),
      );
    });

    test('seeds dummy records on first creation when enabled', () async {
      final seeded = createHelper(seed: true);
      final ds = HealthRecordLocalDataSource(seeded);
      expect(await ds.count(), SampleData.generate(today: today).length);
      await seeded.close();
    });
  });

  group('CRUD', () {
    test('insert then read back by id', () async {
      final id = await dataSource.insert(
        HealthRecord(date: today, steps: 7850, calories: 420, water: 2000),
      );
      final record = await dataSource.getById(id);
      expect(record, isNotNull);
      expect(record!.steps, 7850);
      expect(record.calories, 420);
      expect(record.water, 2000);
      expect(record.date, today);
    });

    test('update modifies only the targeted row', () async {
      final a = await dataSource.insert(HealthRecord(date: today, steps: 100));
      final b = await dataSource.insert(HealthRecord(date: today, steps: 200));

      final changed = await dataSource.update(
        HealthRecord(id: a, date: today, steps: 999, water: 500),
      );
      expect(changed, 1);
      expect((await dataSource.getById(a))!.steps, 999);
      expect((await dataSource.getById(b))!.steps, 200);
    });

    test('delete removes the row', () async {
      final id = await dataSource.insert(HealthRecord(date: today, water: 250));
      expect(await dataSource.delete(id), 1);
      expect(await dataSource.getById(id), isNull);
    });

    test('update without id throws', () {
      expect(
        () => dataSource.update(HealthRecord(date: today)),
        throwsArgumentError,
      );
    });
  });

  group('Filtering and aggregation', () {
    setUp(() async {
      await dataSource.insert(HealthRecord(date: DateTime(2026, 8, 10), steps: 1000));
      await dataSource.insert(HealthRecord(date: DateTime(2026, 8, 15), steps: 2000));
      await dataSource.insert(HealthRecord(date: DateTime(2026, 8, 18), steps: 3000, water: 500));
      await dataSource.insert(HealthRecord(date: DateTime(2026, 8, 18), steps: 4000, water: 1500));
    });

    test('getAll returns newest first by default', () async {
      final records = await dataSource.getAll();
      expect(records.map((r) => r.steps), [4000, 3000, 2000, 1000]);
    });

    test('getAll filters by an inclusive date range', () async {
      final records = await dataSource.getAll(
        from: DateTime(2026, 8, 15),
        to: DateTime(2026, 8, 18),
      );
      expect(records.length, 3);
      expect(records.every((r) => !r.date.isBefore(DateTime(2026, 8, 15))), isTrue);
    });

    test('getAll filters by a single date', () async {
      final records = await dataSource.getAll(from: today, to: today);
      expect(records.length, 2);
    });

    test('getDailySummaries sums multiple entries per day', () async {
      final summaries = await dataSource.getDailySummaries(
        from: DateTime(2026, 8, 1),
        to: today,
      );
      expect(summaries.length, 3);
      final todaySummary = summaries.last;
      expect(todaySummary.date, today);
      expect(todaySummary.steps, 7000);
      expect(todaySummary.water, 2000);
      expect(todaySummary.entryCount, 2);
    });

    test('resetWithSampleData replaces existing rows', () async {
      await dataSource.resetWithSampleData();
      expect(await dataSource.count(), SampleData.generate(today: today).length);
    });
  });

  group('Repository', () {
    test('emits a change event after every write', () async {
      final repo = SqfliteHealthRecordRepository(dataSource);
      var events = 0;
      final sub = repo.changes.listen((_) => events++);

      final saved = await repo.addRecord(HealthRecord(date: today, steps: 10));
      expect(saved.id, isNotNull);
      await repo.updateRecord(saved.copyWith(steps: 20));
      await repo.deleteRecord(saved.id!);
      await Future<void>.delayed(Duration.zero);

      expect(events, 3);
      await sub.cancel();
      repo.dispose();
    });

    test('updating a missing record throws RepositoryException', () async {
      final repo = SqfliteHealthRecordRepository(dataSource);
      expect(
        () => repo.updateRecord(HealthRecord(id: 12345, date: today)),
        throwsA(isA<RepositoryException>()),
      );
      repo.dispose();
    });
  });
}
