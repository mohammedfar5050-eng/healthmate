import 'package:flutter/material.dart';

import '../../../../core/constants/app_constants.dart';
import '../viewmodels/date_filter.dart';

/// Horizontal bar of filter chips: quick presets plus "Pick date" and
/// "Date range" actions that open the Material date pickers.
class DateFilterBar extends StatelessWidget {
  const DateFilterBar({
    super.key,
    required this.filter,
    required this.today,
    required this.onChanged,
  });

  final DateFilter filter;
  final DateTime today;
  final ValueChanged<DateFilter> onChanged;

  Future<void> _pickDate(BuildContext context) async {
    final picked = await showDatePicker(
      context: context,
      helpText: 'Show records for',
      initialDate: filter.type == DateFilterType.singleDate ? filter.start : today,
      firstDate: AppConstants.earliestRecordDate,
      lastDate: today,
    );
    if (picked != null) onChanged(DateFilter.singleDate(picked));
  }

  Future<void> _pickRange(BuildContext context) async {
    final initial = filter.type == DateFilterType.range
        ? DateTimeRange(start: filter.start!, end: filter.end!)
        : DateTimeRange(
            start: DateTime(today.year, today.month, today.day - 6),
            end: today,
          );
    final picked = await showDateRangePicker(
      context: context,
      helpText: 'Select date range',
      saveText: 'Apply',
      initialDateRange: initial,
      firstDate: AppConstants.earliestRecordDate,
      lastDate: today,
    );
    if (picked != null) onChanged(DateFilter.range(picked.start, picked.end));
  }

  @override
  Widget build(BuildContext context) {
    final presets = <DateFilter>[
      const DateFilter.all(),
      const DateFilter.today(),
      const DateFilter.last7Days(),
      const DateFilter.last30Days(),
    ];

    return SizedBox(
      height: 48,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        children: [
          for (final preset in presets) ...[
            ChoiceChip(
              label: Text(preset.type == DateFilterType.all ? 'All' : preset.label),
              selected: filter == preset,
              onSelected: (_) => onChanged(preset),
            ),
            const SizedBox(width: 8),
          ],
          FilterChip(
            key: const Key('filter-pick-date'),
            avatar: const Icon(Icons.calendar_today_rounded, size: 18),
            label: Text(filter.type == DateFilterType.singleDate
                ? filter.label
                : 'Pick date'),
            selected: filter.type == DateFilterType.singleDate,
            showCheckmark: false,
            onSelected: (_) => _pickDate(context),
          ),
          const SizedBox(width: 8),
          FilterChip(
            key: const Key('filter-pick-range'),
            avatar: const Icon(Icons.date_range_rounded, size: 18),
            label: Text(filter.type == DateFilterType.range ? filter.label : 'Date range'),
            selected: filter.type == DateFilterType.range,
            showCheckmark: false,
            onSelected: (_) => _pickRange(context),
          ),
        ],
      ),
    );
  }
}
