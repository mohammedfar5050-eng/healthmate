import 'package:flutter/material.dart';

import '../../../health_records/data/models/health_metric.dart';
import '../../data/daily_goals.dart';
import '../../data/settings_repository.dart';

/// ViewModel (MVVM) for user preferences: daily goals and theme mode.
class SettingsViewModel extends ChangeNotifier {
  SettingsViewModel(this._repository)
      : _goals = _repository.loadGoals(),
        _themeMode = _repository.loadThemeMode();

  final SettingsRepository _repository;

  DailyGoals _goals;
  ThemeMode _themeMode;

  DailyGoals get goals => _goals;
  ThemeMode get themeMode => _themeMode;

  Future<void> updateGoal(HealthMetric metric, int value) async {
    if (value <= 0) return;
    _goals = _goals.copyWithMetric(metric, value);
    notifyListeners();
    await _repository.saveGoals(_goals);
  }

  Future<void> resetGoals() async {
    _goals = const DailyGoals();
    notifyListeners();
    await _repository.saveGoals(_goals);
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    if (mode == _themeMode) return;
    _themeMode = mode;
    notifyListeners();
    await _repository.saveThemeMode(mode);
  }
}
