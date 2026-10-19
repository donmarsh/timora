package org.marshsoft.timora.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.marshsoft.timora.data.AppSettings
import org.marshsoft.timora.data.SettingsRepository
import org.marshsoft.timora.data.ThemeMode

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** Dynamic color is only offered on Android 12+. */
    val dynamicColorAvailable: Boolean = false,
    val versionName: String = "",
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    versionName: String = "",
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = repository.settings
        .map { SettingsUiState(it, dynamicColorAvailable, versionName) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(AppSettings(), dynamicColorAvailable, versionName),
        )

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { repository.setThemeMode(mode) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { repository.setDynamicColor(enabled) } }
    fun setSoundEnabled(enabled: Boolean) { viewModelScope.launch { repository.setSoundEnabled(enabled) } }
    fun setVibrationEnabled(enabled: Boolean) { viewModelScope.launch { repository.setVibrationEnabled(enabled) } }

    companion object {
        fun factory(repository: SettingsRepository, versionName: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsViewModel(repository, versionName = versionName) } }
    }
}
