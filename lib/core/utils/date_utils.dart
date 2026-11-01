import 'package:intl/intl.dart';

/// Helpers for working with calendar dates (no time component).
///
/// Dates are persisted in SQLite as ISO-8601 text (`yyyy-MM-dd`). This format
/// sorts correctly as a string, which lets the database filter with
/// `BETWEEN` and order with `ORDER BY date` without any conversion.
class AppDateUtils {
  AppDateUtils._();

  static final DateFormat _dbFormat = DateFormat('yyyy-MM-dd');
  static final DateFormat _longFormat = DateFormat('d MMMM yyyy');
  static final DateFormat _mediumFormat = DateFormat('EEE, d MMM yyyy');
  static final DateFormat _shortFormat = DateFormat('d MMM');
  static final DateFormat _weekdayFormat = DateFormat('E');

  /// Strips the time component from [dateTime].
  static DateTime dateOnly(DateTime dateTime) =>
      DateTime(dateTime.year, dateTime.month, dateTime.day);

  /// Converts a date to the text format stored in the database.
  static String toDbString(DateTime date) => _dbFormat.format(date);

  /// Parses a date stored in the database (`yyyy-MM-dd`).
  static DateTime fromDbString(String value) =>
      dateOnly(_dbFormat.parseStrict(value));

  static bool isSameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  /// e.g. `18 August 2026`.
  static String formatLong(DateTime date) => _longFormat.format(date);

  /// e.g. `Tue, 18 Aug 2026`.
  static String formatMedium(DateTime date) => _mediumFormat.format(date);

  /// e.g. `18 Aug`.
  static String formatShort(DateTime date) => _shortFormat.format(date);

  /// e.g. `Tue`.
  static String formatWeekday(DateTime date) => _weekdayFormat.format(date);

  /// Human friendly label: "Today", "Yesterday" or a medium date.
  static String friendlyLabel(DateTime date, {DateTime? now}) {
    final today = dateOnly(now ?? DateTime.now());
    final d = dateOnly(date);
    if (isSameDay(d, today)) return 'Today';
    if (isSameDay(d, today.subtract(const Duration(days: 1)))) {
      return 'Yesterday';
    }
    return formatMedium(d);
  }

  /// Returns the list of consecutive dates from [from] to [to] (inclusive).
  static List<DateTime> daysInRange(DateTime from, DateTime to) {
    final start = dateOnly(from);
    final end = dateOnly(to);
    final days = <DateTime>[];
    for (var d = start; !d.isAfter(end); d = DateTime(d.year, d.month, d.day + 1)) {
      days.add(d);
    }
    return days;
  }

  /// Greeting based on the time of day.
  static String greeting(DateTime now) {
    final hour = now.hour;
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }
}
