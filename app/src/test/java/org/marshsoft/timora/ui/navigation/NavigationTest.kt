package org.marshsoft.timora.ui.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.marshsoft.timora.ui.theme.TimoraTheme

/** Drives the real nav graph (real AppContainer under Robolectric) with a [TestNavHostController]. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var nav: TestNavHostController

    private fun startHome() {
        rule.setContent {
            val context = LocalContext.current
            nav = TestNavHostController(context).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            TimoraTheme { TimoraNavHost(navController = nav) }
        }
        rule.waitUntil(timeoutMillis = 10_000) { has("home_fab") }
    }

    private fun has(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun route() = nav.currentDestination?.route

    private fun count(route: String) = nav.currentBackStack.value.count { it.destination.route == route }

    private fun tagStartsWith(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    @Test fun fabOpensNewTimerEditorAndBackReturnsHome() {
        startHome()
        assertEquals(Routes.HOME, route())
        rule.onNodeWithTag("home_fab").performClick()
        rule.waitForIdle()
        assertEquals(Routes.EDITOR, route())
        assertEquals(0L, nav.currentBackStackEntry?.arguments?.getLong(Routes.ARG_TIMER_ID))
        rule.onNodeWithTag("editor_back").performClick()
        rule.waitForIdle()
        if (route() != Routes.HOME && has("discard_confirm")) {
            rule.onNodeWithTag("discard_confirm").performClick()
        }
        rule.waitUntil(timeoutMillis = 10_000) { route() == Routes.HOME }
        rule.onNodeWithTag("home_fab").assertIsDisplayed()
    }

    @Test fun playOpensRunScreenForThatTimer() {
        startHome()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(tagStartsWith("play_")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(tagStartsWith("play_")).onFirst().performClick()
        rule.waitForIdle()
        assertEquals(Routes.RUN, route())
        assertTrue((nav.currentBackStackEntry?.arguments?.getLong(Routes.ARG_TIMER_ID) ?: 0L) > 0L)
    }

    @Test fun settingsOpensAndBackReturnsHome() {
        startHome()
        rule.onNodeWithTag("home_settings").performClick()
        rule.waitForIdle()
        assertEquals(Routes.SETTINGS, route())
        rule.onNodeWithTag("settings_back").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { route() == Routes.HOME }
    }

    @Test fun doubleTapDoesNotPushTwice() {
        startHome()
        // Fire two clicks within the same frame, as a real double tap would, before navigation recomposes.
        val click = rule.onNodeWithTag("home_fab").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action
        rule.runOnIdle { click?.invoke(); click?.invoke() }
        rule.waitForIdle()
        assertEquals(1, count(Routes.EDITOR))
        assertEquals(1, count(Routes.HOME))
    }

    @Test fun lifecycleHelperOnlyTrueWhenResumed() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.STARTED
        assertFalse(owner.lifecycleIsResumed())
        var fired = 0
        owner.ifResumed { fired++ }
        assertEquals(0, fired)
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertTrue(owner.lifecycleIsResumed())
        owner.ifResumed { fired++ }
        assertEquals(1, fired)
    }
}
