package org.marshsoft.timora.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            AppSettings(
                themeMode = p[THEME]?.let { s -> ThemeMode.entries.firstOrNull { it.name == s } } ?: ThemeMode.SYSTEM,
                dynamicColor = p[DYNAMIC] ?: false,
                soundEnabled = p[SOUND] ?: true,
                vibrationEnabled = p[VIBRATION] ?: true,
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) { dataStore.edit { it[THEME] = mode.name } }
    override suspend fun setDynamicColor(enabled: Boolean) { dataStore.edit { it[DYNAMIC] = enabled } }
    override suspend fun setSoundEnabled(enabled: Boolean) { dataStore.edit { it[SOUND] = enabled } }
    override suspend fun setVibrationEnabled(enabled: Boolean) { dataStore.edit { it[VIBRATION] = enabled } }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
    }
}

/**
 * Seeds the sample timers exactly once (tracked in DataStore so deleting them is permanent).
 * All samples are inserted in a single Room transaction, then the flag is set. If the process dies between
 * the two, the next launch sees existing timers and only sets the flag, so samples are never seeded twice.
 */
class SampleSeeder(
    private val dao: TimerDao,
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Returns true if seeding happened. */
    suspend fun seedIfNeeded(): Boolean {
        if (dataStore.data.first()[SEEDED] == true) return false
        val seeded = if (dao.count() == 0) {
            dao.insertAll(SampleTimers.all(clock()))
            true
        } else {
            false
        }
        dataStore.edit { it[SEEDED] = true }
        return seeded
    }

    private companion object {
        val SEEDED = booleanPreferencesKey("samples_seeded")
    }
}
