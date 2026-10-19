package org.marshsoft.timora.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.marshsoft.timora.data.AppSettings
import org.marshsoft.timora.domain.RunStatus
import org.marshsoft.timora.domain.Timer

@OptIn(ExperimentalCoroutinesApi::class)
class RunViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val cues = FakeCuePlayer()
    private val settings = FakeSettingsRepository()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(
        timer: Timer? = workRestTimer(),
        repo: FakeTimerRepository? = null,
        clock: () -> Long = { dispatcher.scheduler.currentTime },
    ) = RunViewModel(
        timerId = 1L,
        repository = repo ?: FakeTimerRepository(listOfNotNull(timer)),
        settingsRepository = settings,
        cuePlayer = cues,
        timeSource = { clock() },
        dispatcher = dispatcher,
    )

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun RunViewModel.ready() = state.value as RunUiState.Ready

    @Test fun startsInLoadingThenAutoStarts() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(RunUiState.Loading, vm.state.value)
        runCurrent()
        val s = vm.ready()
        assertEquals(RunStatus.RUNNING, s.run.status)
        assertEquals("Test timer", s.timerName)
        assertEquals(5_000L, s.run.stepRemainingMs)
        assertEquals("Cycle 1/2", s.cycleLabel)
        assertNull(s.blockLabel)
        assertEquals("Rest · 0:02", s.nextUpText)
        assertEquals(1, cues.count("transition"))
        assertEquals(1, cues.count("vibrate:TRANSITION"))
    }

    @Test fun actionsBeforeLoadAreNoOps() = runTest(dispatcher) {
        val vm = vm()
        vm.togglePlayPause(); vm.skipNext(); vm.skipPrevious(); vm.restart()
        runCurrent()
        assertEquals(RunStatus.RUNNING, vm.ready().run.status)
    }

    @Test fun ticksProgressWithVirtualTime() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        advance(2_000)
        assertEquals(3_000L, vm.ready().run.stepRemainingMs)
        advance(1_000)
        assertEquals(2_000L, vm.ready().run.stepRemainingMs)
    }

    @Test fun countdownAndTransitionCues() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        advance(4_900)
        assertEquals(3, cues.count("countdown"))
        assertEquals(3, cues.count("vibrate:COUNTDOWN"))
        assertEquals(1, cues.count("transition"))
        advance(100)
        assertEquals(2, cues.count("transition"))
        assertEquals("Rest", vm.ready().run.currentStep?.segment?.name)
    }

    @Test fun pauseStopsTimeAndResumeContinues() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        advance(1_000)
        vm.togglePlayPause()
        runCurrent()
        assertEquals(RunStatus.PAUSED, vm.ready().run.status)
        assertEquals(4_000L, vm.ready().run.stepRemainingMs)
        advance(10_000)
        assertEquals(4_000L, vm.ready().run.stepRemainingMs)
        vm.togglePlayPause()
        runCurrent()
        assertEquals(RunStatus.RUNNING, vm.ready().run.status)
        advance(1_000)
        assertEquals(3_000L, vm.ready().run.stepRemainingMs)
    }

    @Test fun skipNextAndPrevious() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        vm.skipNext(); runCurrent()
        assertEquals("Rest", vm.ready().run.currentStep?.segment?.name)
        vm.skipPrevious(); runCurrent()
        assertEquals("Work", vm.ready().run.currentStep?.segment?.name)
    }

    @Test fun skippingPastLastStepFinishes() = runTest(dispatcher) {
        val vm = vm(workRestTimer(cycles = 1))
        runCurrent()
        vm.skipNext(); runCurrent()
        vm.skipNext(); runCurrent()
        assertEquals(RunStatus.FINISHED, vm.ready().run.status)
        assertEquals(1, cues.count("finish"))
        assertNull(vm.ready().nextUpText)
    }

    @Test fun finishesAndFiresFinishCueThenRestarts() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        advance(14_000)
        assertEquals(RunStatus.FINISHED, vm.ready().run.status)
        assertEquals(1, cues.count("finish"))
        assertEquals(1, cues.count("vibrate:FINISH"))
        advance(5_000)
        assertEquals(1, cues.count("finish"))
        vm.togglePlayPause()
        runCurrent()
        assertEquals(RunStatus.RUNNING, vm.ready().run.status)
        assertEquals(5_000L, vm.ready().run.stepRemainingMs)
        advance(1_000)
        assertEquals(4_000L, vm.ready().run.stepRemainingMs)
    }

    @Test fun restartMidRunResetsProgress() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        advance(3_000)
        vm.restart()
        runCurrent()
        assertEquals(5_000L, vm.ready().run.stepRemainingMs)
        assertEquals(RunStatus.RUNNING, vm.ready().run.status)
    }

    @Test fun soundDisabledSuppressesBeepsButNotVibration() = runTest(dispatcher) {
        settings.flow.value = AppSettings(soundEnabled = false)
        val vm = vm()
        runCurrent()
        advance(14_000)
        assertEquals(0, cues.log.count { !it.startsWith("vibrate") })
        assertTrue(cues.count("vibrate:FINISH") == 1)
        assertEquals(RunStatus.FINISHED, vm.ready().run.status)
    }

    @Test fun vibrationDisabledSuppressesVibration() = runTest(dispatcher) {
        settings.flow.value = AppSettings(vibrationEnabled = false)
        vm()
        runCurrent()
        advance(14_000)
        assertEquals(0, cues.log.count { it.startsWith("vibrate") })
        assertEquals(1, cues.count("finish"))
    }

    @Test fun settingsChangesApplyLive() = runTest(dispatcher) {
        vm()
        runCurrent()
        settings.flow.value = AppSettings(soundEnabled = false, vibrationEnabled = false)
        runCurrent()
        cues.log.clear()
        advance(14_000)
        assertTrue(cues.log.isEmpty())
    }

    @Test fun prepStepHasNoBlockOrCycleLabels() = runTest(dispatcher) {
        val vm = vm(workRestTimer(prep = 3, blocks = 2))
        runCurrent()
        assertTrue(vm.ready().run.currentStep!!.isPrep)
        assertNull(vm.ready().blockLabel)
        assertNull(vm.ready().cycleLabel)
        vm.skipNext(); runCurrent()
        assertEquals("Block 1/2", vm.ready().blockLabel)
        assertEquals("Cycle 1/2", vm.ready().cycleLabel)
    }

    @Test fun singleCycleSingleBlockOmitsLabels() = runTest(dispatcher) {
        val vm = vm(workRestTimer(cycles = 1))
        runCurrent()
        assertNull(vm.ready().blockLabel)
        assertNull(vm.ready().cycleLabel)
    }

    @Test fun missingTimerIsNotFound() = runTest(dispatcher) {
        val vm = vm(timer = null)
        runCurrent()
        assertEquals(RunUiState.NotFound, vm.state.value)
    }

    @Test fun repositoryErrorIsNotFound() = runTest(dispatcher) {
        val vm = vm(repo = FakeTimerRepository(listOf(workRestTimer()), failOnGet = true))
        runCurrent()
        assertEquals(RunUiState.NotFound, vm.state.value)
    }

    @Test fun clearingReleasesCuePlayerAndStopsTicking() = runTest(dispatcher) {
        val vm = vm()
        val store = ViewModelStore()
        val provider = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        })
        provider[RunViewModel::class.java]
        runCurrent()
        store.clear()
        assertTrue(cues.released)
    }

    @Test fun pauseIsNoOpUnlessRunning() = runTest(dispatcher) {
        val vm = vm()
        vm.pause() // before load
        runCurrent()
        advance(1_000)
        vm.pause()
        runCurrent()
        assertEquals(RunStatus.PAUSED, vm.ready().run.status)
        assertEquals(4_000L, vm.ready().run.stepRemainingMs)
        vm.pause() // already paused
        advance(5_000)
        assertEquals(RunStatus.PAUSED, vm.ready().run.status)
        assertEquals(4_000L, vm.ready().run.stepRemainingMs)
    }

    @Test fun pauseWhenFinishedIsNoOp() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        advance(14_000)
        vm.pause()
        assertEquals(RunStatus.FINISHED, vm.ready().run.status)
    }

    @Test fun hugeTickDeltaIsCapped() = runTest(dispatcher) {
        var now = 0L
        val vm = vm(clock = { now + dispatcher.scheduler.currentTime })
        runCurrent()
        now += 5_000 // simulated frozen process: clock jumps without ticks
        advance(100)
        assertEquals(RunStatus.RUNNING, vm.ready().run.status)
        assertEquals(4_900L, vm.ready().run.stepRemainingMs)
        assertEquals("Work", vm.ready().run.currentStep?.segment?.name)
    }

    @Test fun skipPreviousWithPendingTickGoesBackOneStep() = runTest(dispatcher) {
        var now = 0L
        val vm = vm(clock = { now + dispatcher.scheduler.currentTime })
        runCurrent()
        vm.skipNext(); runCurrent() // now on Rest (2s), nothing elapsed
        now += 900 // pending, not yet ticked
        vm.skipPrevious(); runCurrent()
        // Flush made the elapsed 900ms (<= 3s threshold) so it goes back one step to Work.
        assertEquals("Work", vm.ready().run.currentStep?.segment?.name)
    }

    @Test fun skipFlushThatFinishesRunDoesNotSkipAgain() = runTest(dispatcher) {
        var now = 0L
        val vm = vm(workRestTimer(cycles = 1).let { it.copy(blocks = listOf(it.blocks[0].copy(segments = listOf(runSeg("Solo", 1))))) }, clock = { now + dispatcher.scheduler.currentTime })
        runCurrent()
        now += 1_000
        vm.skipNext(); runCurrent()
        assertEquals(RunStatus.FINISHED, vm.ready().run.status)
        assertEquals(1, cues.count("finish"))
        assertEquals(1_000L, vm.ready().run.totalDurationMs)
    }

    @Test fun skipNextFlushesElapsedTimeIntoProgress() = runTest(dispatcher) {
        var now = 0L
        val vm = vm(clock = { now + dispatcher.scheduler.currentTime })
        runCurrent()
        now += 500
        vm.skipNext(); runCurrent()
        assertEquals("Rest", vm.ready().run.currentStep?.segment?.name)
        assertEquals(2_000L, vm.ready().run.stepRemainingMs)
    }

    @Test fun blockLabelIncludesBlockName() = runTest(dispatcher) {
        val base = workRestTimer(blocks = 2)
        val named = base.copy(blocks = base.blocks.mapIndexed { i, b -> b.copy(name = if (i == 0) "Tabata" else null) })
        val vm = vm(named)
        runCurrent()
        assertEquals("Block 1/2 · Tabata", vm.ready().blockLabel)
    }

    @Test fun singleNamedBlockShowsJustTheName() = runTest(dispatcher) {
        val base = workRestTimer(blocks = 1)
        val vm = vm(base.copy(blocks = base.blocks.map { it.copy(name = "Tabata") }))
        runCurrent()
        assertEquals("Tabata", vm.ready().blockLabel)
    }
}
