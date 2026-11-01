import 'dart:math' as math;

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../../../core/utils/date_utils.dart';
import '../../../../core/utils/formatters.dart';
import '../../../health_records/data/models/daily_summary.dart';
import '../../../health_records/data/models/health_metric.dart';

/// Bar chart of the last 7 days for the selected metric, with a dashed line
/// marking the daily goal. Today's bar is highlighted.
class WeeklyTrendChart extends StatelessWidget {
  const WeeklyTrendChart({
    super.key,
    required this.days,
    required this.metric,
    required this.goal,
  });

  final List<DailySummary> days;
  final HealthMetric metric;
  final int goal;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final maxValue = days.fold<int>(0, (m, d) => math.max(m, d.valueOf(metric)));
    final maxY = _niceMax(math.max(maxValue, goal).toDouble());
    final interval = maxY / 4;

    return AspectRatio(
      aspectRatio: 1.7,
      child: BarChart(
        BarChartData(
          maxY: maxY,
          minY: 0,
          alignment: BarChartAlignment.spaceAround,
          borderData: FlBorderData(show: false),
          gridData: FlGridData(
            drawVerticalLine: false,
            horizontalInterval: interval,
            getDrawingHorizontalLine: (_) => FlLine(
              color: scheme.outlineVariant.withValues(alpha: 0.6),
              strokeWidth: 1,
              dashArray: const [4, 4],
            ),
          ),
          extraLinesData: ExtraLinesData(
            horizontalLines: [
              if (goal > 0)
                HorizontalLine(
                  y: goal.toDouble(),
                  color: metric.color.withValues(alpha: 0.8),
                  strokeWidth: 1.5,
                  dashArray: const [6, 4],
                  label: HorizontalLineLabel(
                    show: true,
                    alignment: Alignment.topRight,
                    padding: const EdgeInsets.only(right: 4, bottom: 2),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: metric.color,
                      fontWeight: FontWeight.w700,
                    ),
                    labelResolver: (_) => 'Goal',
                  ),
                ),
            ],
          ),
          titlesData: FlTitlesData(
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            leftTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 40,
                interval: interval,
                getTitlesWidget: (value, meta) => SideTitleWidget(
                  meta: meta,
                  child: Text(
                    AppFormatters.compact(value),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ),
            ),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 28,
                getTitlesWidget: (value, meta) {
                  final index = value.toInt();
                  if (index < 0 || index >= days.length) return const SizedBox();
                  final isToday = index == days.length - 1;
                  return SideTitleWidget(
                    meta: meta,
                    child: Text(
                      isToday ? 'Today' : AppDateUtils.formatWeekday(days[index].date),
                      style: theme.textTheme.labelSmall?.copyWith(
                        fontWeight: isToday ? FontWeight.w800 : FontWeight.w500,
                        color: isToday ? metric.color : scheme.onSurfaceVariant,
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
          barTouchData: BarTouchData(
            touchTooltipData: BarTouchTooltipData(
              getTooltipColor: (_) => scheme.inverseSurface,
              tooltipBorderRadius: BorderRadius.circular(10),
              fitInsideHorizontally: true,
              fitInsideVertically: true,
              getTooltipItem: (group, groupIndex, rod, rodIndex) {
                final day = days[group.x];
                return BarTooltipItem(
                  '${AppFormatters.number(day.valueOf(metric))} ${metric.unit}\n',
                  TextStyle(
                    color: scheme.onInverseSurface,
                    fontWeight: FontWeight.w700,
                  ),
                  children: [
                    TextSpan(
                      text: AppDateUtils.formatShort(day.date),
                      style: TextStyle(
                        color: scheme.onInverseSurface.withValues(alpha: 0.8),
                        fontWeight: FontWeight.w400,
                        fontSize: 12,
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
          barGroups: [
            for (var i = 0; i < days.length; i++)
              BarChartGroupData(
                x: i,
                barRods: [
                  BarChartRodData(
                    toY: days[i].valueOf(metric).toDouble(),
                    width: 18,
                    color: i == days.length - 1
                        ? metric.color
                        : metric.color.withValues(alpha: 0.55),
                    borderRadius: const BorderRadius.vertical(top: Radius.circular(6)),
                    backDrawRodData: BackgroundBarChartRodData(
                      show: true,
                      toY: maxY,
                      color: metric.color.withValues(alpha: 0.06),
                    ),
                  ),
                ],
              ),
          ],
        ),
        duration: const Duration(milliseconds: 350),
        curve: Curves.easeOutCubic,
      ),
    );
  }

  /// Rounds the axis maximum up so that each of the 4 grid intervals is a
  /// "nice" number (e.g. 2,500 or 4,000), keeping axis labels tidy.
  static double _niceMax(double value) {
    if (value <= 0) return 4;
    final rawInterval = value * 1.1 / 4;
    final magnitude =
        math.pow(10, (math.log(rawInterval) / math.ln10).floor()).toDouble();
    const steps = [1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0];
    final normalised = rawInterval / magnitude;
    final nice = steps.firstWhere((s) => s >= normalised, orElse: () => 10.0);
    return nice * magnitude * 4;
  }
}
