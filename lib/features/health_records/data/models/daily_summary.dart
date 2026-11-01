import '../../../../core/utils/date_utils.dart';
import 'health_metric.dart';

/// Aggregated totals for a single day.
///
/// A user may log several entries on the same day (e.g. a morning walk and an
/// evening run). The dashboard shows the *sum* of those entries, which is
/// produced in SQL with `GROUP BY date` (see the local data source).
class DailySummary {
  const DailySummary({
    required this.date,
    this.steps = 0,
    this.calories = 0,
    this.water = 0,
    this.entryCount = 0,
  });

  factory DailySummary.empty(DateTime date) =>
      DailySummary(date: AppDateUtils.dateOnly(date));

  factory DailySummary.fromMap(Map<String, Object?> map) {
    return DailySummary(
      date: AppDateUtils.fromDbString(map['date'] as String),
      steps: (map['steps'] as num?)?.toInt() ?? 0,
      calories: (map['calories'] as num?)?.toInt() ?? 0,
      water: (map['water'] as num?)?.toInt() ?? 0,
      entryCount: (map['entries'] as num?)?.toInt() ?? 0,
    );
  }

  final DateTime date;
  final int steps;
  final int calories;
  final int water;

  /// Number of individual records that make up this summary.
  final int entryCount;

  bool get hasData => entryCount > 0;

  int valueOf(HealthMetric metric) => switch (metric) {
        HealthMetric.steps => steps,
        HealthMetric.calories => calories,
        HealthMetric.water => water,
      };

  @override
  bool operator ==(Object other) =>
      other is DailySummary &&
      AppDateUtils.isSameDay(other.date, date) &&
      other.steps == steps &&
      other.calories == calories &&
      other.water == water &&
      other.entryCount == entryCount;

  @override
  int get hashCode => Object.hash(
        AppDateUtils.toDbString(date),
        steps,
        calories,
        water,
        entryCount,
      );

  @override
  String toString() => 'DailySummary(${AppDateUtils.toDbString(date)}, '
      'steps: $steps, calories: $calories, water: $water, entries: $entryCount)';
}
