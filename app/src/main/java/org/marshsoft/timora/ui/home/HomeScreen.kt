package org.marshsoft.timora.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.marshsoft.timora.domain.Timer
import org.marshsoft.timora.domain.formatDuration
import org.marshsoft.timora.domain.totalDurationSeconds
import org.marshsoft.timora.ui.common.appContainer
import org.marshsoft.timora.ui.common.toKey
import org.marshsoft.timora.ui.theme.Spacing
import org.marshsoft.timora.ui.theme.TimoraTheme
import org.marshsoft.timora.ui.theme.timerDuration
import androidx.compose.material3.SnackbarResult

@Composable
fun HomeRoute(
    onCreateTimer: () -> Unit,
    onEditTimer: (Long) -> Unit,
    onRunTimer: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container.timerRepository))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HomeEventsEffect(viewModel.events, snackbarHostState, viewModel::undoDelete)

    HomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onCreateTimer = onCreateTimer,
        onEditTimer = onEditTimer,
        onRunTimer = onRunTimer,
        onOpenSettings = onOpenSettings,
        onDuplicateTimer = viewModel::duplicate,
        onDeleteTimer = viewModel::delete,
    )
}

/** Shows delete/undo and error snackbars for one-shot [HomeEvent]s. */
@Composable
internal fun HomeEventsEffect(
    events: Flow<HomeEvent>,
    snackbarHostState: SnackbarHostState,
    onUndoDelete: (Timer) -> Unit,
) {
    LaunchedEffect(events, snackbarHostState) {
        events.collectLatest { event ->
            when (event) {
                is HomeEvent.TimerDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Deleted \"${event.timer.name}\"",
                        actionLabel = "Undo",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) onUndoDelete(event.timer)
                }
                is HomeEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onCreateTimer: () -> Unit,
    onEditTimer: (Long) -> Unit,
    onRunTimer: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onDuplicateTimer: (Long) -> Unit,
    onDeleteTimer: (Timer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Timora") },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("home_settings")) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateTimer,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New timer") },
                modifier = Modifier.testTag("home_fab"),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                HomeUiState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).testTag("home_loading"),
                )
                is HomeUiState.Error -> Text(
                    state.message,
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.lg).testTag("home_error"),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                is HomeUiState.Content -> if (state.timers.isEmpty()) {
                    EmptyState(onCreateTimer, Modifier.align(Alignment.Center))
                } else {
                    TimerList(
                        timers = state.timers,
                        onEdit = onEditTimer,
                        onRun = onRunTimer,
                        onDuplicate = onDuplicateTimer,
                        onDelete = onDeleteTimer,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerList(
    timers: List<Timer>,
    onEdit: (Long) -> Unit,
    onRun: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Timer) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_list"),
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(timers, key = { it.id }) { timer ->
            TimerCard(
                timer = timer,
                onEdit = { onEdit(timer.id) },
                onRun = { onRun(timer.id) },
                onDuplicate = { onDuplicate(timer.id) },
                onDelete = { onDelete(timer) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerCard(
    timer: Timer,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = TimoraTheme.cardAccent(timer.colorIndex)
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().testTag("timer_card_${timer.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(accent)
                    .testTag("timer_accent_${timer.id}"),
            )
            Column(
                Modifier.weight(1f).padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    timer.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDuration(timer.totalDurationSeconds()),
                    style = MaterialTheme.typography.timerDuration,
                    color = accent,
                    modifier = Modifier.testTag("timer_duration_${timer.id}"),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.testTag("timer_summary_${timer.id}"),
                ) {
                    summarize(timer).forEach { item ->
                        when (item) {
                            is SummaryItem.SegmentChip -> SegmentChip(item)
                            is SummaryItem.Repeat -> Text(
                                item.text,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = Spacing.xs),
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.padding(end = Spacing.xs, top = Spacing.xs, bottom = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("menu_${timer.id}")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${timer.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onEdit() },
                            modifier = Modifier.testTag("menu_edit_${timer.id}"),
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = { menuOpen = false; onDuplicate() },
                            modifier = Modifier.testTag("menu_duplicate_${timer.id}"),
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() },
                            modifier = Modifier.testTag("menu_delete_${timer.id}"),
                        )
                    }
                }
                FilledIconButton(
                    onClick = onRun,
                    modifier = Modifier.size(56.dp).testTag("play_${timer.id}"),
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Run ${timer.name}",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentChip(item: SummaryItem.SegmentChip) {
    val colors = TimoraTheme.segmentColors(item.type.toKey())
    Surface(shape = MaterialTheme.shapes.small, color = colors.container, contentColor = colors.onContainer) {
        Text(
            item.label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.xl).testTag("home_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text("No timers yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Build an interval timer from work, rest and cooldown segments, then run it with one tap.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onCreate, modifier = Modifier.testTag("home_empty_create")) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Create your first timer")
        }
    }
}
