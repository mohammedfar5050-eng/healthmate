import 'package:flutter/material.dart';

import '../../../../core/utils/formatters.dart';
import '../../data/models/health_metric.dart';

/// Circular, tinted icon representing a [HealthMetric].
class MetricIcon extends StatelessWidget {
  const MetricIcon(this.metric, {super.key, this.size = 20, this.padding = 8});

  final HealthMetric metric;
  final double size;
  final double padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.all(padding),
      decoration: BoxDecoration(
        color: metric.color.withValues(alpha: 0.14),
        shape: BoxShape.circle,
      ),
      child: Icon(metric.icon, size: size, color: metric.color),
    );
  }
}

/// Compact "icon + value" pill used inside record cards.
class MetricValueChip extends StatelessWidget {
  const MetricValueChip({
    super.key,
    required this.metric,
    required this.value,
  });

  final HealthMetric metric;
  final int value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = value == 0;
    final color = muted ? theme.colorScheme.outline : metric.color;
    return Semantics(
      label: '${metric.shortLabel}: ${AppFormatters.number(value)} ${metric.unit}',
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(metric.icon, size: 18, color: color),
          const SizedBox(width: 4),
          Flexible(
            child: Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: AppFormatters.number(value),
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  if (metric.shortUnit.isNotEmpty)
                    TextSpan(
                      text: ' ${metric.shortUnit}',
                      style: TextStyle(
                        fontSize: 12,
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                ],
              ),
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: muted ? theme.colorScheme.outline : null,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
