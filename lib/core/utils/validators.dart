import '../constants/app_constants.dart';
import 'date_utils.dart';

/// Reusable, pure validation functions used by the forms.
///
/// Each validator follows the Flutter `FormFieldValidator<String>` contract:
/// it returns `null` when the value is valid, otherwise a user-facing error
/// message. Because they are pure functions they are easy to unit test.
class Validators {
  Validators._();

  /// Validates a whole-number field that must lie in `[min, max]`.
  static String? wholeNumber(
    String? value, {
    required String fieldName,
    int min = 0,
    required int max,
    bool required = true,
  }) {
    final text = value?.trim() ?? '';
    if (text.isEmpty) {
      return required ? 'Please enter $fieldName (use 0 if none)' : null;
    }
    // Allow users to type thousands separators such as "7,850".
    final normalised = text.replaceAll(',', '');
    final parsed = int.tryParse(normalised);
    if (parsed == null) {
      return '$fieldName must be a whole number';
    }
    if (parsed < min) {
      return '$fieldName cannot be less than $min';
    }
    if (parsed > max) {
      return '$fieldName looks too high (max ${_withSeparators(max)})';
    }
    return null;
  }

  static String? steps(String? value) => wholeNumber(
        value,
        fieldName: 'Steps',
        max: AppConstants.maxSteps,
      );

  static String? calories(String? value) => wholeNumber(
        value,
        fieldName: 'Calories',
        max: AppConstants.maxCalories,
      );

  static String? water(String? value) => wholeNumber(
        value,
        fieldName: 'Water intake',
        max: AppConstants.maxWaterMl,
      );

  /// Validates a record date: required, not in the future and not absurdly
  /// far in the past.
  static String? recordDate(DateTime? date, {DateTime? now}) {
    if (date == null) return 'Please select a date';
    final today = AppDateUtils.dateOnly(now ?? DateTime.now());
    final d = AppDateUtils.dateOnly(date);
    if (d.isAfter(today)) return 'Date cannot be in the future';
    if (d.isBefore(AppConstants.earliestRecordDate)) {
      return 'Date is too far in the past';
    }
    return null;
  }

  /// Cross-field rule: a record must contain at least one non-zero activity.
  static String? atLeastOneActivity(int steps, int calories, int water) {
    if (steps == 0 && calories == 0 && water == 0) {
      return 'Enter at least one value greater than 0';
    }
    return null;
  }

  /// Validates a daily goal (must be positive and within the metric limit).
  static String? goal(String? value, {required String fieldName, required int max}) =>
      wholeNumber(value, fieldName: fieldName, min: 1, max: max);

  /// Parses a validated numeric field, tolerating thousands separators.
  static int parseInt(String? value) =>
      int.tryParse((value ?? '').trim().replaceAll(',', '')) ?? 0;

  static String _withSeparators(int value) {
    final s = value.toString();
    final buffer = StringBuffer();
    for (var i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) buffer.write(',');
      buffer.write(s[i]);
    }
    return buffer.toString();
  }
}
