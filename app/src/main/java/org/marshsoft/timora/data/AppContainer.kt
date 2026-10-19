package org.marshsoft.timora.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI container, owned by [org.marshsoft.timora.TimoraApplication]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = applicationScope) {
        appContext.filesDir.resolve("datastore/timora_settings.preferences_pb")
    }
    val database: TimoraDatabase by lazy { TimoraDatabase.create(appContext) }
    val timerRepository: TimerRepository by lazy { TimerRepositoryImpl(database.timerDao()) }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(dataStore) }
    private val seeder by lazy { SampleSeeder(timerRepository, dataStore) }

    /** Kicks off first-launch seeding in the background. */
    fun seedSamples() {
        applicationScope.launch { seeder.seedIfNeeded() }
    }
}
