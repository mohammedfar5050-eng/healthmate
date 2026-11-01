import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:healthmate/features/health_records/presentation/widgets/record_card.dart';

import '../helpers/test_app.dart';

/// Widget tests covering the main user journeys. Each test documents a test
/// case (TC-xx) that is also listed in the README test table.
void main() {
  Future<void> openRecordsTab(WidgetTester tester) async {
    await tester.tap(find.text('Records'));
    await tester.pumpAndSettle();
  }

  Future<void> openAddForm(WidgetTester tester) async {
    await tester.tap(find.text('Log activity'));
    await tester.pumpAndSettle();
    expect(find.text('Add Record'), findsOneWidget);
  }

  /// Scrolls the visible dashboard list until [finder] is built and centred.
  Future<void> scrollTo(WidgetTester tester, Finder finder) async {
    await tester.scrollUntilVisible(finder, 200,
        scrollable: find.byType(Scrollable).first);
    await tester.pumpAndSettle();
    await Scrollable.ensureVisible(tester.element(finder), alignment: 0.5);
    await tester.pumpAndSettle();
  }

  Future<void> tapSave(WidgetTester tester) async {
    final save = find.byKey(const Key('save-button'));
    await tester.ensureVisible(save);
    await tester.tap(save);
    await tester.pumpAndSettle();
  }

  testWidgets("TC-01 Dashboard shows today's totals", (tester) async {
    await pumpHealthMate(tester);

    expect(find.text('Today – 18 August 2026'), findsOneWidget);
    expect(find.textContaining('7,850'), findsWidgets);
    expect(find.textContaining('420'), findsWidgets);
    expect(find.textContaining('2,000'), findsWidgets);
    await scrollTo(tester, find.text('Last 7 days'));
    expect(find.text('Last 7 days'), findsOneWidget);
  });

  testWidgets('TC-02 Empty form shows required-field validation errors', (tester) async {
    await pumpHealthMate(tester);
    await openAddForm(tester);

    await tapSave(tester);

    expect(find.text('Please enter Steps (use 0 if none)'), findsOneWidget);
    expect(find.text('Please enter Calories (use 0 if none)'), findsOneWidget);
    expect(find.text('Please enter Water intake (use 0 if none)'), findsOneWidget);
    expect(find.text('Add Record'), findsOneWidget, reason: 'stays on the form');
  });

  testWidgets('TC-03 Out-of-range and all-zero values are rejected', (tester) async {
    final repo = await pumpHealthMate(tester);
    final before = repo.all.length;
    await openAddForm(tester);

    await tester.enterText(find.byKey(const Key('field-steps')), '250000');
    await tester.enterText(find.byKey(const Key('field-calories')), '0');
    await tester.enterText(find.byKey(const Key('field-water')), '0');
    await tapSave(tester);
    expect(find.text('Steps looks too high (max 100,000)'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('field-steps')), '0');
    await tapSave(tester);
    expect(find.text('Enter at least one value greater than 0'), findsOneWidget);
    expect(repo.all.length, before, reason: 'nothing saved');
  });

  testWidgets('TC-04 Adding a valid record updates the dashboard', (tester) async {
    final repo = await pumpHealthMate(tester);
    final before = repo.all.length;
    await openAddForm(tester);

    await tester.enterText(find.byKey(const Key('field-steps')), '1150');
    await tester.enterText(find.byKey(const Key('field-calories')), '80');
    await tester.enterText(find.byKey(const Key('field-water')), '500');
    await tapSave(tester);

    expect(find.text('Add Record'), findsNothing, reason: 'form closed');
    expect(repo.all.length, before + 1);
    expect(find.textContaining('Record added'), findsOneWidget);
    // 7,850 + 1,150 = 9,000 steps; 2,000 + 500 = 2,500 ml water.
    expect(find.textContaining('9,000'), findsWidgets);
    expect(find.textContaining('2,500'), findsWidgets);
  });

  testWidgets('TC-05 Records list groups entries and filters by date', (tester) async {
    await pumpHealthMate(tester);
    await openRecordsTab(tester);

    expect(find.text('Health Records'), findsOneWidget);
    expect(find.text('Today'), findsWidgets);
    expect(find.text('Yesterday'), findsOneWidget);
    expect(find.byType(RecordCard), findsNWidgets(4));

    await tester.tap(find.widgetWithText(ChoiceChip, 'Today'));
    await tester.pumpAndSettle();
    expect(find.byType(RecordCard), findsNWidgets(2));
    expect(find.textContaining('2 entries'), findsOneWidget);

    await tester.tap(find.widgetWithText(ChoiceChip, 'All'));
    await tester.pumpAndSettle();
    expect(find.byType(RecordCard), findsNWidgets(4));
  });

  testWidgets('TC-06 Editing a record saves the new values', (tester) async {
    final repo = await pumpHealthMate(tester);
    await openRecordsTab(tester);

    // Open the 12 Aug record (the only entry with 6,400 steps).
    await tester.tap(find.text('6,400'));
    await tester.pumpAndSettle();
    expect(find.text('Edit Record'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('field-steps')), '6600');
    await tapSave(tester);

    expect(find.text('Edit Record'), findsNothing);
    expect(repo.all.any((r) => r.steps == 6600), isTrue);
    expect(find.text('6,600'), findsOneWidget);
  });

  testWidgets('TC-07 Deleting asks for confirmation and supports undo', (tester) async {
    final repo = await pumpHealthMate(tester);
    await openRecordsTab(tester);
    final before = repo.all.length;

    await tester.tap(find.byTooltip('Record options').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete').last);
    await tester.pumpAndSettle();
    expect(find.text('Delete record?'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilledButton, 'Delete'));
    await tester.pumpAndSettle();
    expect(repo.all.length, before - 1);
    expect(find.text('Record deleted'), findsOneWidget);

    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();
    expect(repo.all.length, before);
  });

  testWidgets('TC-08 Quick add water increases today\'s water', (tester) async {
    final repo = await pumpHealthMate(tester);

    final button = find.byKey(const Key('quick-water-250'));
    await scrollTo(tester, button);
    await tester.tap(button);
    await tester.pumpAndSettle();

    final todayWater = repo.all
        .where((r) => r.date == testToday)
        .fold<int>(0, (sum, r) => sum + r.water);
    expect(todayWater, 2250);
    expect(find.text('Added 250 ml of water 💧'), findsOneWidget);
  });

  testWidgets('TC-09 Daily goal can be changed in Settings', (tester) async {
    await pumpHealthMate(tester);
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('goal-water')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('goal-field')), '0');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(find.text('Goal cannot be less than 1'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('goal-field')), '3000');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(find.text('3,000 ml per day'), findsOneWidget);
  });

  testWidgets('TC-10 Empty database shows a helpful empty state', (tester) async {
    await pumpHealthMate(tester, records: const []);
    await scrollTo(tester, find.textContaining('Nothing logged yet today'));
    expect(find.textContaining('Nothing logged yet today'), findsOneWidget);

    await openRecordsTab(tester);
    expect(find.text('No records yet'), findsOneWidget);
  });
}
