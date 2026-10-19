package org.marshsoft.timora.ui.run

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.marshsoft.timora.data.AppSettings
import org.marshsoft.timora.data.SettingsRepository
import org.marshsoft.timora.data.ThemeMode
import org.marshsoft.timora.data.TimerRepository
import org.marshsoft.timora.domain.Block
import org.marshsoft.timora.domain.Segment
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.Timer

class FakeTimerRepository(initial: List<Timer> = emptyList(), var failOnGet: Boolean = false) : TimerRepository {
    private val timers = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<Timer>> = timers.map { it.values.toList() }
    override fun observe(id: Long): Flow<Timer?> = timers.map { it[id] }
    override suspend fun get(id: Long): Timer? {
        if (failOnGet) error("boom")
        return timers.value[id]
    }
    override suspend fun save(timer: Timer): Long {
        timers.value = timers.value + (timer.id to timer)
        return timer.id
    }
    override suspend fun delete(id: Long) { timers.value = timers.value - id }
    override suspend fun duplicate(id: Long): Long? = null
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val flow = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = flow
    override suspend fun setThemeMode(mode: ThemeMode) { flow.value = flow.value.copy(themeMode = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) { flow.value = flow.value.copy(dynamicColor = enabled) }
    override suspend fun setSoundEnabled(enabled: Boolean) { flow.value = flow.value.copy(soundEnabled = enabled) }
    override suspend fun setVibrationEnabled(enabled: Boolean) { flow.value = flow.value.copy(vibrationEnabled = enabled) }
}

class FakeCuePlayer : CuePlayer {
    val log = mutableListOf<String>()
    var released = false
    override fun countdownBeep() { log += "countdown" }
    override fun transitionBeep() { log += "transition" }
    override fun finishBeep() { log += "finish" }
    override fun vibrate(pattern: VibrationKind) { log += "vibrate:${pattern.name}" }
    override fun release() { released = true }
    fun count(entry: String) = log.count { it == entry }
}

fun runSeg(name: String, sec: Int, type: SegmentType = SegmentType.WORK) = Segment(0, name, sec, type)

/** Work 5s + Rest 2s, repeated [cycles] times (single block). Total = 7s * cycles. */
fun workRestTimer(id: Long = 1L, cycles: Int = 2, prep: Int = 0, blocks: Int = 1) = Timer(
    id = id,
    name = "Test timer",
    prepSeconds = prep,
    blocks = List(blocks) {
        Block(
            segments = listOf(runSeg("Work", 5), runSeg("Rest", 2, SegmentType.REST)),
            cycles = cycles,
        )
    },
)
