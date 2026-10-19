package org.marshsoft.timora.ui.run

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.marshsoft.timora.domain.RunStatus
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.formatDuration
import org.marshsoft.timora.domain.formatRemainingMs
import org.marshsoft.timora.ui.common.appContainer
import org.marshsoft.timora.ui.common.toKey
import org.marshsoft.timora.ui.theme.SegmentColors
import org.marshsoft.timora.ui.theme.Spacing
import org.marshsoft.timora.ui.theme.TimoraTheme
import org.marshsoft.timora.ui.theme.timerDisplay

@Composable
fun RunRoute(timerId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: RunViewModel = viewModel(
        key = "run-$timerId",
        factory = RunViewModel.factory(timerId, container, context.applicationContext),
    )
    // A backgrounded process can be frozen/throttled; pause so the timer never fast-forwards on return.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.pause() }
    val state by vm.state.collectAsStateWithLifecycle()
    RunScreen(
        state = state,
        onTogglePlayPause = vm::togglePlayPause,
        onSkipNext = vm::skipNext,
        onSkipPrevious = vm::skipPrevious,
        onRestart = vm::restart,
        onClose = onClose,
    )
}

fun SegmentType.label(): String = when (this) {
    SegmentType.WORK -> "Work"
    SegmentType.REST -> "Rest"
    SegmentType.PREP -> "Get ready"
    SegmentType.COOLDOWN -> "Cool-down"
}

@Composable
fun RunScreen(
    state: RunUiState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    val ready = state as? RunUiState.Ready
    val running = ready?.run?.status == RunStatus.RUNNING
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    val segmentColors = TimoraTheme.segmentColors((ready?.run?.currentStep?.segment?.type ?: SegmentType.PREP).toKey())
    val surface = MaterialTheme.colorScheme.surface
    val target = if (ready != null && ready.run.status != RunStatus.FINISHED) {
        lerp(surface, segmentColors.container, 0.45f)
    } else surface
    val background by animateColorAsState(target, tween(500), label = "runBackground")

    var confirmStop by rememberSaveable { mutableStateOf(false) }
    val needsConfirm = ready != null &&
        (ready.run.status == RunStatus.RUNNING || ready.run.status == RunStatus.PAUSED)
    val requestClose: () -> Unit = {
        if (needsConfirm) {
            confirmStop = true
        } else {
            onClose()
        }
    }
    BackHandler(enabled = needsConfirm) { confirmStop = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .safeDrawingPadding(),
    ) {
        when (state) {
            RunUiState.Loading -> Box(Modifier.fillMaxSize().testTag("run_loading"), Alignment.Center) {
                CircularProgressIndicator()
            }
            RunUiState.NotFound -> NotFoundContent(onClose)
            is RunUiState.Ready -> if (state.run.status == RunStatus.FINISHED) {
                FinishedContent(state, onRestart, onClose)
            } else {
                ActiveContent(
                    state = state,
                    segmentColors = segmentColors,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onClose = requestClose,
                )
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop timer?") },
            text = { Text("Your progress in this run will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmStop = false; onClose() },
                    modifier = Modifier.testTag("run_confirm_stop"),
                ) { Text("Stop") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmStop = false },
                    modifier = Modifier.testTag("run_cancel_stop"),
                ) { Text("Keep going") }
            },
        )
    }
}

@Composable
private fun NotFoundContent(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.lg).testTag("run_not_found"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Timer not found", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(Spacing.sm))
        Text(
            "This timer may have been deleted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(Spacing.lg))
        Button(onClick = onClose, modifier = Modifier.testTag("run_not_found_back")) { Text("Go back") }
    }
}

@Composable
private fun FinishedContent(state: RunUiState.Ready, onRestart: () -> Unit, onClose: () -> Unit) {
    val colors = TimoraTheme.segmentColors(org.marshsoft.timora.ui.theme.SegmentTypeKey.WORK)
    Column(
        Modifier.fillMaxSize().padding(Spacing.lg).testTag("run_finished"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(colors.container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.size(Spacing.lg))
        Text(
            "Well done!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(
            state.timerName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("run_finished_name"),
        )
        Spacer(Modifier.size(Spacing.lg))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatDuration((state.run.totalDurationMs / 1000).toInt()),
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.testTag("run_finished_total"),
                )
            }
        }
        Spacer(Modifier.size(Spacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedButton(onClick = onRestart, modifier = Modifier.heightIn(min = 48.dp).testTag("run_restart")) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Spacer(Modifier.size(Spacing.sm))
                Text("Restart")
            }
            Button(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("run_done")) { Text("Done") }
        }
    }
}

/** Sizes derived from the space available to the run content. */
private data class RunMetrics(val ring: Dp, val play: Dp, val skip: Dp, val landscape: Boolean)

@Composable
private fun ActiveContent(
    state: RunUiState.Ready,
    segmentColors: SegmentColors,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val run = state.run
    val step = run.currentStep
    val paused = run.status == RunStatus.PAUSED
    val accent = segmentColors.accent
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val compact = maxHeight < 600.dp
        val ringMax = if (landscape) {
            minOf(maxWidth * 0.45f, maxHeight - 72.dp, 320.dp)
        } else {
            minOf(maxWidth - 32.dp, maxHeight * 0.42f, 320.dp)
        }
        val ring = maxOf(ringMax, 96.dp)
        val m = RunMetrics(
            ring = ring,
            play = if (compact) 72.dp else 88.dp,
            skip = if (compact) 56.dp else 64.dp,
            landscape = landscape,
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RunHeader(state.timerName, onClose)
            if (landscape) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ProgressRing(state, accent, m.ring)
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Pills(state, paused)
                        SegmentName(step?.segment?.name.orEmpty())
                        Spacer(Modifier.size(Spacing.sm))
                        OverallProgress(state, accent)
                        NextUp(state.nextUpText)
                        Controls(m, accent, segmentColors.onAccent, paused, onTogglePlayPause, onSkipNext, onSkipPrevious)
                    }
                }
            } else {
                Pills(state, paused)
                // Ring and name are grouped and centred together so leftover height is spread above/below the
                // group instead of opening a gap between them.
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProgressRing(state, accent, m.ring)
                    Spacer(Modifier.size(Spacing.md))
                    SegmentName(step?.segment?.name.orEmpty())
                }
                OverallProgress(state, accent)
                Spacer(Modifier.size(Spacing.md))
                NextUp(state.nextUpText)
                Controls(m, accent, segmentColors.onAccent, paused, onTogglePlayPause, onSkipNext, onSkipPrevious)
            }
        }
    }
}

@Composable
private fun RunHeader(timerName: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("run_close")) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }
        Text(
            timerName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).testTag("run_timer_name"),
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun Pills(state: RunUiState.Ready, paused: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        state.blockLabel?.let { LabelPill(it, "run_block_label") }
        state.cycleLabel?.let { LabelPill(it, "run_cycle_label") }
        if (paused) LabelPill("Paused", "run_paused_label", live = true)
    }
}

@Composable
private fun SegmentName(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .testTag("run_segment_name")
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ProgressRing(state: RunUiState.Ready, accent: Color, ring: Dp) {
    val run = state.run
    val step = run.currentStep
    val progress by animateFloatAsState(run.stepProgress, tween(120, easing = LinearEasing), label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val strokeDp = (ring.value * 0.0625f).coerceIn(8f, 20f).dp
    Box(
        Modifier
            .size(ring)
            .testTag("run_ring")
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(run.stepProgress, 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(strokeDp / 2)) {
            val stroke = Stroke(width = strokeDp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            if (progress > 0f) {
                drawArc(accent, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = stroke)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The type label is redundant when the segment is simply named after its type (e.g. prep's
            // "Get ready", or a segment called "Work"), so only show it when it adds information.
            val typeLabel = step?.segment?.type?.label()
            if (step != null && typeLabel != null && !typeLabel.equals(step.segment.name, ignoreCase = true)) {
                Text(
                    typeLabel.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.testTag("run_type_label"),
                )
            }
            val time = formatRemainingMs(run.stepRemainingMs)
            // Font scales with the ring; longer "1:01:40" strings get a smaller factor so they never clip.
            val factor = if (time.length > 5) 0.19f else 0.27f
            val fontSize = (ring.value * factor).coerceAtLeast(32f)
            Text(
                time,
                style = MaterialTheme.typography.timerDisplay.copy(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.1f).sp,
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .testTag("run_time")
                    .semantics { contentDescription = "$time remaining" },
            )
        }
    }
}

@Composable
private fun OverallProgress(state: RunUiState.Ready, accent: Color) {
    val run = state.run
    LinearProgressIndicator(
        progress = { run.progress },
        modifier = Modifier.fillMaxWidth().heightIn(min = 6.dp).clip(CircleShape).testTag("run_overall_progress"),
        color = accent,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    )
    Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Total remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatRemainingMs(run.totalRemainingMs),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.testTag("run_total_remaining"),
        )
    }
}

@Composable
private fun NextUp(next: String?) {
    if (next == null) return
    Spacer(Modifier.size(Spacing.sm))
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().testTag("run_next_up"),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text("Next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(Spacing.md))
            Text(next, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.size(Spacing.sm))
}

@Composable
private fun Controls(
    m: RunMetrics,
    accent: Color,
    onAccent: Color,
    paused: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
) {
    val skipIcon = if (m.skip < 64.dp) 30.dp else 36.dp
    val playIcon = if (m.play < 88.dp) 40.dp else 48.dp
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(m.skip).testTag("run_skip_previous")) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(skipIcon))
        }
        Surface(
            onClick = onTogglePlayPause,
            shape = CircleShape,
            color = accent,
            contentColor = onAccent,
            modifier = Modifier.size(m.play).testTag("run_play_pause"),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (paused) "Resume" else "Pause",
                    modifier = Modifier.size(playIcon),
                )
            }
        }
        IconButton(onClick = onSkipNext, modifier = Modifier.size(m.skip).testTag("run_skip_next")) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(skipIcon))
        }
    }
}

@Composable
private fun LabelPill(text: String, tag: String, live: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .testTag(tag)
                .semantics { if (live) liveRegion = LiveRegionMode.Polite },
        )
    }
}
