package org.marshsoft.timora.ui.run

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.marshsoft.timora.domain.RunState
import org.marshsoft.timora.domain.RunStatus
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.TimerEngine
import org.marshsoft.timora.domain.TimerPlan
import org.marshsoft.timora.ui.theme.SegmentTypeKey
import org.marshsoft.timora.ui.theme.ThemeMode
import org.marshsoft.timora.ui.theme.TimoraTheme
import org.marshsoft.timora.ui.theme.segmentColorsFor
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert

@RunWith(AndroidJUnit4::class)
class RunScreenTest {
    @get:Rule val rule = createComposeRule()

    private var playPause = 0
    private var next = 0
    private var prev = 0
    private var restart = 0
    private var closed = 0

    private fun runState(status: RunStatus, step: Int = 0, tickMs: Long = 0, cycles: Int = 2, blocks: Int = 1): RunState {
        val engine = TimerEngine(TimerPlan.from(workRestTimer(cycles = cycles, blocks = blocks)))
        engine.start()
        repeat(step) { engine.skipNext() }
        if (tickMs > 0) engine.tick(tickMs)
        when (status) {
            RunStatus.PAUSED -> engine.pause()
            RunStatus.FINISHED -> repeat(100) { engine.skipNext() }
            else -> Unit
        }
        return engine.state.value
    }

    private fun ready(
        run: RunState,
        block: String? = null,
        cycle: String? = "Cycle 1/2",
        next: String? = "Rest · 0:02",
    ) = RunUiState.Ready("Test timer", run, block, cycle, next)

    private fun show(state: RunUiState) {
        rule.setContent {
            TimoraTheme {
                RunScreen(
                    state = state,
                    onTogglePlayPause = { playPause++ },
                    onSkipNext = { next++ },
                    onSkipPrevious = { prev++ },
                    onRestart = { restart++ },
                    onClose = { closed++ },
                )
            }
        }
    }

    @Test fun loadingShowsSpinner() {
        show(RunUiState.Loading)
        rule.onNodeWithTag("run_loading").assertIsDisplayed()
    }

    @Test fun notFoundShowsMessageAndGoesBack() {
        show(RunUiState.NotFound)
        rule.onNodeWithText("Timer not found").assertIsDisplayed()
        rule.onNodeWithTag("run_not_found_back").performClick()
        assertEquals(1, closed)
    }

    @Test fun runningShowsTimeNamesLabelsAndNext() {
        show(ready(runState(RunStatus.RUNNING, tickMs = 1_000), block = "Block 1/2"))
        rule.onNodeWithTag("run_time").assertTextEquals("0:04")
        rule.onNodeWithTag("run_segment_name").assertTextEquals("Work")
        // Segment is named "Work", same as its type, so the redundant type label is hidden.
        rule.onNodeWithTag("run_type_label").assertDoesNotExist()
        rule.onNodeWithTag("run_timer_name").assertTextEquals("Test timer")
        rule.onNodeWithTag("run_block_label").assertTextContains("Block 1/2")
        rule.onNodeWithTag("run_cycle_label").assertTextContains("Cycle 1/2")
        rule.onNodeWithTag("run_next_up").assertExists()
        rule.onNodeWithText("Rest · 0:02", substring = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("run_total_remaining").assertTextEquals("0:13")
        rule.onNodeWithTag("run_paused_label").assertDoesNotExist()
        rule.onNodeWithTag("run_play_pause").assertIsDisplayed()
    }

    @Test fun labelsAreHiddenWhenNull() {
        show(ready(runState(RunStatus.RUNNING), block = null, cycle = null, next = null))
        rule.onNodeWithTag("run_block_label").assertDoesNotExist()
        rule.onNodeWithTag("run_cycle_label").assertDoesNotExist()
        rule.onNodeWithTag("run_next_up").assertDoesNotExist()
    }

    @Test fun restSegmentShowsRestLabel() {
        show(ready(runState(RunStatus.RUNNING, step = 1)))
        rule.onNodeWithTag("run_type_label").assertDoesNotExist()
        rule.onNodeWithTag("run_segment_name").assertTextEquals("Rest")
    }

    @Test fun pausedShowsPausedLabel() {
        show(ready(runState(RunStatus.PAUSED, tickMs = 500)))
        rule.onNodeWithTag("run_paused_label").assertIsDisplayed()
    }

    @Test fun controlsInvokeCallbacks() {
        show(ready(runState(RunStatus.RUNNING)))
        rule.onNodeWithTag("run_play_pause").performClick()
        rule.onNodeWithTag("run_skip_next").performClick()
        rule.onNodeWithTag("run_skip_previous").performClick()
        assertEquals(1, playPause)
        assertEquals(1, next)
        assertEquals(1, prev)
    }

    @Test fun closeWhileRunningAsksForConfirmation() {
        show(ready(runState(RunStatus.RUNNING)))
        rule.onNodeWithTag("run_close").performClick()
        assertEquals(0, closed)
        rule.onNodeWithText("Stop timer?").assertIsDisplayed()
        rule.onNodeWithTag("run_cancel_stop").performClick()
        rule.onNodeWithText("Stop timer?").assertDoesNotExist()
        rule.onNodeWithTag("run_close").performClick()
        rule.onNodeWithTag("run_confirm_stop").performClick()
        assertEquals(1, closed)
    }

    @Test fun closeWhilePausedAsksForConfirmation() {
        show(ready(runState(RunStatus.PAUSED)))
        rule.onNodeWithTag("run_close").performClick()
        rule.onNodeWithText("Stop timer?").assertIsDisplayed()
    }

    @Test fun finishedShowsSummaryRestartAndDone() {
        show(ready(runState(RunStatus.FINISHED), cycle = null, next = null))
        rule.onNodeWithTag("run_finished").assertIsDisplayed()
        rule.onNodeWithTag("run_finished_name").assertTextEquals("Test timer")
        rule.onNodeWithTag("run_finished_total").assertTextEquals("0:14")
        rule.onNodeWithTag("run_restart").performClick()
        rule.onNodeWithTag("run_done").performClick()
        assertEquals(1, restart)
        assertEquals(1, closed)
        rule.onNodeWithTag("run_play_pause").assertDoesNotExist()
    }

    @Test fun longDurationUsesSmallerFont() {
        val timer = workRestTimer().copy(
            blocks = listOf(workRestTimer().blocks[0].copy(segments = listOf(runSeg("Long", 3_700, SegmentType.COOLDOWN)))),
        )
        val engine = TimerEngine(TimerPlan.from(timer)).also { it.start() }
        show(RunUiState.Ready("Long", engine.state.value, null, null, null))
        rule.onNodeWithTag("run_time").assertTextEquals("1:01:40")
        rule.onNodeWithTag("run_type_label").assertTextEquals("COOL-DOWN")
    }

    private fun customState(type: SegmentType, name: String, prep: Int = 0): RunUiState.Ready {
        val base = workRestTimer(prep = prep)
        val timer = if (prep > 0) base else base.copy(
            blocks = listOf(base.blocks[0].copy(segments = listOf(runSeg(name, 30, type)))),
        )
        val engine = TimerEngine(TimerPlan.from(timer)).also { it.start() }
        return RunUiState.Ready("T", engine.state.value, null, null, null)
    }

    @Test fun typeLabelShownWhenDifferentFromName() {
        show(customState(SegmentType.WORK, "Burpees"))
        rule.onNodeWithTag("run_type_label").assertTextEquals("WORK")
        rule.onNodeWithTag("run_segment_name").assertTextEquals("Burpees")
    }

    @Test fun typeLabelComparisonIsCaseInsensitive() {
        show(customState(SegmentType.WORK, "WORK"))
        rule.onNodeWithTag("run_type_label").assertDoesNotExist()
    }

    @Test fun prepStepShowsGetReadyOnlyOnce() {
        show(customState(SegmentType.PREP, "", prep = 3))
        rule.onNodeWithTag("run_segment_name").assertTextEquals("Get ready")
        rule.onNodeWithTag("run_type_label").assertDoesNotExist()
        rule.onNodeWithText("GET READY").assertDoesNotExist()
    }

    @Test fun ringExposesProgressSemantics() {
        val run = runState(RunStatus.RUNNING, tickMs = 1_000)
        show(ready(run))
        rule.onNodeWithTag("run_ring").assertRangeInfoEquals(ProgressBarRangeInfo(run.stepProgress, 0f..1f))
    }

    @Test fun namedBlockLabelIsShown() {
        show(ready(runState(RunStatus.RUNNING), block = "Block 2/3 · Tabata"))
        rule.onNodeWithTag("run_block_label").assertTextContains("Block 2/3 · Tabata")
    }

    @Test @Config(qualifiers = "w800dp-h360dp")
    fun landscapeLayoutShowsEverythingAndStaysInteractive() {
        show(ready(runState(RunStatus.RUNNING, tickMs = 1_000), block = "Block 1/2"))
        rule.onNodeWithTag("run_time").assertIsDisplayed().assertTextEquals("0:04")
        rule.onNodeWithTag("run_segment_name").assertIsDisplayed()
        rule.onNodeWithTag("run_ring").assertIsDisplayed()
        rule.onNodeWithTag("run_next_up").assertIsDisplayed()
        rule.onNodeWithTag("run_play_pause").assertIsDisplayed().performClick()
        assertEquals(1, playPause)
    }

    @Test @Config(qualifiers = "w360dp-h520dp")
    fun shortPortraitUsesCompactControls() {
        show(ready(runState(RunStatus.PAUSED)))
        rule.onNodeWithTag("run_time").assertIsDisplayed()
        rule.onNodeWithTag("run_paused_label").assertIsDisplayed()
        rule.onNodeWithTag("run_play_pause").assertIsDisplayed().performClick()
        rule.onNodeWithTag("run_skip_next").assertIsDisplayed()
        assertEquals(1, playPause)
    }

    @Test fun keepsScreenOnOnlyWhileRunning() {
        var view: View? = null
        var state by mutableStateOf<RunUiState>(ready(runState(RunStatus.RUNNING)))
        rule.setContent {
            view = LocalView.current
            TimoraTheme { RunScreen(state, {}, {}, {}, {}, {}) }
        }
        rule.waitForIdle()
        assertTrue(view!!.keepScreenOn)
        state = ready(runState(RunStatus.PAUSED))
        rule.waitForIdle()
        assertFalse(view!!.keepScreenOn)
    }

    @Test fun segmentTypeLabels() {
        assertEquals("Get ready", SegmentType.PREP.label())
        assertEquals("Cool-down", SegmentType.COOLDOWN.label())
    }

    @Test fun darkThemeContentColorIsDarkOnContainer() {
        rule.setContent {
            TimoraTheme(themeMode = ThemeMode.DARK) {
                RunScreen(
                    state = ready(runState(RunStatus.RUNNING, tickMs = 1_000), block = "Block 1/2"),
                    onTogglePlayPause = {}, onSkipNext = {}, onSkipPrevious = {}, onRestart = {}, onClose = {},
                )
            }
        }
        rule.waitForIdle()
        val expected = segmentColorsFor(SegmentTypeKey.WORK, dark = true).onContainer
        rule.onNodeWithTag("run_time").assert(
            SemanticsMatcher("content color is dark-theme onContainer") {
                it.config.getOrNull(RunContentColorKey) == expected
            },
        )
    }

    @Test fun runContentColorsDeriveFromOnContainer() {
        for (type in SegmentTypeKey.values()) {
            for (dark in listOf(true, false)) {
                val c = runContentColors(type, dark)
                assertEquals(segmentColorsFor(type, dark).onContainer, c.content)
            }
        }
        // Dark-theme content must be light (luminance well above mid), light-theme content dark.
        assertTrue(runContentColors(SegmentTypeKey.WORK, true).content.luminance() > 0.5f)
        assertTrue(runContentColors(SegmentTypeKey.WORK, false).content.luminance() < 0.1f)
    }
}
