package dev.shushant.tasklens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.ui.TaskLensUiState

// ---------------------------------------------------------------------------
// Main composable
// ---------------------------------------------------------------------------

/**
 * Dashboard screen — shows the full list of tracked [ScheduledWork] entries
 * with a search bar, scheduler-type filter chips, and a clear-all action.
 *
 * Accepts any [TaskLensUiState] so the nav graph can hand Loading / Error /
 * TaskList to the same composable without an extra branch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskLensDashboard(
    state: TaskLensUiState,
    onTaskClick: (taskId: String) -> Unit,
    onClearAll: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        ClearAllDialog(
            onConfirm = {
                showClearDialog = false
                onClearAll()
            },
            onDismiss = { showClearDialog = false }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TaskLens",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Clear all"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state) {
                is TaskLensUiState.Loading -> LoadingContent()
                is TaskLensUiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = onRefresh
                )
                is TaskLensUiState.TaskList -> TaskListContent(
                    state = state,
                    onTaskClick = onTaskClick,
                    onQueryChange = { /* no-op — filterTasks called via vm externally */ }
                )
                is TaskLensUiState.TaskDetail -> {
                    // Shouldn't reach here — nav graph routes detail to TaskDetailScreen
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Content areas
// ---------------------------------------------------------------------------

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Loading tasks…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⚠\uFE0F $message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListContent(
    state: TaskLensUiState.TaskList,
    onTaskClick: (taskId: String) -> Unit,
    onQueryChange: (String) -> Unit
) {
    var localQuery by remember { mutableStateOf(state.searchQuery) }
    var selectedScheduler by remember { mutableStateOf<SchedulerType?>(null) }

    // Apply search + filter
    val visibleTasks = state.tasks.filter { task ->
        val matchesQuery = localQuery.isBlank() ||
            task.name?.contains(localQuery, ignoreCase = true) == true ||
            task.id.contains(localQuery, ignoreCase = true) ||
            task.platformId?.contains(localQuery, ignoreCase = true) == true
        val matchesScheduler = selectedScheduler == null || task.scheduler == selectedScheduler
        matchesQuery && matchesScheduler
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value = localQuery,
            onValueChange = {
                localQuery = it
                onQueryChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search tasks…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (localQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        localQuery = ""
                        onQueryChange("")
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Scheduler filter chips
        val schedulerTypes = SchedulerType.entries
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedScheduler == null,
                    onClick = { selectedScheduler = null },
                    label = { Text("All") }
                )
            }
            items(schedulerTypes) { type ->
                FilterChip(
                    selected = selectedScheduler == type,
                    onClick = {
                        selectedScheduler = if (selectedScheduler == type) null else type
                    },
                    label = {
                        Text(
                            type.name
                                .replace('_', ' ')
                                .lowercase()
                                .replaceFirstChar { it.uppercase() }
                        )
                    }
                )
            }
        }

        // Empty state
        if (visibleTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.tasks.isEmpty()) "No tasks tracked yet." else "No tasks match your filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleTasks, key = { it.id }) { task ->
                    TaskRow(task = task, onClick = { onTaskClick(task.id) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Task row card
// ---------------------------------------------------------------------------

@Composable
private fun TaskRow(
    task: ScheduledWork,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scheduler type dot
            SchedulerDot(task.scheduler)
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Task name / ID
                Text(
                    text = task.name ?: task.id,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Scheduler badge
                    MiniChip(
                        label = task.scheduler.name
                            .replace('_', ' ')
                            .lowercase()
                            .replaceFirstChar { it.uppercase() }
                    )
                    // Task type badge
                    MiniChip(
                        label = task.type.name
                            .replace('_', ' ')
                            .lowercase()
                            .replaceFirstChar { it.uppercase() },
                        background = MaterialTheme.colorScheme.tertiaryContainer,
                        textColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    if (task.periodic) {
                        MiniChip(
                            label = "Periodic",
                            background = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                task.platformId?.let { pid ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = pid,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Submitted-at timestamp (right-aligned)
            task.submittedAt?.let { ts ->
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ts.toString().take(16).replace("T", "\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Small helper composables
// ---------------------------------------------------------------------------

@Composable
private fun SchedulerDot(scheduler: SchedulerType) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(scheduler.dotColor())
    )
}

@Composable
private fun MiniChip(
    label: String,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Box(
        modifier = Modifier
            .background(
                color = background,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun ClearAllDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear all data?") },
        text = {
            Text(
                "This will permanently delete all tracked task data from the local store. " +
                    "This action cannot be undone."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Clear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------------------
// Colour mapping for scheduler types
// ---------------------------------------------------------------------------

private fun SchedulerType.dotColor(): Color = when (this) {
    SchedulerType.WORK_MANAGER -> Color(0xFF3498DB)
    SchedulerType.JOB_SCHEDULER -> Color(0xFF2ECC71)
    SchedulerType.FOREGROUND_SERVICE -> Color(0xFFE67E22)
    SchedulerType.ALARM_MANAGER -> Color(0xFFE74C3C)
    SchedulerType.BG_TASK_SCHEDULER -> Color(0xFF9B59B6)
    SchedulerType.URL_SESSION -> Color(0xFF1ABC9C)
    SchedulerType.MANUAL -> Color(0xFF95A5A6)
}
