import 'package:intl/intl.dart';

/// Number formatting helpers used by the UI.
class AppFormatters {
  AppFormatters._();

  static final NumberFormat _decimal = NumberFormat.decimalPattern('en_US');
  static final NumberFormat _compact = NumberFormat.compact(locale: 'en_US');

  /// `7850` -> `7,850`.
  static String number(num value) => _decimal.format(value);

  /// `12500` -> `12.5K` (used on chart axes where space is limited).
  static String compact(num value) => _compact.format(value);

  /// `0.734` -> `73%`.
  static String percent(double fraction) =>
      '${(fraction * 100).clamp(0, 999).round()}%';
}
