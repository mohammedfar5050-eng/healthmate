import 'package:sqflite/sqflite.dart';

import '../../../../core/database/database_helper.dart';
import '../../../../core/utils/date_utils.dart';
import '../models/daily_summary.dart';
import '../models/health_record.dart';

/// Data-access object (DAO) for the `health_records` table.
///
/// This is the only class that writes SQL for health records. Everything
/// above it (repository, view models, UI) works with [HealthRecord] objects.
class HealthRecordLocalDataSource {
  HealthRecordLocalDataSource(this._dbHelper);

  final DatabaseHelper _dbHelper;

  static const String _table = DatabaseHelper.tableHealthRecords;

  // ---------------------------------------------------------------- CREATE --

  /// Inserts [record] and returns the generated primary key.
  Future<int> insert(HealthRecord record) async {
    final db = await _dbHelper.database;
    return db.insert(
      _table,
      record.toMap(),
      conflictAlgorithm: ConflictAlgorithm.abort,
    );
  }

  // ------------------------------------------------------------------ READ --

  Future<HealthRecord?> getById(int id) async {
    final db = await _dbHelper.database;
    final rows = await db.query(
      _table,
      where: '${HealthRecord.colId} = ?',
      whereArgs: [id],
      limit: 1,
    );
    return rows.isEmpty ? null : HealthRecord.fromMap(rows.first);
  }

  /// Returns records, optionally limited to the inclusive date range
  /// [from]..[to]. Results are ordered by date, newest first by default.
  ///
  /// Dates are stored as `yyyy-MM-dd` text, so a plain string comparison
  /// (`BETWEEN`) is a correct and index-friendly date filter.
  Future<List<HealthRecord>> getAll({
    DateTime? from,
    DateTime? to,
    bool newestFirst = true,
  }) async {
    final db = await _dbHelper.database;
    final where = <String>[];
    final args = <Object?>[];
    if (from != null) {
      where.add('${HealthRecord.colDate} >= ?');
      args.add(AppDateUtils.toDbString(from));
    }
    if (to != null) {
      where.add('${HealthRecord.colDate} <= ?');
      args.add(AppDateUtils.toDbString(to));
    }
    final direction = newestFirst ? 'DESC' : 'ASC';
    final rows = await db.query(
      _table,
      where: where.isEmpty ? null : where.join(' AND '),
      whereArgs: args.isEmpty ? null : args,
      orderBy: '${HealthRecord.colDate} $direction, '
          '${HealthRecord.colId} $direction',
    );
    return rows.map(HealthRecord.fromMap).toList();
  }

  /// Aggregates totals per day inside [from]..[to] using `GROUP BY`.
  Future<List<DailySummary>> getDailySummaries({
    required DateTime from,
    required DateTime to,
  }) async {
    final db = await _dbHelper.database;
    final rows = await db.rawQuery(
      '''
      SELECT date,
             SUM(steps)    AS steps,
             SUM(calories) AS calories,
             SUM(water)    AS water,
             COUNT(*)      AS entries
      FROM $_table
      WHERE date BETWEEN ? AND ?
      GROUP BY date
      ORDER BY date ASC
      ''',
      [AppDateUtils.toDbString(from), AppDateUtils.toDbString(to)],
    );
    return rows.map(DailySummary.fromMap).toList();
  }

  Future<int> count() async {
    final db = await _dbHelper.database;
    final result = await db.rawQuery('SELECT COUNT(*) AS c FROM $_table');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  // ---------------------------------------------------------------- UPDATE --

  /// Updates the row matching `record.id`. Returns the number of rows changed.
  Future<int> update(HealthRecord record) async {
    final id = record.id;
    if (id == null) {
      throw ArgumentError('Cannot update a record without an id');
    }
    final db = await _dbHelper.database;
    return db.update(
      _table,
      record.toMap()..remove(HealthRecord.colId),
      where: '${HealthRecord.colId} = ?',
      whereArgs: [id],
    );
  }

  // ---------------------------------------------------------------- DELETE --

  /// Deletes the row with [id]. Returns the number of rows removed.
  Future<int> delete(int id) async {
    final db = await _dbHelper.database;
    return db.delete(
      _table,
      where: '${HealthRecord.colId} = ?',
      whereArgs: [id],
    );
  }

  Future<int> deleteAll() async {
    final db = await _dbHelper.database;
    return db.delete(_table);
  }

  /// Deletes everything and inserts fresh sample data in one transaction.
  Future<void> resetWithSampleData() async {
    final db = await _dbHelper.database;
    await db.transaction((txn) async {
      await txn.delete(_table);
      await _dbHelper.insertSampleData(txn);
    });
  }
}
