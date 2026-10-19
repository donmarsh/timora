package org.marshsoft.timora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.marshsoft.timora.data.AppSettings
import org.marshsoft.timora.ui.common.appContainer
import org.marshsoft.timora.ui.common.toUi
import org.marshsoft.timora.ui.navigation.TimoraNavHost
import org.marshsoft.timora.ui.theme.TimoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = appContainer.settingsRepository.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            TimoraTheme(themeMode = settings.themeMode.toUi(), dynamicColor = settings.dynamicColor) {
                TimoraNavHost()
            }
        }
    }
}
