import 'package:flutter/material.dart';

import '../../data/models/health_metric.dart';
import '../../data/models/health_record.dart';
import 'metric_badge.dart';

enum RecordAction { edit, delete }

/// Card displaying a single health record.
///
/// Tapping the card opens it for editing; the overflow menu offers explicit
/// Edit/Delete actions (discoverable alternative to swipe-to-delete).
class RecordCard extends StatelessWidget {
  const RecordCard({
    super.key,
    required this.record,
    required this.onTap,
    required this.onAction,
    this.entryLabel,
  });

  final HealthRecord record;
  final VoidCallback onTap;
  final ValueChanged<RecordAction> onAction;

  /// Optional label such as "Entry 2" when a day has several entries.
  final String? entryLabel;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 4, 12),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (entryLabel != null) ...[
                      Text(
                        entryLabel!,
                        style: theme.textTheme.labelMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 8),
                    ],
                    Row(
                      children: [
                        for (final metric in HealthMetric.values)
                          Expanded(
                            child: Align(
                              alignment: Alignment.centerLeft,
                              child: MetricValueChip(
                                metric: metric,
                                value: metric.valueOf(record),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
              PopupMenuButton<RecordAction>(
                tooltip: 'Record options',
                icon: const Icon(Icons.more_vert_rounded),
                onSelected: onAction,
                itemBuilder: (context) => const [
                  PopupMenuItem(
                    value: RecordAction.edit,
                    child: ListTile(
                      leading: Icon(Icons.edit_outlined),
                      title: Text('Edit'),
                      contentPadding: EdgeInsets.zero,
                    ),
                  ),
                  PopupMenuItem(
                    value: RecordAction.delete,
                    child: ListTile(
                      leading: Icon(Icons.delete_outline_rounded),
                      title: Text('Delete'),
                      contentPadding: EdgeInsets.zero,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
