import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../dashboard/presentation/screens/dashboard_screen.dart';
import '../health_records/presentation/screens/records_screen.dart';
import '../health_records/presentation/viewmodels/date_filter.dart';
import '../health_records/presentation/viewmodels/health_records_viewmodel.dart';
import '../settings/presentation/screens/settings_screen.dart';

/// Root screen with a Material 3 bottom [NavigationBar].
///
/// An [IndexedStack] keeps every tab alive, so scroll positions and filters
/// are preserved when the user switches between tabs.
class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  static const String routeName = '/';

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  void _select(int index) => setState(() => _index = index);

  void _openTodayRecords() {
    context.read<HealthRecordsViewModel>().setFilter(const DateFilter.today());
    _select(1);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: [
          DashboardScreen(onViewTodayRecords: _openTodayRecords),
          const RecordsScreen(),
          const SettingsScreen(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: _select,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.space_dashboard_outlined),
            selectedIcon: Icon(Icons.space_dashboard_rounded),
            label: 'Dashboard',
          ),
          NavigationDestination(
            icon: Icon(Icons.list_alt_outlined),
            selectedIcon: Icon(Icons.list_alt_rounded),
            label: 'Records',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings_rounded),
            label: 'Settings',
          ),
        ],
      ),
    );
  }
}
