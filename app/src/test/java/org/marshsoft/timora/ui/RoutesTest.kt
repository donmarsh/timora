package org.marshsoft.timora.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.marshsoft.timora.ui.navigation.Routes
import org.marshsoft.timora.ui.run.RunRoute
import org.marshsoft.timora.ui.settings.SettingsRoute
import org.marshsoft.timora.ui.theme.TimoraTheme
import org.junit.Assert.assertEquals

/** Exercises the Route composables against the real AppContainer (Room + DataStore under Robolectric). */
@RunWith(AndroidJUnit4::class)
class RoutesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun settingsRouteRenders() {
        rule.setContent { TimoraTheme { SettingsRoute(onBack = {}) } }
        rule.onNodeWithTag("settings_sound").assertIsDisplayed()
    }

    @Test fun runRouteForUnknownTimerShowsNotFound() {
        rule.setContent { TimoraTheme { RunRoute(timerId = 987_654L, onClose = {}) } }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTagCount("run_not_found") > 0
        }
        rule.onNodeWithTag("run_not_found").assertIsDisplayed()
    }

    @Test fun routeHelpers() {
        assertEquals("editor/5", Routes.editor(5))
        assertEquals("run/7", Routes.run(7))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
