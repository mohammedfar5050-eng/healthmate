import 'package:flutter_test/flutter_test.dart';
import 'package:healthmate/core/database/sample_data.dart';
import 'package:healthmate/core/utils/date_utils.dart';
import 'package:healthmate/core/utils/validators.dart';
import 'package:healthmate/features/health_records/data/models/health_record.dart';

void main() {
  group('HealthRecord', () {
    test('toMap / fromMap round-trip', () {
      final record = HealthRecord(
        id: 7,
        date: DateTime(2026, 8, 18, 14, 30),
        steps: 7850,
        calories: 420,
        water: 2000,
      );
      final map = record.toMap();
      expect(map, {
        'id': 7,
        'date': '2026-08-18',
        'steps': 7850,
        'calories': 420,
        'water': 2000,
      });
      expect(HealthRecord.fromMap(map), record);
    });

    test('toMap omits id for new records so SQLite can generate it', () {
      final map = HealthRecord(date: DateTime(2026, 1, 1)).toMap();
      expect(map.containsKey('id'), isFalse);
    });

    test('copyWith changes only the given fields', () {
      final original = HealthRecord(id: 1, date: DateTime(2026, 1, 1), steps: 10);
      final copy = original.copyWith(water: 500);
      expect(copy.steps, 10);
      expect(copy.water, 500);
      expect(copy.id, 1);
    });
  });

  group('Validators', () {
    test('steps: empty value is rejected', () {
      expect(Validators.steps(''), isNotNull);
      expect(Validators.steps(null), isNotNull);
    });

    test('steps: non numeric value is rejected', () {
      expect(Validators.steps('abc'), 'Steps must be a whole number');
      expect(Validators.steps('12.5'), 'Steps must be a whole number');
    });

    test('steps: negative and too large values are rejected', () {
      expect(Validators.steps('-1'), isNotNull);
      expect(Validators.steps('100001'), contains('too high'));
    });

    test('valid values pass, including thousands separators', () {
      expect(Validators.steps('7850'), isNull);
      expect(Validators.steps('7,850'), isNull);
      expect(Validators.calories('420'), isNull);
      expect(Validators.water('2000'), isNull);
      expect(Validators.parseInt('7,850'), 7850);
    });

    test('water above 10 litres is rejected', () {
      expect(Validators.water('10001'), contains('too high'));
    });

    test('record date cannot be in the future', () {
      final now = DateTime(2026, 8, 18, 9);
      expect(Validators.recordDate(null, now: now), 'Please select a date');
      expect(Validators.recordDate(DateTime(2026, 8, 19), now: now),
          'Date cannot be in the future');
      expect(Validators.recordDate(DateTime(2026, 8, 18), now: now), isNull);
    });

    test('at least one activity must be greater than zero', () {
      expect(Validators.atLeastOneActivity(0, 0, 0), isNotNull);
      expect(Validators.atLeastOneActivity(0, 0, 250), isNull);
    });

    test('goals must be at least 1', () {
      expect(Validators.goal('0', fieldName: 'Goal', max: 100), isNotNull);
      expect(Validators.goal('50', fieldName: 'Goal', max: 100), isNull);
    });
  });

  group('AppDateUtils', () {
    test('db string round-trip', () {
      final d = DateTime(2026, 2, 5);
      expect(AppDateUtils.toDbString(d), '2026-02-05');
      expect(AppDateUtils.fromDbString('2026-02-05'), d);
    });

    test('friendly labels', () {
      final now = DateTime(2026, 8, 18, 20);
      expect(AppDateUtils.friendlyLabel(DateTime(2026, 8, 18), now: now), 'Today');
      expect(AppDateUtils.friendlyLabel(DateTime(2026, 8, 17), now: now), 'Yesterday');
      expect(AppDateUtils.friendlyLabel(DateTime(2026, 8, 10), now: now),
          'Mon, 10 Aug 2026');
    });

    test('daysInRange is inclusive and crosses month boundaries', () {
      final days = AppDateUtils.daysInRange(DateTime(2026, 7, 30), DateTime(2026, 8, 2));
      expect(days.length, 4);
      expect(days.last, DateTime(2026, 8, 2));
    });
  });

  group('SampleData', () {
    test('is deterministic and never in the future', () {
      final today = DateTime(2026, 8, 18);
      final a = SampleData.generate(today: today);
      final b = SampleData.generate(today: today);
      expect(a, b);
      expect(a.every((r) => !r.date.isAfter(today)), isTrue);
      expect(a.any((r) => AppDateUtils.isSameDay(r.date, today)), isTrue);
    });
  });
}
