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
import kotlinx.coroutines.launch
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
        assertEquals(AppSettings(ThemeMode.SYSTEM, false, true, true), settings.settings.first())
    }

    @Test fun settersPersistIndependently() = runBlocking {
        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.settings.first().themeMode)
        settings.setDynamicColor(true)
        settings.setSoundEnabled(false)
        settings.setVibrationEnabled(false)
        assertEquals(AppSettings(ThemeMode.DARK, true, false, false), settings.settings.first())
        settings.setThemeMode(ThemeMode.LIGHT)
        settings.setSoundEnabled(true)
        assertEquals(AppSettings(ThemeMode.LIGHT, true, true, false), settings.settings.first())
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
            val seeder = SampleSeeder(db.timerDao(), store) { 10_000 }
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

    @Test fun seederOnlySetsFlagWhenTimersAlreadyExist() = runBlocking {
        // Simulates a crash after the transactional insert but before the flag was written.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TimoraDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.timerDao()
            dao.insertAll(SampleTimers.all(10_000))
            assertEquals(2, dao.count())
            val seeder = SampleSeeder(dao, store) { 10_000 }
            assertFalse(seeder.seedIfNeeded())
            assertEquals(2, dao.count())
            assertFalse(seeder.seedIfNeeded())
        } finally {
            db.close()
        }
    }

    @Test fun gatedRepositoryWaitsForSeeding() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TimoraDatabase::class.java).allowMainThreadQueries().build()
        try {
            val seeded = kotlinx.coroutines.flow.MutableStateFlow(false)
            val repo = SeedGatedTimerRepository(TimerRepositoryImpl(db.timerDao()), seeded)
            val first = kotlinx.coroutines.CompletableDeferred<List<org.marshsoft.timora.domain.Timer>>()
            val job = scope.launch { first.complete(repo.observeAll().first()) }
            kotlinx.coroutines.delay(150)
            assertFalse(first.isCompleted)
            SampleSeeder(db.timerDao(), store) { 10_000 }.seedIfNeeded()
            seeded.value = true
            assertEquals(2, first.await().size)
            job.cancel()
        } finally {
            db.close()
        }
    }
}
