package org.marshsoft.timora.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.marshsoft.timora.data.AppSettings
import org.marshsoft.timora.data.ThemeMode
import org.marshsoft.timora.ui.theme.TimoraTheme

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    private var back = 0
    private var theme: ThemeMode? = null
    private var dynamic: Boolean? = null
    private var sound: Boolean? = null
    private var vibration: Boolean? = null

    private fun show(state: SettingsUiState) {
        rule.setContent {
            TimoraTheme {
                SettingsScreen(
                    state = state,
                    onBack = { back++ },
                    onThemeMode = { theme = it },
                    onDynamicColor = { dynamic = it },
                    onSound = { sound = it },
                    onVibration = { vibration = it },
                )
            }
        }
    }

    @Test fun showsSelectedThemeAndVersion() {
        show(SettingsUiState(AppSettings(themeMode = ThemeMode.DARK), dynamicColorAvailable = true, versionName = "9.9"))
        rule.onNodeWithTag("settings_theme_DARK").assertIsSelected()
        rule.onNodeWithTag("settings_version").assertTextEquals("Version 9.9")
    }

    @Test fun themeButtonsInvokeCallback() {
        show(SettingsUiState(dynamicColorAvailable = true))
        rule.onNodeWithTag("settings_theme_LIGHT").performClick()
        assertEquals(ThemeMode.LIGHT, theme)
        rule.onNodeWithTag("settings_theme_SYSTEM").performClick()
        assertEquals(ThemeMode.SYSTEM, theme)
    }

    @Test fun togglesInvokeCallbacksWithInvertedValue() {
        show(SettingsUiState(AppSettings(dynamicColor = true, soundEnabled = true, vibrationEnabled = false), true))
        rule.onNodeWithTag("settings_dynamic_color").performScrollTo().performClick()
        rule.onNodeWithTag("settings_sound").performScrollTo().performClick()
        rule.onNodeWithTag("settings_vibration").performScrollTo().performClick()
        assertEquals(false, dynamic)
        assertEquals(false, sound)
        assertEquals(true, vibration)
    }

    @Test fun dynamicColorHiddenWhenUnavailable() {
        show(SettingsUiState(dynamicColorAvailable = false))
        rule.onNodeWithTag("settings_dynamic_color").assertDoesNotExist()
        rule.onNodeWithTag("settings_sound").assertIsDisplayed()
    }

    @Test fun backInvokesCallback() {
        show(SettingsUiState())
        rule.onNodeWithTag("settings_back").performClick()
        assertEquals(1, back)
    }
}
