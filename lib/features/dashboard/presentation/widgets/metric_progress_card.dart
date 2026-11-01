import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/utils/formatters.dart';
import '../../../health_records/data/models/health_metric.dart';

/// Card showing today's value for a metric with an animated progress ring
/// towards the user's daily goal.
class MetricProgressCard extends StatelessWidget {
  const MetricProgressCard({
    super.key,
    required this.metric,
    required this.value,
    required this.goal,
    this.onTap,
  });

  final HealthMetric metric;
  final int value;
  final int goal;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final progress = goal <= 0 ? 0.0 : value / goal;
    final reached = progress >= 1;
    final remaining = (goal - value).clamp(0, goal);

    return Semantics(
      label: '${metric.label}: ${AppFormatters.number(value)} ${metric.unit} '
          'of ${AppFormatters.number(goal)} goal, ${AppFormatters.percent(progress)}',
      container: true,
      excludeSemantics: true,
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                _ProgressRing(metric: metric, progress: progress),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${metric.emoji} ${metric.label}',
                        style: theme.textTheme.labelLarge?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text.rich(
                        TextSpan(
                          children: [
                            TextSpan(
                              text: AppFormatters.number(value),
                              style: theme.textTheme.headlineSmall?.copyWith(
                                fontWeight: FontWeight.w800,
                                color: metric.color,
                              ),
                            ),
                            TextSpan(
                              text: '  ${metric.unit}',
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                        key: Key('today-${metric.name}'),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Goal ${AppFormatters.number(goal)} ${metric.unit}',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                reached
                    ? Chip(
                        avatar: const Icon(Icons.check_circle_rounded,
                            size: 18, color: AppColors.success),
                        label: const Text('Done'),
                        visualDensity: VisualDensity.compact,
                        side: BorderSide.none,
                        backgroundColor: AppColors.success.withValues(alpha: 0.12),
                      )
                    : Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            AppFormatters.number(remaining),
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          Text(
                            'to go',
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ProgressRing extends StatelessWidget {
  const _ProgressRing({required this.metric, required this.progress});

  final HealthMetric metric;
  final double progress;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 64,
      height: 64,
      child: TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: progress.clamp(0, 1).toDouble()),
        duration: const Duration(milliseconds: 700),
        curve: Curves.easeOutCubic,
        builder: (context, animated, _) => Stack(
          fit: StackFit.expand,
          children: [
            CircularProgressIndicator(
              value: animated,
              strokeWidth: 7,
              strokeCap: StrokeCap.round,
              color: metric.color,
              backgroundColor: metric.color.withValues(alpha: 0.14),
            ),
            Center(child: Icon(metric.icon, color: metric.color, size: 26)),
          ],
        ),
      ),
    );
  }
}
