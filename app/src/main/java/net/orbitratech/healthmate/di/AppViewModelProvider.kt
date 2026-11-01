package net.orbitratech.healthmate.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.orbitratech.healthmate.feature.dashboard.ui.DashboardViewModel
import net.orbitratech.healthmate.feature.records.ui.form.RecordFormViewModel
import net.orbitratech.healthmate.feature.records.ui.list.RecordsViewModel
import net.orbitratech.healthmate.feature.settings.ui.AppViewModel
import net.orbitratech.healthmate.feature.settings.ui.SettingsViewModel

/** Gives composables access to the dependency container (real or test fake). */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided")
}

/** Factory that knows how to build every ViewModel from the [AppContainer]. */
object AppViewModelProvider {
    fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { AppViewModel(container.settingsRepository) }
        initializer {
            DashboardViewModel(container.healthRecordRepository, container.settingsRepository, container.clock)
        }
        initializer { RecordsViewModel(container.healthRecordRepository, container.clock) }
        initializer {
            RecordFormViewModel(
                createSavedStateHandle(),
                container.healthRecordRepository,
                container.settingsRepository,
                container.clock,
            )
        }
        initializer { SettingsViewModel(container.settingsRepository, container.healthRecordRepository) }
    }
}

/** `viewModel()` wired to HealthMate's factory. */
@Composable
inline fun <reified VM : ViewModel> healthMateViewModel(): VM {
    val container = LocalAppContainer.current
    val factory = remember(container) { AppViewModelProvider.factory(container) }
    return viewModel(factory = factory)
}
