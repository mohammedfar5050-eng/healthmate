package net.orbitratech.healthmate.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import net.orbitratech.healthmate.core.database.HealthMateDatabase
import net.orbitratech.healthmate.feature.records.data.HealthRecordRepository
import net.orbitratech.healthmate.feature.records.data.OfflineHealthRecordRepository
import net.orbitratech.healthmate.feature.settings.data.DataStoreSettingsRepository
import net.orbitratech.healthmate.feature.settings.data.SettingsRepository
import java.time.Clock

/**
 * Manual dependency-injection container: creates each dependency once and
 * shares it across the app. Tests provide their own container with fakes.
 */
interface AppContainer {
    val clock: Clock
    val healthRecordRepository: HealthRecordRepository
    val settingsRepository: SettingsRepository
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val clock: Clock = Clock.systemDefaultZone()

    private val database: HealthMateDatabase by lazy { HealthMateDatabase.create(context, clock) }

    override val healthRecordRepository: HealthRecordRepository by lazy {
        OfflineHealthRecordRepository(database.healthRecordDao(), clock)
    }

    override val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(context.settingsDataStore)
    }
}
