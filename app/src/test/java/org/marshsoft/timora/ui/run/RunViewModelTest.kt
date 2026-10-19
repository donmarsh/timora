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

    private fun vm(timer: Timer? = workRestTimer(), repo: FakeTimerRepository? = null) = RunViewModel(
        timerId = 1L,
        repository = repo ?: FakeTimerRepository(listOfNotNull(timer)),
        settingsRepository = settings,
        cuePlayer = cues,
        timeSource = { dispatcher.scheduler.currentTime },
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
}
