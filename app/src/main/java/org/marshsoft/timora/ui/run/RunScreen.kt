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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.marshsoft.timora.domain.RunStatus
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.formatDuration
import org.marshsoft.timora.domain.formatRemainingMs
import org.marshsoft.timora.ui.common.appContainer
import org.marshsoft.timora.ui.common.toKey
import org.marshsoft.timora.ui.theme.Spacing
import org.marshsoft.timora.ui.theme.TimoraTheme
import org.marshsoft.timora.ui.theme.timerDisplay

@Composable
fun RunRoute(timerId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val cuePlayer = remember { AndroidCuePlayer(context) }
    val vm: RunViewModel = viewModel(
        key = "run-$timerId",
        factory = RunViewModel.factory(timerId, container, cuePlayer),
    )
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
            .statusBarsPadding()
            .navigationBarsPadding(),
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
                    accent = segmentColors.accent,
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
        Text("Well done!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
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

@Composable
private fun ActiveContent(
    state: RunUiState.Ready,
    accent: Color,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val run = state.run
    val step = run.currentStep
    val paused = run.status == RunStatus.PAUSED
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("run_close")) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
            Text(
                state.timerName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("run_timer_name"),
            )
            Spacer(Modifier.size(48.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.blockLabel?.let { LabelPill(it, "run_block_label") }
            state.cycleLabel?.let { LabelPill(it, "run_cycle_label") }
            if (paused) LabelPill("Paused", "run_paused_label")
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.sm), contentAlignment = Alignment.Center) {
            val progress by animateFloatAsState(run.stepProgress, tween(120, easing = LinearEasing), label = "ring")
            val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            Box(
                Modifier
                    .widthIn(max = 320.dp)
                    .heightIn(max = 320.dp)
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val stroke = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
                    if (progress > 0f) {
                        drawArc(accent, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = stroke)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    step?.let {
                        Text(
                            it.segment.type.label().uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("run_type_label"),
                        )
                    }
                    val time = formatRemainingMs(run.stepRemainingMs)
                    Text(
                        time,
                        style = MaterialTheme.typography.timerDisplay.let {
                            if (time.length > 5) it.copy(fontSize = 60.sp, lineHeight = 68.sp) else it
                        },
                        maxLines = 1,
                        modifier = Modifier
                            .testTag("run_time")
                            .semantics { contentDescription = "$time remaining" },
                    )
                }
            }
        }

        Text(
            step?.segment?.name.orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("run_segment_name"),
        )
        Spacer(Modifier.size(Spacing.md))

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
        Spacer(Modifier.size(Spacing.md))

        state.nextUpText?.let { next ->
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
            Spacer(Modifier.size(Spacing.md))
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(64.dp).testTag("run_skip_previous")) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
            }
            Surface(
                onClick = onTogglePlayPause,
                shape = CircleShape,
                color = accent,
                contentColor = Color.White,
                modifier = Modifier.size(88.dp).testTag("run_play_pause"),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (paused) "Resume" else "Pause",
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            IconButton(onClick = onSkipNext, modifier = Modifier.size(64.dp).testTag("run_skip_next")) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun LabelPill(text: String, tag: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs).testTag(tag),
        )
    }
}
