import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'core/constants/app_constants.dart';
import 'core/routing/app_router.dart';
import 'core/theme/app_theme.dart';
import 'features/dashboard/presentation/viewmodels/dashboard_viewmodel.dart';
import 'features/health_records/data/repositories/health_record_repository.dart';
import 'features/health_records/presentation/viewmodels/health_records_viewmodel.dart';
import 'features/home/home_shell.dart';
import 'features/settings/data/settings_repository.dart';
import 'features/settings/presentation/viewmodels/settings_viewmodel.dart';

/// Root widget. Wires the dependency graph with Provider:
///
///   Repository (data)  ->  ViewModels (ChangeNotifier)  ->  Screens (UI)
///
/// Repositories are injected through the constructor, so tests can supply
/// in-memory fakes instead of SQLite / SharedPreferences.
class HealthMateApp extends StatelessWidget {
  const HealthMateApp({
    super.key,
    required this.recordRepository,
    required this.settingsRepository,
    this.clock,
  });

  final HealthRecordRepository recordRepository;
  final SettingsRepository settingsRepository;

  /// Optional clock override (used by widget tests for a fixed "today").
  final DateTime Function()? clock;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider<HealthRecordRepository>.value(value: recordRepository),
        ChangeNotifierProvider(create: (_) => SettingsViewModel(settingsRepository)),
        ChangeNotifierProvider(
          create: (_) =>
              HealthRecordsViewModel(recordRepository, clock: clock)..loadRecords(),
        ),
        ChangeNotifierProvider(
          create: (_) => DashboardViewModel(recordRepository, clock: clock)..load(),
        ),
      ],
      child: Selector<SettingsViewModel, ThemeMode>(
        selector: (_, settings) => settings.themeMode,
        builder: (context, themeMode, _) => MaterialApp(
          title: AppConstants.appName,
          debugShowCheckedModeBanner: false,
          theme: AppTheme.light,
          darkTheme: AppTheme.dark,
          themeMode: themeMode,
          initialRoute: HomeShell.routeName,
          onGenerateRoute: AppRouter.onGenerateRoute,
        ),
      ),
    );
  }
}
