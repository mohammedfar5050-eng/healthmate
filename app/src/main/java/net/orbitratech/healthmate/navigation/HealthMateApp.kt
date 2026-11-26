package net.orbitratech.healthmate.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.orbitratech.healthmate.core.designsystem.component.AppIcon
import net.orbitratech.healthmate.core.designsystem.component.AppSnackbar
import net.orbitratech.healthmate.core.designsystem.component.LocalAppSnackbar
import net.orbitratech.healthmate.core.designsystem.component.ObserveEvents
import net.orbitratech.healthmate.core.designsystem.theme.HealthMateTheme
import net.orbitratech.healthmate.di.AppContainer
import net.orbitratech.healthmate.di.LocalAppContainer
import net.orbitratech.healthmate.di.healthMateViewModel
import net.orbitratech.healthmate.feature.dashboard.ui.DashboardScreen
import net.orbitratech.healthmate.feature.dashboard.ui.DashboardViewModel
import net.orbitratech.healthmate.feature.records.model.HealthRecord
import net.orbitratech.healthmate.feature.records.ui.form.RecordFormScreen
import net.orbitratech.healthmate.feature.records.ui.form.RecordFormViewModel
import net.orbitratech.healthmate.feature.records.ui.list.DateFilter
import net.orbitratech.healthmate.feature.records.ui.list.RecordsEvent
import net.orbitratech.healthmate.feature.records.ui.list.RecordsScreen
import net.orbitratech.healthmate.feature.records.ui.list.RecordsViewModel
import net.orbitratech.healthmate.feature.settings.data.ThemeMode
import net.orbitratech.healthmate.feature.settings.ui.AppViewModel
import net.orbitratech.healthmate.feature.settings.ui.SettingsScreen

/**
 * Root composable: provides dependencies, applies the theme and hosts the
 * navigation graph inside a Scaffold with a Material 3 bottom NavigationBar.
 */
@Composable
fun HealthMateApp(
    container: AppContainer,
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val appViewModel: AppViewModel = healthMateViewModel()
        val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
        onThemeModeChange(themeMode)

        HealthMateTheme(themeMode = themeMode) {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val snackbar = remember { AppSnackbar(snackbarHostState, scope) }
            CompositionLocalProvider(LocalAppSnackbar provides snackbar) {
                AppScaffold(snackbarHostState)
            }
        }
    }
}

@Composable
private fun AppScaffold(snackbarHostState: SnackbarHostState) {
    val navController = rememberNavController()
    val snackbar = LocalAppSnackbar.current

    // Activity-scoped ViewModels shared between tabs (e.g. "See all" on the
    // dashboard sets the records filter, and deletes anywhere offer Undo).
    val recordsViewModel: RecordsViewModel = healthMateViewModel()
    val dashboardViewModel: DashboardViewModel = healthMateViewModel()

    // Make "today" roll over correctly if the app was left open overnight.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        recordsViewModel.refreshDate()
        dashboardViewModel.refreshDate()
    }

    ObserveEvents(recordsViewModel.events) { event ->
        when (event) {
            is RecordsEvent.Deleted -> snackbar.show("Record deleted", actionLabel = "Undo") {
                recordsViewModel.restoreRecord(event.record)
            }
            is RecordsEvent.Error -> snackbar.show(event.message)
        }
    }
    val showUndoForDeleted: (HealthRecord) -> Unit = { record ->
        snackbar.show("Record deleted", actionLabel = "Undo") { recordsViewModel.restoreRecord(record) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
    val showBottomBar = currentTab != null

    Scaffold(
        snackbarHost = {
            // The FABs live in each screen's own Scaffold, so lift snackbars above
            // them here (FAB 56 dp + 16 dp margin) instead of covering them.
            SnackbarHost(
                snackbarHostState,
                Modifier.padding(bottom = if (currentTab?.hasFab == true) 72.dp else 0.dp),
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination) },
                            icon = {
                                AppIcon(if (selected) destination.selectedIcon else destination.icon, null)
                            },
                            label = { Text(destination.label) },
                            modifier = Modifier.testTag("nav_${destination.route}"),
                        )
                    }
                }
            }
        },
        // Inner screens handle the status bar themselves.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.DASHBOARD.route,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(TopLevelDestination.DASHBOARD.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onLogActivity = { navController.navigate(RecordFormDestination.add()) },
                    onOpenRecord = { navController.navigate(RecordFormDestination.edit(it)) },
                    onDeleteRecord = recordsViewModel::deleteRecord,
                    onSeeAllToday = {
                        recordsViewModel.setFilter(DateFilter.Today)
                        navController.navigateToTab(TopLevelDestination.RECORDS)
                    },
                )
            }
            composable(TopLevelDestination.RECORDS.route) {
                RecordsScreen(
                    viewModel = recordsViewModel,
                    onAddRecord = { navController.navigate(RecordFormDestination.add(it)) },
                    onOpenRecord = { navController.navigate(RecordFormDestination.edit(it)) },
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(viewModel = healthMateViewModel())
            }
            composable(
                route = RecordFormDestination.ROUTE,
                arguments = listOf(
                    navArgument(RecordFormViewModel.ARG_RECORD_ID) {
                        type = NavType.LongType
                        defaultValue = RecordFormViewModel.NEW_RECORD
                    },
                    navArgument(RecordFormViewModel.ARG_DATE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                RecordFormScreen(
                    viewModel = healthMateViewModel(),
                    onClose = { navController.popBackStack() },
                    onDeleted = { record ->
                        navController.popBackStack()
                        showUndoForDeleted(record)
                    },
                )
            }
        }
    }
}

/** Standard bottom-navigation behaviour: one copy of each tab, state restored. */
private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
