package dev.shushant.tasklens.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.TaskLensEvent

/**
 * Renders a vertical timeline of [TaskLensEvent] entries, ordered by sequence number.
 * Each event shows: timestamp, type badge, source label, and optional severity icon.
 */
@Composable
fun EventTimeline(
    events: List<TaskLensEvent>,
    modifier: Modifier = Modifier
) {
    val sorted = events.sortedBy { it.sequenceNumber }
    Column(modifier = modifier) {
        sorted.forEachIndexed { index, event ->
            EventRow(event = event, isLast = index == sorted.lastIndex)
        }
        if (sorted.isEmpty()) {
            Text(
                text = "No events recorded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun EventRow(
    event: TaskLensEvent,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline stem
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Icon(
                imageVector = event.type.icon(),
                contentDescription = null,
                tint = event.severity.color(),
                modifier = Modifier.size(18.dp)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .padding(vertical = 2.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier
                            .width(2.dp)
                            .height(20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = event.type.name
                        .replace('_', ' ')
                        .lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = event.timestamp.toString().take(23),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                text = "src: ${event.source.name.lowercase()} · seq: ${event.sequenceNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            // Attributes (compact)
            if (event.attributes.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                event.attributes.entries.take(4).forEach { (k, v) ->
                    Text(
                        text = "$k=$v",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Icon + colour helpers
// ---------------------------------------------------------------------------

private fun EventType.icon(): ImageVector = when (this) {
    EventType.TASK_SUCCEEDED -> Icons.Filled.CheckCircle
    EventType.TASK_FAILED -> Icons.Filled.Error
    EventType.TASK_CANCELLED -> Icons.Filled.Stop
    EventType.TASK_RETRY_REQUESTED -> Icons.Filled.Loop
    EventType.TASK_EXPIRED -> Icons.Filled.HourglassEmpty
    else -> Icons.Filled.RadioButtonUnchecked
}

private fun EventSeverity.color(): Color = when (this) {
    EventSeverity.INFO -> Color(0xFF3498DB)
    EventSeverity.WARNING -> Color(0xFFF39C12)
    EventSeverity.ERROR -> Color(0xFFE74C3C)
    EventSeverity.CRITICAL -> Color(0xFF8E44AD)
}
