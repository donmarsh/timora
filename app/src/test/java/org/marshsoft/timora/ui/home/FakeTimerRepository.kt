package org.marshsoft.timora.ui.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.marshsoft.timora.data.InvalidTimerException
import org.marshsoft.timora.data.TimerRepository
import org.marshsoft.timora.domain.Block
import org.marshsoft.timora.domain.Segment
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.Timer
import org.marshsoft.timora.domain.validate

/** In-memory [TimerRepository] with switchable failures, used by the home and editor tests. */
class FakeTimerRepository(initial: List<Timer> = emptyList()) : TimerRepository {
    private val items = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    var observeOverride: Flow<List<Timer>>? = null
    var failSave: Throwable? = null
    var failDelete: Throwable? = null
    var failDuplicate: Throwable? = null
    var failGet: Throwable? = null

    /** Every timer passed to a successful [save] call, in order. */
    val saved = mutableListOf<Timer>()

    val current: List<Timer> get() = items.value

    override fun observeAll(): Flow<List<Timer>> = observeOverride ?: items

    override fun observe(id: Long): Flow<Timer?> = items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(id: Long): Timer? {
        failGet?.let { throw it }
        return items.value.firstOrNull { it.id == id }
    }

    override suspend fun save(timer: Timer): Long {
        failSave?.let { throw it }
        val errors = timer.validate()
        if (errors.isNotEmpty()) throw InvalidTimerException(errors)
        saved += timer
        return if (timer.id == 0L) {
            val id = nextId++
            items.value = items.value + timer.copy(id = id)
            id
        } else {
            items.value = items.value.map { if (it.id == timer.id) timer else it }
            timer.id
        }
    }

    override suspend fun delete(id: Long) {
        failDelete?.let { throw it }
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun duplicate(id: Long): Long? {
        failDuplicate?.let { throw it }
        val original = items.value.firstOrNull { it.id == id } ?: return null
        val newId = nextId++
        items.value = items.value + original.copy(id = newId, name = "${original.name} (copy)")
        return newId
    }
}

fun sampleTimer(
    id: Long = 1,
    name: String = "HIIT",
    colorIndex: Int = 0,
    prep: Int = 0,
    cycles: Int = 8,
): Timer = Timer(
    id = id,
    name = name,
    prepSeconds = prep,
    colorIndex = colorIndex,
    blocks = listOf(
        Block(
            id = id * 10,
            cycles = cycles,
            segments = listOf(
                Segment(id * 100, "Work", 40, SegmentType.WORK),
                Segment(id * 100 + 1, "Rest", 20, SegmentType.REST),
            ),
        ),
    ),
)
