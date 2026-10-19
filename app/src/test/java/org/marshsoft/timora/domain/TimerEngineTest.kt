package org.marshsoft.timora.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {
    // prep 5, then Work 10 / Rest 4 x2 (skipLastRest) => steps: prep5, W10, R4, W10  => 29s
    private fun engine(prep: Int = 5, skip: Boolean = true, cycles: Int = 2) =
        TimerEngine(TimerPlan.from(timer(listOf(workRest(10, 4, cycles, skip)), prep = prep)))

    @Test fun initialStateIsIdleWithFirstStepPreview() {
        val e = engine()
        val s = e.state.value
        assertEquals(RunStatus.IDLE, s.status)
        assertEquals(0, s.stepIndex)
        assertEquals(4, s.totalSteps)
        assertEquals(5_000, s.stepRemainingMs)
        assertEquals(29_000, s.totalRemainingMs)
        assertEquals(0f, s.progress, 0f)
        assertEquals(e.plan.steps[1], s.nextStep)
    }

    @Test fun tickIgnoredWhenNotRunning() {
        val e = engine()
        e.tick(1000)
        assertEquals(5_000, e.state.value.stepRemainingMs)
        e.start(); e.pause(); e.tick(1000)
        assertEquals(5_000, e.state.value.stepRemainingMs)
    }

    @Test fun nonPositiveTickIgnored() {
        val e = engine(); e.start()
        e.tick(0); e.tick(-5)
        assertEquals(5_000, e.state.value.stepRemainingMs)
    }

    @Test fun startAndTickWithinStep() {
        val e = engine(); e.start()
        assertEquals(RunStatus.RUNNING, e.state.value.status)
        e.tick(2_000)
        val s = e.state.value
        assertEquals(3_000, s.stepRemainingMs)
        assertEquals(27_000, s.totalRemainingMs)
        assertEquals(2f / 29f, s.progress, 1e-5f)
        assertEquals(0.4f, s.stepProgress, 1e-5f)
    }

    @Test fun startIsNoOpWhileRunningOrPaused() {
        val e = engine(); e.start(); e.tick(1000); e.start()
        assertEquals(4_000, e.state.value.stepRemainingMs)
        e.pause(); e.start()
        assertEquals(RunStatus.PAUSED, e.state.value.status)
    }

    @Test fun tickCrossesStepBoundaryWithCarryOver() {
        val e = engine(); e.start()
        e.tick(7_000) // 5 prep + 2 into work
        val s = e.state.value
        assertEquals(1, s.stepIndex)
        assertEquals(8_000, s.stepRemainingMs)
        assertEquals(22_000, s.totalRemainingMs)
    }

    @Test fun tickCrossesMultipleStepsAtOnce() {
        val e = engine(); e.start()
        e.tick(5_000 + 10_000 + 1_000)
        assertEquals(2, e.state.value.stepIndex)
        assertEquals(3_000, e.state.value.stepRemainingMs)
    }

    @Test fun exactBoundaryAdvancesToNextStepAtFullDuration() {
        val e = engine(); e.start(); e.tick(5_000)
        assertEquals(1, e.state.value.stepIndex)
        assertEquals(10_000, e.state.value.stepRemainingMs)
    }

    @Test fun finishesAndClampsOvershoot() {
        val e = engine(); e.start()
        e.tick(1_000_000)
        val s = e.state.value
        assertEquals(RunStatus.FINISHED, s.status)
        assertEquals(0, s.stepRemainingMs)
        assertEquals(0, s.totalRemainingMs)
        assertEquals(1f, s.progress, 0f)
        assertNull(s.nextStep)
        assertEquals(3, s.stepIndex)
        e.tick(1000)
        assertEquals(RunStatus.FINISHED, e.state.value.status)
    }

    @Test fun pauseResume() {
        val e = engine(); e.start(); e.tick(1_000)
        e.pause()
        assertEquals(RunStatus.PAUSED, e.state.value.status)
        e.pause() // no-op
        e.tick(5_000)
        assertEquals(4_000, e.state.value.stepRemainingMs)
        e.resume()
        assertEquals(RunStatus.RUNNING, e.state.value.status)
        e.resume() // no-op
        e.tick(1_000)
        assertEquals(3_000, e.state.value.stepRemainingMs)
    }

    @Test fun pauseResumeIgnoredFromIdle() {
        val e = engine(); e.pause(); e.resume()
        assertEquals(RunStatus.IDLE, e.state.value.status)
    }

    @Test fun skipNextMovesAndResetsStepElapsed() {
        val e = engine(); e.start(); e.tick(2_000)
        e.skipNext()
        assertEquals(1, e.state.value.stepIndex)
        assertEquals(10_000, e.state.value.stepRemainingMs)
        assertEquals(RunStatus.RUNNING, e.state.value.status)
    }

    @Test fun skipNextOnLastStepFinishes() {
        val e = engine(); e.start()
        repeat(3) { e.skipNext() }
        assertEquals(3, e.state.value.stepIndex)
        e.skipNext()
        assertEquals(RunStatus.FINISHED, e.state.value.status)
        assertEquals(0, e.state.value.totalRemainingMs)
    }

    @Test fun skipWhilePausedKeepsPaused() {
        val e = engine(); e.start(); e.pause(); e.skipNext()
        assertEquals(RunStatus.PAUSED, e.state.value.status)
        assertEquals(1, e.state.value.stepIndex)
        e.skipPrevious()
        assertEquals(0, e.state.value.stepIndex)
    }

    @Test fun skipPreviousRestartsStepIfPastThreshold() {
        val e = engine(); e.start(); e.skipNext(); e.tick(4_000)
        e.skipPrevious()
        assertEquals(1, e.state.value.stepIndex)
        assertEquals(10_000, e.state.value.stepRemainingMs)
    }

    @Test fun skipPreviousGoesBackIfNearStart() {
        val e = engine(); e.start(); e.skipNext(); e.tick(3_000) // exactly threshold -> go back
        e.skipPrevious()
        assertEquals(0, e.state.value.stepIndex)
        assertEquals(5_000, e.state.value.stepRemainingMs)
    }

    @Test fun skipPreviousAtFirstStepRestartsIt() {
        val e = engine(); e.start(); e.tick(1_000)
        e.skipPrevious()
        assertEquals(0, e.state.value.stepIndex)
        assertEquals(5_000, e.state.value.stepRemainingMs)
        assertEquals(RunStatus.RUNNING, e.state.value.status)
    }

    @Test fun skipsIgnoredWhenIdleOrFinished() {
        val e = engine()
        e.skipNext(); e.skipPrevious()
        assertEquals(RunStatus.IDLE, e.state.value.status)
        assertEquals(0, e.state.value.stepIndex)
        e.start(); e.tick(1_000_000)
        e.skipNext(); e.skipPrevious()
        assertEquals(RunStatus.FINISHED, e.state.value.status)
        assertEquals(3, e.state.value.stepIndex)
    }

    @Test fun resetReturnsToIdle() {
        val e = engine(); e.start(); e.tick(12_000)
        e.reset()
        val s = e.state.value
        assertEquals(RunStatus.IDLE, s.status)
        assertEquals(0, s.stepIndex)
        assertEquals(29_000, s.totalRemainingMs)
        assertEquals(0f, s.progress, 0f)
    }

    @Test fun canRestartAfterFinish() {
        val e = engine(); e.start(); e.tick(1_000_000)
        e.start()
        assertEquals(RunStatus.RUNNING, e.state.value.status)
        assertEquals(0, e.state.value.stepIndex)
        assertEquals(29_000, e.state.value.totalRemainingMs)
    }

    @Test fun noPrepSingleCycleSingleStep() {
        val e = TimerEngine(TimerPlan.from(timer(listOf(Block(segments = listOf(seg("Go", 3)))))))
        assertEquals(1, e.state.value.totalSteps)
        assertNull(e.state.value.nextStep)
        e.start(); e.tick(3_000)
        assertEquals(RunStatus.FINISHED, e.state.value.status)
        assertEquals(1f, e.state.value.progress, 0f)
    }

    @Test fun emptyPlanFinishesOnStart() {
        val e = TimerEngine(TimerPlan(emptyList()))
        assertEquals(0f, e.state.value.progress, 0f)
        assertNull(e.state.value.currentStep)
        e.start()
        assertEquals(RunStatus.FINISHED, e.state.value.status)
        assertEquals(1f, e.state.value.progress, 0f)
    }

    @Test fun skipLastRestReflectedInEngineTotals() {
        val withSkip = engine(prep = 0, skip = true)
        val without = engine(prep = 0, skip = false)
        assertEquals(24_000, withSkip.state.value.totalDurationMs)
        assertEquals(28_000, without.state.value.totalDurationMs)
    }

    @Test fun eventsStepChangedCountdownAndFinished() = runTest {
        val e = TimerEngine(TimerPlan.from(timer(listOf(Block(segments = listOf(seg("A", 5), seg("B", 2)))))))
        val events = mutableListOf<EngineEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { e.events.toList(events) }
        e.start()
        repeat(7) { e.tick(1_000) }
        job.cancel()
        val a = e.plan.steps[0]; val b = e.plan.steps[1]
        assertEquals(
            listOf(
                EngineEvent.StepChanged(a),
                EngineEvent.Countdown(3), EngineEvent.Countdown(2), EngineEvent.Countdown(1),
                EngineEvent.StepChanged(b),
                EngineEvent.Countdown(1),
                EngineEvent.Finished,
            ),
            events,
        )
    }

    @Test fun largeTickEmitsCountdownsForSkippedSeconds() = runTest {
        val e = TimerEngine(TimerPlan.from(timer(listOf(Block(segments = listOf(seg("A", 10), seg("B", 10)))))))
        val events = mutableListOf<EngineEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { e.events.toList(events) }
        e.start()
        e.tick(10_000)
        job.cancel()
        assertEquals(
            listOf(
                EngineEvent.StepChanged(e.plan.steps[0]),
                EngineEvent.Countdown(3), EngineEvent.Countdown(2), EngineEvent.Countdown(1),
                EngineEvent.StepChanged(e.plan.steps[1]),
            ),
            events,
        )
    }

    @Test fun skipEmitsStepChangedAndFinished() = runTest {
        val e = engine(prep = 0, skip = false, cycles = 1)
        val events = mutableListOf<EngineEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { e.events.toList(events) }
        e.start(); e.skipNext(); e.skipNext()
        job.cancel()
        assertEquals(3, events.size)
        assertTrue(events.last() is EngineEvent.Finished)
    }

    @Test fun shortStepsEmitNoCountdownDuplicates() = runTest {
        val e = TimerEngine(TimerPlan.from(timer(listOf(Block(segments = listOf(seg("A", 1)), cycles = 2)))))
        val events = mutableListOf<EngineEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { e.events.toList(events) }
        e.start(); e.tick(2_000)
        job.cancel()
        assertEquals(0, events.count { it is EngineEvent.Countdown && it.secondsLeft != 1 })
    }
}
