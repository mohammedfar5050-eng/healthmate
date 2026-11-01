/// Application-wide constants (names, limits and default goals).
///
/// Keeping these values in one place avoids "magic numbers" scattered across
/// the UI, the validators and the database layer.
class AppConstants {
  AppConstants._();

  static const String appName = 'HealthMate';
  static const String appTagline = 'Personal Health & Wellness Tracker';
  static const String appVersion = '1.0.0';

  // ---- Validation limits -------------------------------------------------
  /// Upper bounds used by the form validators. They are generous enough for
  /// real-world usage while still catching obvious typing mistakes.
  static const int maxSteps = 100000;
  static const int maxCalories = 10000;
  static const int maxWaterMl = 10000;

  /// Earliest date a record may be logged for.
  static final DateTime earliestRecordDate = DateTime(2000, 1, 1);

  // ---- Default daily goals ----------------------------------------------
  static const int defaultStepsGoal = 10000;
  static const int defaultCaloriesGoal = 500;
  static const int defaultWaterGoal = 2500;

  // ---- Quick-add water presets (ml) --------------------------------------
  static const List<int> waterPresets = [250, 500, 750];

  // ---- Dashboard ---------------------------------------------------------
  /// Number of days shown on the weekly trend chart.
  static const int trendDays = 7;

  /// Number of days inspected when calculating the logging streak.
  static const int streakLookbackDays = 60;
}
