import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../core/constants/app_constants.dart';
import 'daily_goals.dart';

/// Persists lightweight user preferences (goals and theme) with
/// SharedPreferences. Health records themselves live in SQLite; preferences
/// are simple key/value pairs, for which SharedPreferences is the better fit.
class SettingsRepository {
  SettingsRepository(this._prefs);

  final SharedPreferences _prefs;

  static const String _kStepsGoal = 'goal_steps';
  static const String _kCaloriesGoal = 'goal_calories';
  static const String _kWaterGoal = 'goal_water';
  static const String _kThemeMode = 'theme_mode';

  DailyGoals loadGoals() => DailyGoals(
        steps: _prefs.getInt(_kStepsGoal) ?? AppConstants.defaultStepsGoal,
        calories:
            _prefs.getInt(_kCaloriesGoal) ?? AppConstants.defaultCaloriesGoal,
        water: _prefs.getInt(_kWaterGoal) ?? AppConstants.defaultWaterGoal,
      );

  Future<void> saveGoals(DailyGoals goals) async {
    await Future.wait([
      _prefs.setInt(_kStepsGoal, goals.steps),
      _prefs.setInt(_kCaloriesGoal, goals.calories),
      _prefs.setInt(_kWaterGoal, goals.water),
    ]);
  }

  ThemeMode loadThemeMode() {
    final name = _prefs.getString(_kThemeMode);
    return ThemeMode.values.firstWhere(
      (m) => m.name == name,
      orElse: () => ThemeMode.system,
    );
  }

  Future<void> saveThemeMode(ThemeMode mode) =>
      _prefs.setString(_kThemeMode, mode.name);
}
