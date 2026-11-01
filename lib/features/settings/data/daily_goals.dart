import '../../../core/constants/app_constants.dart';
import '../../health_records/data/models/health_metric.dart';

/// The user's daily targets, used to show progress on the dashboard.
class DailyGoals {
  const DailyGoals({
    this.steps = AppConstants.defaultStepsGoal,
    this.calories = AppConstants.defaultCaloriesGoal,
    this.water = AppConstants.defaultWaterGoal,
  });

  final int steps;
  final int calories;
  final int water;

  int goalFor(HealthMetric metric) => switch (metric) {
        HealthMetric.steps => steps,
        HealthMetric.calories => calories,
        HealthMetric.water => water,
      };

  /// Progress towards the goal for [metric] as a fraction (may exceed 1).
  double progress(HealthMetric metric, int value) {
    final goal = goalFor(metric);
    return goal <= 0 ? 0 : value / goal;
  }

  DailyGoals copyWithMetric(HealthMetric metric, int value) => switch (metric) {
        HealthMetric.steps => DailyGoals(steps: value, calories: calories, water: water),
        HealthMetric.calories => DailyGoals(steps: steps, calories: value, water: water),
        HealthMetric.water => DailyGoals(steps: steps, calories: calories, water: value),
      };

  @override
  bool operator ==(Object other) =>
      other is DailyGoals &&
      other.steps == steps &&
      other.calories == calories &&
      other.water == water;

  @override
  int get hashCode => Object.hash(steps, calories, water);
}
