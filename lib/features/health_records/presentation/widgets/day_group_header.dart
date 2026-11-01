import 'package:flutter/material.dart';

import '../../../../core/utils/date_utils.dart';
import '../../../../core/utils/formatters.dart';
import '../../data/models/daily_summary.dart';
import '../../data/models/health_metric.dart';

/// Header shown above the records of one day, with the day's totals.
class DayGroupHeader extends StatelessWidget {
  const DayGroupHeader({super.key, required this.summary, required this.now});

  final DailySummary summary;
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final label = AppDateUtils.friendlyLabel(summary.date, now: now);
    final isRelative = label == 'Today' || label == 'Yesterday';

    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 16, 4, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.w800,
                    color: theme.colorScheme.primary,
                  ),
                ),
                if (isRelative)
                  Text(
                    AppDateUtils.formatMedium(summary.date),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
          ),
          if (summary.entryCount > 1)
            Tooltip(
              message: 'Daily total of ${summary.entryCount} entries',
              child: Text(
                'Total  ${HealthMetric.steps.emoji} ${AppFormatters.compact(summary.steps)}'
                '  ${HealthMetric.calories.emoji} ${AppFormatters.compact(summary.calories)}'
                '  ${HealthMetric.water.emoji} ${AppFormatters.compact(summary.water)}',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
