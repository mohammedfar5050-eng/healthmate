import 'dart:math';

import '../../features/health_records/data/models/health_record.dart';
import '../utils/date_utils.dart';

/// Generates realistic dummy records for initial testing / demonstration.
///
/// Records are generated relative to [today] so the dashboard and the weekly
/// chart always have data, no matter when the app is first installed. A fixed
/// random seed keeps the output deterministic (useful for tests).
class SampleData {
  SampleData._();

  /// Number of past days (including today) that receive sample data.
  static const int days = 21;

  static List<HealthRecord> generate({required DateTime today, int seed = 42}) {
    final random = Random(seed);
    final base = AppDateUtils.dateOnly(today);
    final records = <HealthRecord>[];

    for (var offset = days - 1; offset >= 0; offset--) {
      final date = DateTime(base.year, base.month, base.day - offset);

      // Leave a couple of gaps so the history looks natural.
      if (offset == 9 || offset == 16) continue;

      if (offset == 0) {
        // Today: a partially completed day, matching the brief's example.
        records.add(HealthRecord(
          date: date,
          steps: 5200,
          calories: 280,
          water: 1250,
        ));
        records.add(HealthRecord(
          date: date,
          steps: 2650,
          calories: 140,
          water: 750,
        ));
        continue;
      }

      final steps = 4000 + random.nextInt(9000); // 4,000 - 12,999
      final calories = (steps * (0.04 + random.nextDouble() * 0.02)).round();
      final water = 1500 + random.nextInt(9) * 150; // 1,500 - 2,700

      // Every few days split the activity into two separate entries.
      if (offset % 4 == 0) {
        final morningSteps = (steps * 0.4).round();
        final morningCalories = (calories * 0.4).round();
        records.add(HealthRecord(
          date: date,
          steps: morningSteps,
          calories: morningCalories,
          water: 750,
        ));
        records.add(HealthRecord(
          date: date,
          steps: steps - morningSteps,
          calories: calories - morningCalories,
          water: water - 750,
        ));
      } else {
        records.add(HealthRecord(
          date: date,
          steps: steps,
          calories: calories,
          water: water,
        ));
      }
    }
    return records;
  }
}
