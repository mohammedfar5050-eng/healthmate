import '../../../../core/utils/date_utils.dart';

/// Model representing one row of the `health_records` table.
///
/// The model is immutable; use [copyWith] to derive an updated instance.
/// [toMap] / [HealthRecord.fromMap] convert between the Dart object and the
/// `Map<String, Object?>` representation used by sqflite.
class HealthRecord {
  const HealthRecord({
    this.id,
    required this.date,
    this.steps = 0,
    this.calories = 0,
    this.water = 0,
  });

  /// Primary key. `null` until the record has been inserted.
  final int? id;

  /// Calendar day the activity belongs to (time component is ignored).
  final DateTime date;

  /// Number of steps walked.
  final int steps;

  /// Calories burned (kcal).
  final int calories;

  /// Water intake in millilitres.
  final int water;

  // Column names – shared with the database layer to avoid typos.
  static const String colId = 'id';
  static const String colDate = 'date';
  static const String colSteps = 'steps';
  static const String colCalories = 'calories';
  static const String colWater = 'water';

  factory HealthRecord.fromMap(Map<String, Object?> map) {
    return HealthRecord(
      id: map[colId] as int?,
      date: AppDateUtils.fromDbString(map[colDate] as String),
      steps: (map[colSteps] as num?)?.toInt() ?? 0,
      calories: (map[colCalories] as num?)?.toInt() ?? 0,
      water: (map[colWater] as num?)?.toInt() ?? 0,
    );
  }

  /// Converts the record to a map for sqflite. The `id` is only included when
  /// it is known so that SQLite can auto-generate it on insert.
  Map<String, Object?> toMap() {
    return {
      if (id != null) colId: id,
      colDate: AppDateUtils.toDbString(date),
      colSteps: steps,
      colCalories: calories,
      colWater: water,
    };
  }

  HealthRecord copyWith({
    int? id,
    DateTime? date,
    int? steps,
    int? calories,
    int? water,
  }) {
    return HealthRecord(
      id: id ?? this.id,
      date: date ?? this.date,
      steps: steps ?? this.steps,
      calories: calories ?? this.calories,
      water: water ?? this.water,
    );
  }

  @override
  bool operator ==(Object other) {
    return other is HealthRecord &&
        other.id == id &&
        AppDateUtils.isSameDay(other.date, date) &&
        other.steps == steps &&
        other.calories == calories &&
        other.water == water;
  }

  @override
  int get hashCode => Object.hash(
        id,
        AppDateUtils.toDbString(date),
        steps,
        calories,
        water,
      );

  @override
  String toString() =>
      'HealthRecord(id: $id, date: ${AppDateUtils.toDbString(date)}, '
      'steps: $steps, calories: $calories, water: $water)';
}
