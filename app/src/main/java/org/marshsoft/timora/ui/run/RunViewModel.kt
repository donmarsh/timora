package org.marshsoft.timora.ui.run

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.marshsoft.timora.data.AppContainer
import org.marshsoft.timora.data.AppSettings
import org.marshsoft.timora.data.SettingsRepository
import org.marshsoft.timora.data.TimerRepository
import org.marshsoft.timora.domain.EngineEvent
import org.marshsoft.timora.domain.PlannedStep
import org.marshsoft.timora.domain.RunState
import org.marshsoft.timora.domain.RunStatus
import org.marshsoft.timora.domain.TimerEngine
import org.marshsoft.timora.domain.TimerPlan
import org.marshsoft.timora.domain.formatDuration

/** Monotonic millisecond clock, injectable so tests can use virtual time. */
fun interface TimeSource {
    fun nowMs(): Long

    companion object {
        val Monotonic = TimeSource { SystemClock.elapsedRealtime() }
    }
}

sealed interface RunUiState {
    data object Loading : RunUiState
    data object NotFound : RunUiState
    data class Ready(
        val timerName: String,
        val run: RunState,
        /** "Block 2/3" or "Block 2/3 · Tabata"; just the name for a single named block; null for an unnamed single block or prep. */
        val blockLabel: String?,
        /** "Cycle 3/8"; null for the prep step or a single cycle. */
        val cycleLabel: String?,
        /** "Rest · 0:20"; null on the last step / finished. */
        val nextUpText: String?,
    ) : RunUiState
}

class RunViewModel(
    private val timerId: Long,
    private val repository: TimerRepository,
    private val settingsRepository: SettingsRepository,
    private val cuePlayer: CuePlayer,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _state = MutableStateFlow<RunUiState>(RunUiState.Loading)
    val state: StateFlow<RunUiState> = _state.asStateFlow()

    private var engine: TimerEngine? = null
    private var timerName: String = ""
    private var settings = AppSettings()
    private var tickJob: Job? = null
    private var lastTickMs = 0L

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch(dispatcher) {
            val timer = try {
                repository.get(timerId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (timer == null) {
                _state.value = RunUiState.NotFound
                return@launch
            }
            settings = settingsRepository.settings.first()
            launch { settingsRepository.settings.collect { settings = it } }
            val e = TimerEngine(TimerPlan.from(timer))
            engine = e
            timerName = timer.name
            // Subscribe before start() so the first StepChanged event is not lost.
            launch(start = CoroutineStart.UNDISPATCHED) { e.events.collect { onEvent(it) } }
            launch(start = CoroutineStart.UNDISPATCHED) { e.state.collect { _state.value = buildReady(it) } }
            e.start()
            startTicker()
        }
    }

    private fun onEvent(event: EngineEvent) {
        when (event) {
            is EngineEvent.Countdown -> {
                if (settings.soundEnabled) cuePlayer.countdownBeep()
                if (settings.vibrationEnabled) cuePlayer.vibrate(VibrationKind.COUNTDOWN)
            }
            is EngineEvent.StepChanged -> {
                if (settings.soundEnabled) cuePlayer.transitionBeep()
                if (settings.vibrationEnabled) cuePlayer.vibrate(VibrationKind.TRANSITION)
            }
            EngineEvent.Finished -> {
                if (settings.soundEnabled) cuePlayer.finishBeep()
                if (settings.vibrationEnabled) cuePlayer.vibrate(VibrationKind.FINISH)
            }
        }
    }

    private fun buildReady(run: RunState): RunUiState.Ready {
        val step: PlannedStep? = run.currentStep
        val prep = step?.isPrep ?: true
        return RunUiState.Ready(
            timerName = timerName,
            run = run,
            blockLabel = when {
                prep || step == null -> null
                step.totalBlocks > 1 -> buildString {
                    append("Block ${step.blockIndex + 1}/${step.totalBlocks}")
                    step.blockName?.let { append(" · ").append(it) }
                }
                else -> step.blockName
            },
            cycleLabel = if (!prep && step != null && step.totalCycles > 1) {
                "Cycle ${step.cycleIndex}/${step.totalCycles}"
            } else null,
            nextUpText = run.nextStep?.let { "${it.segment.name} · ${formatDuration(it.segment.durationSeconds)}" },
        )
    }

    private fun startTicker() {
        val e = engine ?: return
        tickJob?.cancel()
        lastTickMs = timeSource.nowMs()
        tickJob = viewModelScope.launch(dispatcher) {
            while (isActive && e.state.value.status == RunStatus.RUNNING) {
                delay(TICK_INTERVAL_MS)
                flushTick(e)
            }
        }
    }

    private fun flushTick(e: TimerEngine) {
        val now = timeSource.nowMs()
        var delta = now - lastTickMs
        // A single delta far above the tick interval means the process was frozen/throttled (e.g. backgrounded
        // or doze). Fast-forwarding the timer by that gap would silently skip segments, so cap it. The run is
        // also paused on ON_STOP; this is a second line of defence.
        if (delta > MAX_TICK_DELTA_MS) delta = TICK_INTERVAL_MS
        e.tick(delta)
        lastTickMs = now
    }

    /** Pauses the run; a no-op unless it is currently running (used when the app goes to background). */
    fun pause() {
        val e = engine ?: return
        if (e.state.value.status != RunStatus.RUNNING) return
        flushTick(e)
        tickJob?.cancel()
        e.pause()
    }

    fun togglePlayPause() {
        val e = engine ?: return
        when (e.state.value.status) {
            RunStatus.RUNNING -> pause()
            RunStatus.PAUSED -> {
                e.resume()
                startTicker()
            }
            RunStatus.IDLE, RunStatus.FINISHED -> restart()
        }
    }

    /**
     * Applies the pending partial tick while running so the skip acts on up-to-date state.
     * Returns false if flushing finished the run (the skip must then not be applied again).
     */
    private fun flushIfRunning(e: TimerEngine): Boolean {
        if (e.state.value.status == RunStatus.RUNNING) {
            flushTick(e)
            return e.state.value.status != RunStatus.FINISHED
        }
        return true
    }

    fun skipNext() {
        val e = engine ?: return
        if (!flushIfRunning(e)) return
        e.skipNext()
    }

    fun skipPrevious() {
        val e = engine ?: return
        if (!flushIfRunning(e)) return
        e.skipPrevious()
    }

    /** Restarts from the beginning (from FINISHED, or mid-run). */
    fun restart() {
        val e = engine ?: return
        tickJob?.cancel()
        e.reset()
        e.start()
        startTicker()
    }

    override fun onCleared() {
        tickJob?.cancel()
        cuePlayer.release()
    }

    companion object {
        const val TICK_INTERVAL_MS = 100L
        const val MAX_TICK_DELTA_MS = 1_000L

        /** The cue player is created here (with the application context) so it is tied to the ViewModel, not composition. */
        fun factory(
            timerId: Long,
            container: AppContainer,
            appContext: Context,
            cuePlayerFactory: (Context) -> CuePlayer = { AndroidCuePlayer(it) },
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RunViewModel(
                        timerId,
                        container.timerRepository,
                        container.settingsRepository,
                        cuePlayerFactory(appContext.applicationContext),
                    )
                }
            }
    }
}
