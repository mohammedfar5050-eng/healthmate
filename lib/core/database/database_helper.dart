import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'sample_data.dart';

/// Owns the SQLite connection for the whole application.
///
/// Responsibilities:
///  * open (and lazily create) the `healthmate.db` file,
///  * define the schema in [_onCreate],
///  * seed dummy records the first time the database is created so the app
///    has something to show during testing/demonstration.
///
/// A [DatabaseFactory] and [path] can be injected, which allows unit tests to
/// run against an in-memory database through `sqflite_common_ffi`.
class DatabaseHelper {
  DatabaseHelper({
    DatabaseFactory? factory,
    String? path,
    this.seedSampleData = true,
    DateTime Function()? clock,
  })  : _factory = factory,
        _path = path,
        _clock = clock ?? DateTime.now;

  static const String databaseName = 'healthmate.db';
  static const int databaseVersion = 1;
  static const String tableHealthRecords = 'health_records';

  final DatabaseFactory? _factory;
  final String? _path;
  final DateTime Function() _clock;

  /// Whether dummy records are inserted when the database is first created.
  final bool seedSampleData;

  Future<Database>? _database;

  /// Returns the open database, opening it on first access.
  ///
  /// The `Future` itself is cached so concurrent callers share one
  /// connection instead of racing to open the file twice.
  Future<Database> get database => _database ??= _open();

  Future<Database> _open() async {
    final factory = _factory ?? databaseFactory;
    final path =
        _path ?? p.join(await factory.getDatabasesPath(), databaseName);
    return factory.openDatabase(
      path,
      options: OpenDatabaseOptions(
        version: databaseVersion,
        onConfigure: _onConfigure,
        onCreate: _onCreate,
      ),
    );
  }

  Future<void> _onConfigure(Database db) async {
    await db.execute('PRAGMA foreign_keys = ON');
  }

  /// Creates the schema. Runs only once, when the database file is new.
  Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE $tableHealthRecords (
        id       INTEGER PRIMARY KEY AUTOINCREMENT,
        date     TEXT    NOT NULL,
        steps    INTEGER NOT NULL DEFAULT 0 CHECK (steps >= 0),
        calories INTEGER NOT NULL DEFAULT 0 CHECK (calories >= 0),
        water    INTEGER NOT NULL DEFAULT 0 CHECK (water >= 0)
      )
    ''');
    // Most queries filter or group by date, so index it.
    await db.execute(
      'CREATE INDEX idx_${tableHealthRecords}_date '
      'ON $tableHealthRecords (date)',
    );

    if (seedSampleData) {
      await insertSampleData(db);
    }
  }

  /// Inserts the dummy records in a single batch (one transaction).
  Future<void> insertSampleData(DatabaseExecutor db) async {
    final batch = db.batch();
    for (final record in SampleData.generate(today: _clock())) {
      batch.insert(tableHealthRecords, record.toMap());
    }
    await batch.commit(noResult: true);
  }

  /// Closes the connection (used by tests and on app shutdown).
  Future<void> close() async {
    final future = _database;
    _database = null;
    if (future != null) {
      final db = await future;
      await db.close();
    }
  }
}
