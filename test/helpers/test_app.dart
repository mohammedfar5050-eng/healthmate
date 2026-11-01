import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:healthmate/app.dart';
import 'package:healthmate/features/health_records/data/models/health_record.dart';
import 'package:healthmate/features/settings/data/settings_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'fake_health_record_repository.dart';

/// Fixed "now" used by widget tests: the date from the coursework brief.
final testNow = DateTime(2026, 8, 18, 9, 30);
final testToday = DateTime(2026, 8, 18);

/// Records matching the brief's example: today = 7,850 steps,
/// 420 kcal and 2,000 ml spread over two entries.
List<HealthRecord> defaultTestRecords() => [
      HealthRecord(date: testToday, steps: 5000, calories: 250, water: 1200),
      HealthRecord(date: testToday, steps: 2850, calories: 170, water: 800),
      HealthRecord(date: DateTime(2026, 8, 17), steps: 9100, calories: 480, water: 2300),
      HealthRecord(date: DateTime(2026, 8, 12), steps: 6400, calories: 300, water: 1900),
    ];

/// Pumps the full app with in-memory dependencies and a phone-sized screen.
Future<FakeHealthRecordRepository> pumpHealthMate(
  WidgetTester tester, {
  List<HealthRecord>? records,
}) async {
  tester.view.physicalSize = const Size(1170, 2532);
  tester.view.devicePixelRatio = 3.0;
  addTearDown(tester.view.reset);

  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final repo = FakeHealthRecordRepository(
    initial: records ?? defaultTestRecords(),
    sampleToday: testToday,
  );

  await tester.pumpWidget(HealthMateApp(
    recordRepository: repo,
    settingsRepository: SettingsRepository(prefs),
    clock: () => testNow,
  ));
  await tester.pumpAndSettle();
  return repo;
}
