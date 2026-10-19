package org.marshsoft.timora.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.marshsoft.timora.domain.Block
import org.marshsoft.timora.domain.Limits
import org.marshsoft.timora.domain.Segment
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.formatDuration
import org.marshsoft.timora.ui.common.appContainer
import org.marshsoft.timora.ui.common.toKey
import org.marshsoft.timora.ui.theme.CardAccentCount
import org.marshsoft.timora.ui.theme.Spacing
import org.marshsoft.timora.ui.theme.TimoraTheme
import org.marshsoft.timora.ui.theme.timerDuration

@Composable
fun EditorRoute(timerId: Long, onDone: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel: EditorViewModel = viewModel(
        key = "editor_$timerId",
        factory = EditorViewModel.factory(container.timerRepository, timerId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Saved -> onDone()
            }
        }
    }
    EditorScreen(state = state, actions = viewModel, onBack = onDone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    actions: EditorActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (state.dirty) {
            showDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = state.dirty) { showDiscard = true }

    Scaffold(
        modifier = modifier,
        // Insets are applied exactly once, here: safeDrawing = system bars + display cutout + IME,
        // so the keyboard pushes the content up and nothing else pads for it.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.isNew) "New timer" else "Edit timer", style = MaterialTheme.typography.titleLarge)
                        if (!state.loading && !state.notFound) {
                            Text(
                                "Total ${formatDuration(state.totalSeconds)}",
                                style = MaterialTheme.typography.timerDuration,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("editor_total"),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = requestBack, modifier = Modifier.testTag("editor_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.loading && !state.notFound) {
                        TextButton(
                            onClick = actions::save,
                            enabled = !state.saving,
                            modifier = Modifier.testTag("editor_save"),
                        ) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).testTag("editor_loading"),
                )
                state.notFound -> Text(
                    "This timer no longer exists.",
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.lg).testTag("editor_not_found"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> EditorContent(state, actions)
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this timer have not been saved.") },
            confirmButton = {
                TextButton(
                    onClick = { showDiscard = false; onBack() },
                    modifier = Modifier.testTag("discard_confirm"),
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }, modifier = Modifier.testTag("discard_cancel")) {
                    Text("Keep editing")
                }
            },
        )
    }
}

@Composable
private fun EditorContent(state: EditorUiState, actions: EditorActions) {
    val timer = state.timer
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("editor_list"),
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.saveError != null) {
            item(key = "save_error") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().testTag("editor_save_error"),
                ) {
                    Text(state.saveError, Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item(key = "details") {
            DetailsCard(state, actions)
        }
        if (state.noBlocksError) {
            item(key = "no_blocks_error") {
                Text(
                    "Add at least one block.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("editor_no_blocks_error"),
                )
            }
        }
        itemsIndexed(timer.blocks, key = { _, block -> block.id }) { index, block ->
            BlockCard(index, block, timer.blocks.size, state, actions)
        }
        item(key = "add_block") {
            OutlinedButton(
                onClick = actions::addBlock,
                modifier = Modifier.fillMaxWidth().testTag("editor_add_block"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Add block")
            }
        }
    }
}

@Composable
private fun DetailsCard(state: EditorUiState, actions: EditorActions) {
    val timer = state.timer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedTextField(
                value = timer.name,
                onValueChange = actions::updateName,
                label = { Text("Timer name") },
                placeholder = { Text("e.g. Morning HIIT") },
                singleLine = true,
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text("Name can't be blank", Modifier.testTag("editor_name_error")) }
                } else null,
                modifier = Modifier.fillMaxWidth().testTag("editor_name"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Get ready", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.prepError) "Must be 0 to ${Limits.MAX_PREP_SECONDS / 60} min" else "Countdown before start",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.prepError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("editor_prep_hint"),
                    )
                }
                NumberStepper(
                    value = timer.prepSeconds,
                    onChange = actions::updatePrepSeconds,
                    min = 0,
                    max = Limits.MAX_PREP_SECONDS,
                    step = 5,
                    tagPrefix = "editor_prep",
                    what = "get ready time",
                    format = { "${it}s" },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Color", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    repeat(CardAccentCount) { i ->
                        ColorSwatch(i, selected = i == timer.colorIndex, onClick = { actions.updateColorIndex(i) })
                    }
                }
            }
        }
    }
}

/** Black on light swatches, white on dark ones, so the check mark is always visible. */
internal fun checkTint(accent: Color): Color = if (accent.luminance() > 0.4f) Color.Black else Color.White

@Composable
private fun ColorSwatch(index: Int, selected: Boolean, onClick: () -> Unit) {
    val color = TimoraTheme.cardAccent(index)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Color ${index + 1}"; this.selected = selected }
            .testTag("editor_color_$index"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = checkTint(color), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BlockCard(index: Int, block: Block, blockCount: Int, state: EditorUiState, actions: EditorActions) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("block_$index"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Block ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ReorderButtons(
                    tagPrefix = "block",
                    index = index,
                    count = blockCount,
                    what = "block ${index + 1}",
                    onMove = { to -> actions.moveBlock(index, to) },
                    onDelete = { actions.removeBlock(index) },
                )
            }
            OutlinedTextField(
                value = block.name.orEmpty(),
                onValueChange = { actions.updateBlockName(index, it) },
                label = { Text("Block name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("block_name_$index"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cycles", style = MaterialTheme.typography.titleSmall)
                    if (state.cyclesError(index)) {
                        Text(
                            "Use 1 to ${Limits.MAX_CYCLES}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                NumberStepper(
                    value = block.cycles,
                    onChange = { actions.updateCycles(index, it) },
                    min = Limits.MIN_CYCLES,
                    max = Limits.MAX_CYCLES,
                    step = 1,
                    tagPrefix = "cycles_$index",
                    what = "cycles",
                    format = { "$it" },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skip last rest", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Drop the final rest after the last cycle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = block.skipLastRest,
                    onCheckedChange = { actions.setSkipLastRest(index, it) },
                    modifier = Modifier.testTag("skip_rest_$index"),
                )
            }
            if (state.emptyBlockError(index)) {
                Text(
                    "Add at least one segment.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("block_empty_error_$index"),
                )
            }
            block.segments.forEachIndexed { si, segment ->
                androidx.compose.runtime.key(segment.id) {
                    SegmentEditor(index, si, block.segments.size, segment, state, actions)
                }
            }
            TextButton(
                onClick = { actions.addSegment(index) },
                modifier = Modifier.testTag("add_segment_$index"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Add segment")
            }
        }
    }
}

@Composable
private fun SegmentEditor(
    blockIndex: Int,
    segIndex: Int,
    segCount: Int,
    segment: Segment,
    state: EditorUiState,
    actions: EditorActions,
) {
    val colors = TimoraTheme.segmentColors(segment.type.toKey())
    val suffix = "${blockIndex}_$segIndex"
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().testTag("segment_$suffix"),
    ) {
        Column(Modifier.padding(Spacing.sm + Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(colors.accent))
                Spacer(Modifier.width(Spacing.sm))
                Text("Segment ${segIndex + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                ReorderButtons(
                    tagPrefix = "seg",
                    tagSuffix = suffix,
                    index = segIndex,
                    count = segCount,
                    what = "segment ${segIndex + 1}",
                    onMove = { to -> actions.moveSegment(blockIndex, segIndex, to) },
                    onDelete = { actions.removeSegment(blockIndex, segIndex) },
                )
            }
            val nameError = state.segmentNameError(blockIndex, segIndex)
            OutlinedTextField(
                value = segment.name,
                onValueChange = { actions.updateSegmentName(blockIndex, segIndex, it) },
                label = { Text("Name") },
                singleLine = true,
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("Name can't be blank", Modifier.testTag("seg_name_error_$suffix")) }
                } else null,
                modifier = Modifier.fillMaxWidth().testTag("seg_name_$suffix"),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SegmentType.entries.forEach { type ->
                    val tc = TimoraTheme.segmentColors(type.toKey())
                    FilterChip(
                        selected = type == segment.type,
                        onClick = { actions.updateSegmentType(blockIndex, segIndex, type) },
                        label = { Text(EditorViewModel.defaultSegmentName(type)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tc.container,
                            selectedLabelColor = tc.onContainer,
                        ),
                        modifier = Modifier.testTag("seg_type_${suffix}_${type.name}"),
                    )
                }
            }
            val durationError = state.durationError(blockIndex, segIndex)
            DurationEditor(
                totalSeconds = segment.durationSeconds,
                isError = durationError,
                onSet = { actions.updateSegmentDuration(blockIndex, segIndex, it) },
                onAdjust = { actions.adjustSegmentDuration(blockIndex, segIndex, it) },
                tagPrefix = "seg_dur_$suffix",
            )
            if (durationError) {
                Text(
                    "Duration must be between 0:01 and 24:00:00",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("seg_dur_error_$suffix"),
                )
            }
        }
    }
}

/** Up / down / delete icon buttons for a row in a reorderable list. Tags: `<prefix>_up_<suffix>` etc. */
@Composable
private fun ReorderButtons(
    tagPrefix: String,
    index: Int,
    count: Int,
    what: String,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    tagSuffix: String = index.toString(),
) {
    IconButton(
        onClick = { onMove(index - 1) },
        enabled = index > 0,
        modifier = Modifier.testTag("${tagPrefix}_up_$tagSuffix"),
    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move $what up") }
    IconButton(
        onClick = { onMove(index + 1) },
        enabled = index < count - 1,
        modifier = Modifier.testTag("${tagPrefix}_down_$tagSuffix"),
    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move $what down") }
    IconButton(onClick = onDelete, modifier = Modifier.testTag("${tagPrefix}_delete_$tagSuffix")) {
        Icon(Icons.Filled.Delete, contentDescription = "Delete $what")
    }
}

/** Minus / value / plus control. Tags: `<prefix>_dec`, `<prefix>_value`, `<prefix>_inc`. */
@Composable
internal fun NumberStepper(
    value: Int,
    onChange: (Int) -> Unit,
    min: Int,
    max: Int,
    step: Int,
    tagPrefix: String,
    what: String,
    format: (Int) -> String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
            modifier = Modifier.testTag("${tagPrefix}_dec"),
        ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease $what") }
        Text(
            format(value),
            style = MaterialTheme.typography.timerDuration,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 48.dp).testTag("${tagPrefix}_value"),
        )
        IconButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
            modifier = Modifier.testTag("${tagPrefix}_inc"),
        ) { Icon(Icons.Filled.Add, contentDescription = "Increase $what") }
    }
}

/**
 * Minutes / seconds editor with -/+ steppers (5 s). Tags: `<prefix>_dec`, `_min`, `_sec`, `_inc`.
 * [onSet] receives the new total seconds; [onAdjust] a signed delta.
 */
@Composable
internal fun DurationEditor(
    totalSeconds: Int,
    isError: Boolean,
    onSet: (Int) -> Unit,
    onAdjust: (Int) -> Unit,
    tagPrefix: String,
) {
    // Local text state is the source of truth while a field is focused, so typing is never
    // overwritten by the model round-trip. It resyncs from the model when unfocused, after the
    // +/- buttons, and (normalising e.g. 90s -> 1:30) when a field loses focus.
    var minText by remember { mutableStateOf((totalSeconds / 60).toString()) }
    var secText by remember { mutableStateOf((totalSeconds % 60).toString()) }
    var minFocused by remember { mutableStateOf(false) }
    var secFocused by remember { mutableStateOf(false) }
    var forceSync by remember { mutableStateOf(false) }
    LaunchedEffect(totalSeconds, forceSync) {
        if (forceSync || !(minFocused || secFocused)) {
            minText = (totalSeconds / 60).toString()
            secText = (totalSeconds % 60).toString()
            forceSync = false
        }
    }
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
    val numberStyle = MaterialTheme.typography.timerDuration.copy(textAlign = TextAlign.Center)

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { forceSync = true; onAdjust(-DURATION_STEP) },
            modifier = Modifier.testTag("${tagPrefix}_dec"),
        ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease duration") }
        OutlinedTextField(
            value = minText,
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit).take(4)
                minText = digits
                onSet(combine(digits, secText))
            },
            label = { Text("min") },
            singleLine = true,
            isError = isError,
            textStyle = numberStyle,
            keyboardOptions = numberKeyboard,
            modifier = Modifier
                .width(76.dp)
                .onFocusChanged { f ->
                    if (minFocused && !f.isFocused) forceSync = true
                    minFocused = f.isFocused
                }
                .testTag("${tagPrefix}_min"),
        )
        Text(":", style = MaterialTheme.typography.timerDuration, modifier = Modifier.padding(horizontal = Spacing.xs))
        OutlinedTextField(
            value = secText,
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit).take(5)
                secText = digits
                onSet(combine(minText, digits))
            },
            label = { Text("sec") },
            singleLine = true,
            isError = isError,
            textStyle = numberStyle,
            keyboardOptions = numberKeyboard,
            modifier = Modifier
                .width(76.dp)
                .onFocusChanged { f ->
                    if (secFocused && !f.isFocused) forceSync = true
                    secFocused = f.isFocused
                }
                .testTag("${tagPrefix}_sec"),
        )
        IconButton(
            onClick = { forceSync = true; onAdjust(DURATION_STEP) },
            modifier = Modifier.testTag("${tagPrefix}_inc"),
        ) { Icon(Icons.Filled.Add, contentDescription = "Increase duration") }
    }
}

private const val DURATION_STEP = 5

/** Combines minute and second digit strings (blank = 0) into total seconds. */
internal fun combine(minutes: String, seconds: String): Int {
    val m = minutes.toIntOrNull() ?: 0
    val s = seconds.toIntOrNull() ?: 0
    return (m.toLong() * 60 + s).coerceAtMost(Limits.MAX_DURATION_SECONDS.toLong()).toInt()
}
