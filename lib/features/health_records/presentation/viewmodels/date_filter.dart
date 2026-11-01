import '../../../../core/utils/date_utils.dart';

/// The kind of date filter currently applied on the records screen.
enum DateFilterType { all, today, last7Days, last30Days, singleDate, range }

/// Immutable description of a date filter.
///
/// [resolve] converts the filter into concrete inclusive `from`/`to` bounds
/// that are passed down to the SQL query.
class DateFilter {
  const DateFilter._(this.type, {this.start, this.end});

  const DateFilter.all() : this._(DateFilterType.all);
  const DateFilter.today() : this._(DateFilterType.today);
  const DateFilter.last7Days() : this._(DateFilterType.last7Days);
  const DateFilter.last30Days() : this._(DateFilterType.last30Days);

  DateFilter.singleDate(DateTime date)
      : this._(
          DateFilterType.singleDate,
          start: AppDateUtils.dateOnly(date),
          end: AppDateUtils.dateOnly(date),
        );

  factory DateFilter.range(DateTime start, DateTime end) {
    var s = AppDateUtils.dateOnly(start);
    var e = AppDateUtils.dateOnly(end);
    if (e.isBefore(s)) {
      final tmp = s;
      s = e;
      e = tmp;
    }
    if (AppDateUtils.isSameDay(s, e)) return DateFilter.singleDate(s);
    return DateFilter._(DateFilterType.range, start: s, end: e);
  }

  final DateFilterType type;
  final DateTime? start;
  final DateTime? end;

  bool get isActive => type != DateFilterType.all;

  bool get isCustom =>
      type == DateFilterType.singleDate || type == DateFilterType.range;

  /// Inclusive date bounds for the query (`null` means unbounded).
  ({DateTime? from, DateTime? to}) resolve(DateTime now) {
    final today = AppDateUtils.dateOnly(now);
    return switch (type) {
      DateFilterType.all => (from: null, to: null),
      DateFilterType.today => (from: today, to: today),
      DateFilterType.last7Days => (
          from: DateTime(today.year, today.month, today.day - 6),
          to: today,
        ),
      DateFilterType.last30Days => (
          from: DateTime(today.year, today.month, today.day - 29),
          to: today,
        ),
      DateFilterType.singleDate || DateFilterType.range => (from: start, to: end),
    };
  }

  /// Short label displayed in the filter bar / empty state.
  String get label => switch (type) {
        DateFilterType.all => 'All records',
        DateFilterType.today => 'Today',
        DateFilterType.last7Days => 'Last 7 days',
        DateFilterType.last30Days => 'Last 30 days',
        DateFilterType.singleDate => AppDateUtils.formatMedium(start!),
        DateFilterType.range =>
          '${AppDateUtils.formatShort(start!)} – '
              '${AppDateUtils.formatShort(end!)} ${end!.year}',
      };

  /// Phrase used in sentences, e.g. "No entries in the last 7 days".
  String get phrase => switch (type) {
        DateFilterType.all => 'yet',
        DateFilterType.today => 'for today',
        DateFilterType.last7Days => 'in the last 7 days',
        DateFilterType.last30Days => 'in the last 30 days',
        DateFilterType.singleDate => 'on ${AppDateUtils.formatMedium(start!)}',
        DateFilterType.range => 'between ${AppDateUtils.formatShort(start!)} '
            'and ${AppDateUtils.formatShort(end!)} ${end!.year}',
      };

  @override
  bool operator ==(Object other) =>
      other is DateFilter &&
      other.type == type &&
      other.start == start &&
      other.end == end;

  @override
  int get hashCode => Object.hash(type, start, end);
}
