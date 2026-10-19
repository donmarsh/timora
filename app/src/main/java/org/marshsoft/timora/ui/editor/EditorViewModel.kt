package org.marshsoft.timora.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.marshsoft.timora.data.InvalidTimerException
import org.marshsoft.timora.data.TimerRepository
import org.marshsoft.timora.domain.Block
import org.marshsoft.timora.domain.Limits
import org.marshsoft.timora.domain.Segment
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.Timer
import org.marshsoft.timora.domain.ValidationError
import org.marshsoft.timora.domain.TimerPlan
import org.marshsoft.timora.domain.validate

data class EditorUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    /** The draft being edited. New blocks/segments carry temporary negative ids. */
    val timer: Timer = Timer(name = "", blocks = emptyList()),
    val initial: Timer = timer,
    /** True once the user pressed save; from then on [errors] is kept live. */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
) {
    val dirty: Boolean get() = timer != initial
    val errors: List<ValidationError> get() = if (showErrors) timer.validate() else emptyList()
    val totalSeconds: Int get() = TimerPlan.from(timer).totalDurationSeconds

    val nameError: Boolean get() = ValidationError.BlankName in errors
    val noBlocksError: Boolean get() = ValidationError.NoBlocks in errors
    val prepError: Boolean get() = errors.any { it is ValidationError.InvalidPrep }
    fun emptyBlockError(blockIndex: Int): Boolean = ValidationError.EmptyBlock(blockIndex) in errors
    fun cyclesError(blockIndex: Int): Boolean = errors.any { it is ValidationError.InvalidCycles && it.blockIndex == blockIndex }
    fun durationError(blockIndex: Int, segmentIndex: Int): Boolean =
        errors.any { it is ValidationError.InvalidDuration && it.blockIndex == blockIndex && it.segmentIndex == segmentIndex }
    fun segmentNameError(blockIndex: Int, segmentIndex: Int): Boolean =
        ValidationError.BlankSegmentName(blockIndex, segmentIndex) in errors
}

sealed interface EditorEvent {
    data class Saved(val id: Long) : EditorEvent
}

/** Everything the editor UI can ask for; implemented by [EditorViewModel]. */
interface EditorActions {
    fun updateName(name: String)
    fun updatePrepSeconds(seconds: Int)
    fun updateColorIndex(index: Int)
    fun addBlock()
    fun removeBlock(index: Int)
    fun moveBlock(from: Int, to: Int)
    fun updateBlockName(blockIndex: Int, name: String)
    fun updateCycles(blockIndex: Int, cycles: Int)
    fun setSkipLastRest(blockIndex: Int, skip: Boolean)
    fun addSegment(blockIndex: Int)
    fun removeSegment(blockIndex: Int, segmentIndex: Int)
    fun moveSegment(blockIndex: Int, from: Int, to: Int)
    fun updateSegmentName(blockIndex: Int, segmentIndex: Int, name: String)
    fun updateSegmentType(blockIndex: Int, segmentIndex: Int, type: SegmentType)
    fun updateSegmentDuration(blockIndex: Int, segmentIndex: Int, seconds: Int)
    fun adjustSegmentDuration(blockIndex: Int, segmentIndex: Int, deltaSeconds: Int)
    fun save()
}

class EditorViewModel(
    private val repository: TimerRepository,
    private val timerId: Long,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel(), EditorActions {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = eventChannel.receiveAsFlow()

    private var nextTempId = -1L

    init {
        if (!restore()) load()
        // Mirror the draft into the SavedStateHandle so it survives process death.
        viewModelScope.launch { _state.collect { persist(it) } }
    }

    private fun persist(s: EditorUiState) {
        if (s.loading || s.notFound) return
        savedState[KEY_DRAFT] = EditorDraftCodec.encode(s.timer)
        savedState[KEY_INITIAL] = EditorDraftCodec.encode(s.initial)
        savedState[KEY_NEXT_ID] = nextTempId
        savedState[KEY_IS_NEW] = s.isNew
        savedState[KEY_SHOW_ERRORS] = s.showErrors
        savedState[KEY_LOADED] = true
    }

    /** Restores a draft saved before process death; returns false if there is none (or it is corrupt). */
    private fun restore(): Boolean {
        if (savedState.get<Boolean>(KEY_LOADED) != true) return false
        val draft = EditorDraftCodec.decode(savedState.get<String>(KEY_DRAFT)) ?: return false
        val initial = EditorDraftCodec.decode(savedState.get<String>(KEY_INITIAL)) ?: draft
        nextTempId = savedState.get<Long>(KEY_NEXT_ID) ?: -1L
        _state.value = EditorUiState(
            loading = false,
            isNew = savedState.get<Boolean>(KEY_IS_NEW) ?: (timerId <= 0L),
            timer = draft,
            initial = initial,
            showErrors = savedState.get<Boolean>(KEY_SHOW_ERRORS) ?: false,
        )
        return true
    }

    private fun tempId(): Long = nextTempId--

    private fun load() {
        viewModelScope.launch {
            if (timerId <= 0L) {
                val draft = defaultTimer()
                _state.value = EditorUiState(loading = false, isNew = true, timer = draft, initial = draft)
                return@launch
            }
            val existing = try {
                repository.get(timerId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (existing == null) {
                _state.update { it.copy(loading = false, notFound = true, isNew = false) }
            } else {
                val draft = existing
                _state.value = EditorUiState(loading = false, isNew = false, timer = draft, initial = draft)
            }
        }
    }

    private fun defaultTimer(): Timer = Timer(
        name = "",
        prepSeconds = 0,
        colorIndex = 0,
        blocks = listOf(
            Block(
                id = tempId(),
                cycles = 5,
                segments = listOf(
                    Segment(tempId(), "Work", 30, SegmentType.WORK),
                    Segment(tempId(), "Rest", 15, SegmentType.REST),
                ),
            ),
        ),
    )

    // ---- pure-ish state transforms -------------------------------------------------------------

    private fun edit(transform: (Timer) -> Timer) {
        _state.update { it.copy(timer = transform(it.timer), saveError = null) }
    }

    private fun editBlock(blockIndex: Int, transform: (Block) -> Block) = edit { t ->
        if (blockIndex !in t.blocks.indices) t
        else t.copy(blocks = t.blocks.mapIndexed { i, b -> if (i == blockIndex) transform(b) else b })
    }

    override fun updateName(name: String) = edit { it.copy(name = name) }

    override fun updatePrepSeconds(seconds: Int) = edit { it.copy(prepSeconds = seconds.coerceIn(0, Limits.MAX_PREP_SECONDS)) }

    override fun updateColorIndex(index: Int) = edit { it.copy(colorIndex = index) }

    override fun addBlock() = edit { t ->
        val block = Block(
            id = tempId(),
            cycles = 1,
            segments = listOf(Segment(tempId(), "Work", 30, SegmentType.WORK)),
        )
        t.copy(blocks = t.blocks + block)
    }

    override fun removeBlock(index: Int) = edit { t ->
        if (index !in t.blocks.indices) t else t.copy(blocks = t.blocks.filterIndexed { i, _ -> i != index })
    }

    override fun moveBlock(from: Int, to: Int) = edit { t -> t.copy(blocks = t.blocks.moved(from, to)) }

    override fun updateBlockName(blockIndex: Int, name: String) =
        editBlock(blockIndex) { it.copy(name = name.ifEmpty { null }) }

    override fun updateCycles(blockIndex: Int, cycles: Int) =
        editBlock(blockIndex) { it.copy(cycles = cycles.coerceIn(Limits.MIN_CYCLES, Limits.MAX_CYCLES)) }

    override fun setSkipLastRest(blockIndex: Int, skip: Boolean) = editBlock(blockIndex) { it.copy(skipLastRest = skip) }

    override fun addSegment(blockIndex: Int) = editBlock(blockIndex) { b ->
        val type = if (b.segments.lastOrNull()?.type == SegmentType.WORK) SegmentType.REST else SegmentType.WORK
        val seg = Segment(tempId(), defaultSegmentName(type), if (type == SegmentType.REST) 15 else 30, type)
        b.copy(segments = b.segments + seg)
    }

    override fun removeSegment(blockIndex: Int, segmentIndex: Int) = editBlock(blockIndex) { b ->
        if (segmentIndex !in b.segments.indices) b
        else b.copy(segments = b.segments.filterIndexed { i, _ -> i != segmentIndex })
    }

    override fun moveSegment(blockIndex: Int, from: Int, to: Int) =
        editBlock(blockIndex) { b -> b.copy(segments = b.segments.moved(from, to)) }

    fun updateSegment(blockIndex: Int, segmentIndex: Int, transform: (Segment) -> Segment) =
        editBlock(blockIndex) { b ->
            if (segmentIndex !in b.segments.indices) b
            else b.copy(segments = b.segments.mapIndexed { i, s -> if (i == segmentIndex) transform(s) else s })
        }

    override fun updateSegmentName(blockIndex: Int, segmentIndex: Int, name: String) =
        updateSegment(blockIndex, segmentIndex) { it.copy(name = name) }

    /** Changes the type; if the name was still the old type's default label it follows the new type. */
    override fun updateSegmentType(blockIndex: Int, segmentIndex: Int, type: SegmentType) =
        updateSegment(blockIndex, segmentIndex) { s ->
            val name = if (s.name == defaultSegmentName(s.type)) defaultSegmentName(type) else s.name
            s.copy(type = type, name = name)
        }

    /** Sets the duration, clamped to 0..MAX (0 is representable so validation can flag it). */
    override fun updateSegmentDuration(blockIndex: Int, segmentIndex: Int, seconds: Int) =
        updateSegment(blockIndex, segmentIndex) {
            it.copy(durationSeconds = seconds.coerceIn(0, Limits.MAX_DURATION_SECONDS))
        }

    /** Adds [deltaSeconds] (may be negative), staying within the valid 1..MAX range. */
    override fun adjustSegmentDuration(blockIndex: Int, segmentIndex: Int, deltaSeconds: Int) =
        updateSegment(blockIndex, segmentIndex) {
            it.copy(
                durationSeconds = (it.durationSeconds + deltaSeconds)
                    .coerceIn(Limits.MIN_DURATION_SECONDS, Limits.MAX_DURATION_SECONDS),
            )
        }

    // ---- save -------------------------------------------------------------------------------

    override fun save() {
        val current = _state.value
        if (current.loading || current.notFound || current.saving) return
        if (current.timer.validate().isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, showErrors = true, saveError = null) }
        viewModelScope.launch {
            try {
                val id = repository.save(current.timer.withPersistentIds())
                _state.update { it.copy(saving = false, initial = it.timer) }
                eventChannel.send(EditorEvent.Saved(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidTimerException) {
                _state.update { it.copy(saving = false, showErrors = true, saveError = "Please fix the highlighted fields.") }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveError = "Could not save timer. Please try again.") }
            }
        }
    }

    /** Temporary (non-positive) child ids become 0 so the database assigns real ones. */
    private fun Timer.withPersistentIds(): Timer = copy(
        blocks = blocks.map { b ->
            b.copy(
                id = if (b.id > 0) b.id else 0,
                segments = b.segments.map { s -> s.copy(id = if (s.id > 0) s.id else 0) },
            )
        },
    )

    companion object {
        fun defaultSegmentName(type: SegmentType): String = when (type) {
            SegmentType.WORK -> "Work"
            SegmentType.REST -> "Rest"
            SegmentType.PREP -> "Prep"
            SegmentType.COOLDOWN -> "Cooldown"
        }

        internal const val KEY_DRAFT = "editor_draft"
        internal const val KEY_INITIAL = "editor_initial"
        internal const val KEY_NEXT_ID = "editor_next_temp_id"
        internal const val KEY_IS_NEW = "editor_is_new"
        internal const val KEY_SHOW_ERRORS = "editor_show_errors"
        internal const val KEY_LOADED = "editor_loaded"

        fun factory(repository: TimerRepository, timerId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditorViewModel(repository, timerId, createSavedStateHandle()) }
        }
    }
}

/** Moves the element at [from] to position [to]; out-of-range indices return the list unchanged. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val m = toMutableList()
    m.add(to, m.removeAt(from))
    return m
}
