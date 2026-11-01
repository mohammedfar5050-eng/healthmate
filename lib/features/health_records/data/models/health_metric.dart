import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import 'health_record.dart';

/// The three activities tracked by HealthMate.
///
/// Centralising label, unit, icon and colour here keeps the UI consistent:
/// every screen that shows "water" uses the same blue drop icon and unit.
enum HealthMetric {
  steps(
    label: 'Steps',
    unit: 'steps',
    shortUnit: '',
    icon: Icons.directions_walk_rounded,
    color: AppColors.steps,
    emoji: '👣',
  ),
  calories(
    label: 'Calories Burned',
    unit: 'kcal',
    shortUnit: 'kcal',
    icon: Icons.local_fire_department_rounded,
    color: AppColors.calories,
    emoji: '🔥',
  ),
  water(
    label: 'Water Intake',
    unit: 'ml',
    shortUnit: 'ml',
    icon: Icons.water_drop_rounded,
    color: AppColors.water,
    emoji: '💧',
  );

  const HealthMetric({
    required this.label,
    required this.unit,
    required this.shortUnit,
    required this.icon,
    required this.color,
    required this.emoji,
  });

  final String label;
  final String unit;
  final String shortUnit;
  final IconData icon;
  final Color color;
  final String emoji;

  /// Short label used on compact controls (chips, segmented buttons).
  String get shortLabel => switch (this) {
        HealthMetric.steps => 'Steps',
        HealthMetric.calories => 'Calories',
        HealthMetric.water => 'Water',
      };

  int valueOf(HealthRecord record) => switch (this) {
        HealthMetric.steps => record.steps,
        HealthMetric.calories => record.calories,
        HealthMetric.water => record.water,
      };
}
