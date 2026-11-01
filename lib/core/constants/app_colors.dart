import 'package:flutter/material.dart';

/// Colour palette for HealthMate.
///
/// Each health metric owns a distinct, meaningful colour so the user can
/// recognise it at a glance anywhere in the app:
///  * Steps    -> green  (movement / activity)
///  * Calories -> red    (energy / heat)
///  * Water    -> blue   (hydration)
class AppColors {
  AppColors._();

  /// Seed colour used to generate the Material 3 colour scheme.
  static const Color seed = Color(0xFF00897B); // teal 600

  static const Color steps = Color(0xFF2E7D32); // green 800
  static const Color stepsLight = Color(0xFF66BB6A);

  static const Color calories = Color(0xFFE53935); // red 600
  static const Color caloriesLight = Color(0xFFEF5350);

  static const Color water = Color(0xFF1E88E5); // blue 600
  static const Color waterLight = Color(0xFF42A5F5);

  static const Color success = Color(0xFF43A047);
  static const Color warning = Color(0xFFFFA000);
}
