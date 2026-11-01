import 'package:flutter/material.dart';

import '../../features/health_records/presentation/screens/record_form_screen.dart';
import '../../features/home/home_shell.dart';

/// Centralised named-route table.
///
/// Keeping navigation in one place means screens only need to know a route
/// name and its argument type, not how other screens are constructed.
class AppRouter {
  AppRouter._();

  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case HomeShell.routeName:
        return MaterialPageRoute(builder: (_) => const HomeShell(), settings: settings);
      case RecordFormScreen.routeName:
        final args = settings.arguments is RecordFormArgs
            ? settings.arguments! as RecordFormArgs
            : const RecordFormArgs();
        return MaterialPageRoute<bool>(
          builder: (_) => RecordFormScreen(args: args),
          settings: settings,
          fullscreenDialog: args.record == null,
        );
      default:
        return MaterialPageRoute(
          builder: (_) => Scaffold(
            appBar: AppBar(),
            body: Center(child: Text('Page not found: ${settings.name}')),
          ),
        );
    }
  }
}
