package dev.shushant.tasklens.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.ui.TaskLensUiState

// ---------------------------------------------------------------------------
// Main composable
// ---------------------------------------------------------------------------

/**
 * Detail screen showing the full trace for a single [ScheduledWork] task.
 *
 * Three tabs:
 *  - **Attempts** — each execution attempt with outcome + timing
 *  - **Events** — scrollable [EventTimeline]
 *  - **Diagnoses** — list of [DiagnosisCard] entries
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    state: TaskLensUiState.TaskDetail,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val task = state.task
    val displayName = task.name ?: task.id

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Export trace"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Summary header
            TaskSummaryHeader(task = task, attemptCount = state.attempts.size)

            HorizontalDivider()

            // Tabbed content
            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = listOf(
                "Attempts (${state.attempts.size})",
                "Events (${state.events.size})",
                "Diagnoses (${state.diagnoses.size})"
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> AttemptsTab(attempts = state.attempts)
                1 -> EventsTab(events = state.events)
                2 -> DiagnosesTab(diagnoses = state.diagnoses)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Summary header
// ---------------------------------------------------------------------------

@Composable
private fun TaskSummaryHeader(
    task: ScheduledWork,
    attemptCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetaBadge(
                label = task.type.name.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() },
                background = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            MetaBadge(
                label = task.scheduler.name.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() }
            )
            if (task.periodic) {
                MetaBadge(
                    label = "Periodic",
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    textColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            MetaBadge(label = "$attemptCount attempt${if (attemptCount != 1) "s" else ""}")
        }

        task.platformId?.let { pid ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Platform ID: $pid",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        task.submittedAt?.let { ts ->
            Text(
                text = "Submitted: $ts",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        if (task.metadata.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            task.metadata.entries.take(3).forEach { (k, v) ->
                Text(
                    text = "$k = $v",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab: Attempts
// ---------------------------------------------------------------------------

@Composable
private fun AttemptsTab(attempts: List<ExecutionAttempt>) {
    if (attempts.isEmpty()) {
        EmptyTabMessage("No execution attempts recorded.")
        return
    }

    val sorted = attempts.sortedBy { it.attemptNumber }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(sorted, key = { it.attemptId }) { attempt ->
            AttemptCard(attempt = attempt)
        }
    }
}

@Composable
private fun AttemptCard(attempt: ExecutionAttempt) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Outcome indicator
            OutcomeDot(outcome = attempt.outcome)

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attempt #${attempt.attemptNumber}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    attempt.outcome?.let { outcome ->
                        MetaBadge(
                            label = outcome.name.lowercase().replaceFirstChar { it.uppercase() },
                            background = outcome.badgeColor(),
                            textColor = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                attempt.startedAt?.let { start ->
                    LabeledValue("Started", start.toString().take(23))
                }
                attempt.endedAt?.let { end ->
                    LabeledValue("Ended", end.toString().take(23))
                }
                val startedAt = attempt.startedAt
                val endedAt   = attempt.endedAt
                if (startedAt != null && endedAt != null) {
                    val durationMs = endedAt.toEpochMilliseconds() -
                        startedAt.toEpochMilliseconds()
                    LabeledValue("Duration", "${durationMs} ms")
                }
                attempt.platformReason?.let { reason ->
                    LabeledValue("Stop reason", "[${reason.code}] ${reason.name}")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab: Events
// ---------------------------------------------------------------------------

@Composable
private fun EventsTab(events: List<dev.shushant.tasklens.core.TaskLensEvent>) {
    if (events.isEmpty()) {
        EmptyTabMessage("No events recorded for this task.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            EventTimeline(events = events, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------------------
// Tab: Diagnoses
// ---------------------------------------------------------------------------

@Composable
private fun DiagnosesTab(diagnoses: List<dev.shushant.tasklens.core.Diagnosis>) {
    if (diagnoses.isEmpty()) {
        EmptyTabMessage("No diagnoses available — the engine found no anomalies.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(diagnoses, key = { it.id }) { diagnosis ->
            DiagnosisCard(diagnosis = diagnosis)
        }
    }
}

// ---------------------------------------------------------------------------
// Shared helper composables
// ---------------------------------------------------------------------------

@Composable
private fun EmptyTabMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun MetaBadge(
    label: String,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Box(
        modifier = Modifier
            .background(color = background, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun OutcomeDot(outcome: AttemptOutcome?) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(50))
            .background(color = outcome.dotColor())
    )
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}

// ---------------------------------------------------------------------------
// Colour helpers
// ---------------------------------------------------------------------------

private fun AttemptOutcome?.dotColor(): Color = when (this) {
    AttemptOutcome.SUCCESS -> Color(0xFF2ECC71)
    AttemptOutcome.FAILED -> Color(0xFFE74C3C)
    AttemptOutcome.RETRY -> Color(0xFFF39C12)
    AttemptOutcome.CANCELLED -> Color(0xFF95A5A6)
    AttemptOutcome.STOPPED -> Color(0xFF7F8C8D)
    AttemptOutcome.EXPIRED -> Color(0xFF8E44AD)
    AttemptOutcome.RUNNING -> Color(0xFF3498DB)
    AttemptOutcome.UNKNOWN, null -> Color(0xFFBDC3C7)
}

private fun AttemptOutcome.badgeColor(): Color = when (this) {
    AttemptOutcome.SUCCESS -> Color(0xFF27AE60)
    AttemptOutcome.FAILED -> Color(0xFFC0392B)
    AttemptOutcome.RETRY -> Color(0xFFE67E22)
    AttemptOutcome.CANCELLED -> Color(0xFF7F8C8D)
    AttemptOutcome.STOPPED -> Color(0xFF7F8C8D)
    AttemptOutcome.EXPIRED -> Color(0xFF8E44AD)
    AttemptOutcome.RUNNING -> Color(0xFF2980B9)
    AttemptOutcome.UNKNOWN -> Color(0xFFBDC3C7)
}
