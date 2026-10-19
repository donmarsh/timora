package org.marshsoft.timora.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsAndSeedTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var settings: DataStoreSettingsRepository

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb").also { it.delete() } }
        settings = DataStoreSettingsRepository(store)
    }

    @After fun tearDown() = scope.cancel()

    @Test fun defaults() = runBlocking {
        assertEquals(AppSettings(ThemeMode.SYSTEM, true, true, true), settings.settings.first())
    }

    @Test fun settersPersistIndependently() = runBlocking {
        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.settings.first().themeMode)
        settings.setDynamicColor(false)
        settings.setSoundEnabled(false)
        settings.setVibrationEnabled(false)
        assertEquals(AppSettings(ThemeMode.DARK, false, false, false), settings.settings.first())
        settings.setThemeMode(ThemeMode.LIGHT)
        settings.setSoundEnabled(true)
        assertEquals(AppSettings(ThemeMode.LIGHT, false, true, false), settings.settings.first())
    }

    @Test fun corruptThemeFallsBackToSystem() = runBlocking {
        store.edit { it[stringPreferencesKey("theme_mode")] = "PURPLE" }
        assertEquals(ThemeMode.SYSTEM, settings.settings.first().themeMode)
    }

    @Test fun seederRunsOnceOnly() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TimoraDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = TimerRepositoryImpl(db.timerDao()) { 10_000 }
            val seeder = SampleSeeder(repo, store) { 10_000 }
            assertTrue(seeder.seedIfNeeded())
            val names = repo.observeAll().first().map { it.name }.toSet()
            assertEquals(setOf("HIIT 20/10 Tabata", "Pomodoro"), names)
            assertFalse(seeder.seedIfNeeded())
            repo.observeAll().first().forEach { repo.delete(it.id) }
            assertFalse(seeder.seedIfNeeded())
            assertTrue(repo.observeAll().first().isEmpty())
        } finally {
            db.close()
        }
    }
}
