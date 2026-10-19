package org.marshsoft.timora.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RunStatus { IDLE, RUNNING, PAUSED, FINISHED }

data class RunState(
    val status: RunStatus = RunStatus.IDLE,
    /** Current step; in FINISHED it is the last step; null only for an empty plan. */
    val currentStep: PlannedStep? = null,
    /** Next step, null on the last step / finished. */
    val nextStep: PlannedStep? = null,
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val stepRemainingMs: Long = 0,
    val stepDurationMs: Long = 0,
    val totalRemainingMs: Long = 0,
    val totalDurationMs: Long = 0,
    /** Overall progress 0..1. */
    val progress: Float = 0f,
    /** Progress within the current step 0..1. */
    val stepProgress: Float = 0f,
)

sealed interface EngineEvent {
    /** Emitted when a new step becomes current (start, natural transition, or skip). */
    data class StepChanged(val step: PlannedStep) : EngineEvent

    /** Emitted when the displayed whole-seconds-left within a step reaches 3, 2 or 1. */
    data class Countdown(val secondsLeft: Int) : EngineEvent

    data object Finished : EngineEvent
}

/**
 * Deterministic state machine over a [TimerPlan]. Time only advances through [tick].
 * Not thread-safe: drive it from a single coroutine/thread.
 */
class TimerEngine(val plan: TimerPlan) {
    private var status = RunStatus.IDLE
    private var index = 0
    private var elapsedInStepMs = 0L

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<RunState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    /** Starts from IDLE (or restarts from FINISHED). No-op while running/paused. */
    fun start() {
        if (status == RunStatus.RUNNING || status == RunStatus.PAUSED) return
        index = 0
        elapsedInStepMs = 0
        if (plan.steps.isEmpty()) {
            status = RunStatus.FINISHED
            _events.tryEmit(EngineEvent.Finished)
        } else {
            status = RunStatus.RUNNING
            _events.tryEmit(EngineEvent.StepChanged(plan.steps[0]))
        }
        publish()
    }

    fun pause() {
        if (status != RunStatus.RUNNING) return
        status = RunStatus.PAUSED
        publish()
    }

    fun resume() {
        if (status != RunStatus.PAUSED) return
        status = RunStatus.RUNNING
        publish()
    }

    fun skipNext() {
        if (status != RunStatus.RUNNING && status != RunStatus.PAUSED) return
        if (index >= plan.steps.lastIndex) finish() else goTo(index + 1)
        publish()
    }

    /** Restarts the current step if more than [RESTART_THRESHOLD_MS] elapsed, otherwise goes back one step. */
    fun skipPrevious() {
        if (status != RunStatus.RUNNING && status != RunStatus.PAUSED) return
        if (elapsedInStepMs > RESTART_THRESHOLD_MS || index == 0) {
            goTo(index)
        } else {
            goTo(index - 1)
        }
        publish()
    }

    fun reset() {
        status = RunStatus.IDLE
        index = 0
        elapsedInStepMs = 0
        publish()
    }

    /** Advances time by [elapsedMs] (ignored unless RUNNING). Crosses multiple steps if needed. */
    fun tick(elapsedMs: Long) {
        if (status != RunStatus.RUNNING || elapsedMs <= 0) return
        var budget = elapsedMs
        while (budget > 0 && status == RunStatus.RUNNING) {
            val step = plan.steps[index]
            val remaining = step.durationMs - elapsedInStepMs
            if (budget >= remaining) {
                budget -= remaining
                if (index >= plan.steps.lastIndex) {
                    emitCountdown(remaining, 0, step)
                    elapsedInStepMs = step.durationMs
                    finish()
                } else {
                    emitCountdown(remaining, 0, step)
                    goTo(index + 1)
                }
            } else {
                val before = remaining
                elapsedInStepMs += budget
                budget = 0
                emitCountdown(before, step.durationMs - elapsedInStepMs, step)
            }
        }
        publish()
    }

    private fun emitCountdown(beforeMs: Long, afterMs: Long, step: PlannedStep) {
        val c0 = ceilSeconds(beforeMs)
        val c1 = ceilSeconds(afterMs)
        // Emit 3..1 when crossing into them; reaching 0 is covered by StepChanged/Finished.
        for (s in (c0 - 1) downTo maxOf(c1, 1)) {
            if (s in 1..3 && step.durationMs > 0) _events.tryEmit(EngineEvent.Countdown(s.toInt()))
        }
    }

    private fun ceilSeconds(ms: Long): Long = (ms + 999) / 1000

    private fun goTo(newIndex: Int) {
        index = newIndex
        elapsedInStepMs = 0
        _events.tryEmit(EngineEvent.StepChanged(plan.steps[newIndex]))
    }

    private fun finish() {
        status = RunStatus.FINISHED
        index = plan.steps.lastIndex.coerceAtLeast(0)
        elapsedInStepMs = plan.steps.getOrNull(index)?.durationMs ?: 0
        _events.tryEmit(EngineEvent.Finished)
    }

    private fun publish() {
        _state.value = compute()
    }

    private fun compute(): RunState {
        val steps = plan.steps
        val step = steps.getOrNull(index)
        val total = plan.totalDurationMs
        val elapsedTotal = (step?.startOffsetMs ?: 0L) + elapsedInStepMs
        val duration = step?.durationMs ?: 0L
        return RunState(
            status = status,
            currentStep = step,
            nextStep = if (status == RunStatus.FINISHED) null else steps.getOrNull(index + 1),
            stepIndex = index,
            totalSteps = steps.size,
            stepRemainingMs = duration - elapsedInStepMs,
            stepDurationMs = duration,
            totalRemainingMs = (total - elapsedTotal).coerceAtLeast(0),
            totalDurationMs = total,
            progress = if (total == 0L) (if (status == RunStatus.FINISHED) 1f else 0f)
            else (elapsedTotal.toFloat() / total).coerceIn(0f, 1f),
            stepProgress = if (duration == 0L) 0f else (elapsedInStepMs.toFloat() / duration).coerceIn(0f, 1f),
        )
    }

    companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
