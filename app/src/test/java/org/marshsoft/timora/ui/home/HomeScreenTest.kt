package org.marshsoft.timora.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.marshsoft.timora.domain.Timer
import org.marshsoft.timora.ui.theme.TimoraTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class Calls {
        var create = 0
        var settings = 0
        val edited = mutableListOf<Long>()
        val run = mutableListOf<Long>()
        val duplicated = mutableListOf<Long>()
        val deleted = mutableListOf<Timer>()
    }

    private fun show(state: HomeUiState, calls: Calls = Calls(), host: SnackbarHostState = SnackbarHostState()): Calls {
        rule.setContent {
            TimoraTheme {
                HomeScreen(
                    state = state,
                    snackbarHostState = host,
                    onCreateTimer = { calls.create++ },
                    onEditTimer = { calls.edited += it },
                    onRunTimer = { calls.run += it },
                    onOpenSettings = { calls.settings++ },
                    onDuplicateTimer = { calls.duplicated += it },
                    onDeleteTimer = { calls.deleted += it },
                )
            }
        }
        return calls
    }

    @Test fun loadingState() {
        show(HomeUiState.Loading)
        rule.onNodeWithTag("home_loading").assertIsDisplayed()
    }

    @Test fun errorState() {
        show(HomeUiState.Error("Oops"))
        rule.onNodeWithTag("home_error").assertIsDisplayed()
        rule.onNodeWithText("Oops").assertIsDisplayed()
    }

    @Test fun emptyStateShowsGuidanceAndCreateButton() {
        val calls = show(HomeUiState.Content(emptyList()))
        rule.onNodeWithTag("home_empty").assertIsDisplayed()
        rule.onNodeWithText("No timers yet").assertIsDisplayed()
        rule.onNodeWithTag("home_empty_create").performClick()
        assertEquals(1, calls.create)
    }

    @Test fun listShowsNameDurationAndSummary() {
        show(HomeUiState.Content(listOf(sampleTimer(1, "Tabata"), sampleTimer(2, "Pomodoro", colorIndex = 3))))
        rule.onNodeWithTag("home_list").assertIsDisplayed()
        rule.onNodeWithText("Tabata").assertIsDisplayed()
        rule.onNodeWithText("Pomodoro").assertIsDisplayed()
        // 8 x (40 + 20) = 480s
        rule.onNodeWithTag("timer_duration_1", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText("8:00").assertCountEquals(2)
        rule.onAllNodesWithText("Work 40s").assertCountEquals(2)
        rule.onAllNodesWithText("Rest 20s").assertCountEquals(2)
        rule.onAllNodesWithText("×8").assertCountEquals(2)
    }

    @Test fun fabAndSettingsInvokeCallbacks() {
        val calls = show(HomeUiState.Content(emptyList()))
        rule.onNodeWithTag("home_fab").performClick()
        rule.onNodeWithTag("home_settings").performClick()
        assertEquals(1, calls.create)
        assertEquals(1, calls.settings)
        rule.onNodeWithTag("home_fab").assertExists()
    }

    @Test fun playAndCardTapInvokeCallbacks() {
        val calls = show(HomeUiState.Content(listOf(sampleTimer(7, "A"))))
        rule.onNodeWithTag("play_7").performClick()
        rule.onNodeWithTag("timer_card_7").performClick()
        assertEquals(listOf(7L), calls.run)
        assertEquals(listOf(7L), calls.edited)
    }

    @Test fun overflowMenuEdit() {
        val calls = show(HomeUiState.Content(listOf(sampleTimer(7, "A"))))
        rule.onNodeWithTag("menu_7").performClick()
        rule.onNodeWithTag("menu_edit_7").performClick()
        assertEquals(listOf(7L), calls.edited)
        rule.onAllNodesWithTag("menu_edit_7").assertCountEquals(0)
    }

    @Test fun overflowMenuDuplicate() {
        val calls = show(HomeUiState.Content(listOf(sampleTimer(7, "A"))))
        rule.onNodeWithTag("menu_7").performClick()
        rule.onNodeWithTag("menu_duplicate_7").performClick()
        assertEquals(listOf(7L), calls.duplicated)
    }

    @Test fun overflowMenuDelete() {
        val timer = sampleTimer(7, "A")
        val calls = show(HomeUiState.Content(listOf(timer)))
        rule.onNodeWithTag("menu_7").performClick()
        rule.onNodeWithTag("menu_delete_7").performClick()
        assertEquals(listOf(timer), calls.deleted)
    }

    @Test fun deleteShowsSnackbarAndUndoRestoresTimer() {
        val repo = FakeTimerRepository(listOf(sampleTimer(1, "Tabata")))
        val vm = HomeViewModel(repo)
        val host = SnackbarHostState()
        rule.setContent {
            TimoraTheme {
                val state by vm.state.collectAsState()
                HomeEventsEffect(vm.events, host, vm::undoDelete)
                HomeScreen(
                    state = state,
                    snackbarHostState = host,
                    onCreateTimer = {},
                    onEditTimer = {},
                    onRunTimer = {},
                    onOpenSettings = {},
                    onDuplicateTimer = vm::duplicate,
                    onDeleteTimer = vm::delete,
                )
            }
        }
        rule.onNodeWithTag("menu_1").performClick()
        rule.onNodeWithTag("menu_delete_1").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Deleted \"Tabata\"").assertIsDisplayed()
        rule.onNodeWithTag("home_empty").assertIsDisplayed()

        rule.onNodeWithText("Undo").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Tabata").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, repo.current.size)
        rule.onNodeWithTag("home_list").assertIsDisplayed()
    }

    @Test fun duplicateThroughViewModelAddsCard() {
        val repo = FakeTimerRepository(listOf(sampleTimer(1, "Tabata")))
        val vm = HomeViewModel(repo)
        val host = SnackbarHostState()
        rule.setContent {
            TimoraTheme {
                val state by vm.state.collectAsState()
                HomeScreen(state, host, {}, {}, {}, {}, vm::duplicate, vm::delete)
            }
        }
        rule.onNodeWithTag("menu_1").performClick()
        rule.onNodeWithTag("menu_duplicate_1").performClick()
        rule.onNodeWithText("Tabata (copy)").assertIsDisplayed()
    }

    @Test fun messageEventShowsSnackbar() {
        val repo = FakeTimerRepository(listOf(sampleTimer(1, "Tabata"))).apply { failDelete = IllegalStateException() }
        val vm = HomeViewModel(repo)
        val host = SnackbarHostState()
        rule.setContent {
            TimoraTheme {
                val state by vm.state.collectAsState()
                HomeEventsEffect(vm.events, host, vm::undoDelete)
                HomeScreen(state, host, {}, {}, {}, {}, vm::duplicate, vm::delete)
            }
        }
        rule.onNodeWithTag("menu_1").performClick()
        rule.onNodeWithTag("menu_delete_1").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Could not delete timer").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun quickSuccessiveDeletesKeepEveryUndo() {
        val a = sampleTimer(1, "Alpha")
        val b = sampleTimer(2, "Beta")
        val repo = FakeTimerRepository(listOf(a, b))
        val vm = HomeViewModel(repo)
        val host = SnackbarHostState()
        rule.setContent {
            TimoraTheme {
                val state by vm.state.collectAsState()
                HomeEventsEffect(vm.events, host, vm::undoDelete)
                HomeScreen(state, host, {}, {}, {}, {}, vm::duplicate, vm::delete)
            }
        }
        rule.runOnIdle {
            vm.delete(a)
            vm.delete(b)
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Deleted \"Alpha\"").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Undo").performClick()
        // the second delete's snackbar must still show up afterwards, with its own Undo
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Deleted \"Beta\"").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Undo").performClick()
        rule.waitUntil(5_000) { repo.current.size == 2 }
        assertEquals(setOf("Alpha", "Beta"), repo.current.map { it.name }.toSet())
    }
}
