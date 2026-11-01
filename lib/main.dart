import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app.dart';
import 'core/database/database_helper.dart';
import 'features/health_records/data/datasources/health_record_local_datasource.dart';
import 'features/health_records/data/repositories/health_record_repository.dart';
import 'features/settings/data/settings_repository.dart';

/// Composition root: creates the concrete data-layer objects and hands them
/// to [HealthMateApp].
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
  ]);

  final prefs = await SharedPreferences.getInstance();
  final databaseHelper = DatabaseHelper();
  final recordRepository = SqfliteHealthRecordRepository(
    HealthRecordLocalDataSource(databaseHelper),
  );

  runApp(HealthMateApp(
    recordRepository: recordRepository,
    settingsRepository: SettingsRepository(prefs),
  ));
}
