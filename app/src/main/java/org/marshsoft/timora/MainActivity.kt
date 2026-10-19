package org.marshsoft.timora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.marshsoft.timora.data.AppSettings
import org.marshsoft.timora.ui.common.appContainer
import org.marshsoft.timora.ui.common.toUi
import org.marshsoft.timora.ui.navigation.TimoraNavHost
import org.marshsoft.timora.ui.theme.TimoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // null until DataStore has emitted the persisted settings; the splash screen stays up until then
        // so the user never sees the default theme flash before their own.
        val loaded: StateFlow<AppSettings?> = appContainer.settingsRepository.settings
            .map<AppSettings, AppSettings?> { it }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        splash.setKeepOnScreenCondition { loaded.value == null }
        setContent {
            val settings by loaded.collectAsStateWithLifecycle()
            val current = settings ?: AppSettings()
            TimoraTheme(themeMode = current.themeMode.toUi(), dynamicColor = current.dynamicColor) {
                TimoraNavHost()
            }
        }
    }
}
