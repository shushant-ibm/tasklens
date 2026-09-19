package dev.shushant.tasklens.core

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class TimelineIcon(val symbol: String, val description: String) {
    SUBMITTED("●", "Task Submitted"),
    WAITING("◌", "Waiting on Constraint / Scheduler"),
    STARTED("▶", "Execution Started"),
    ENV_CHANGE("◆", "Environment State Changed"),
    RETRY("↻", "Retry Requested"),
    WARNING("⚠", "Execution Warning"),
    FAILED("✕", "Execution Failed"),
    EXPIRED("⌛", "Task Expired"),
    SUCCESS("✓", "Execution Completed Successfully"),
    CANCELLED("⊘", "Task Cancelled"),
    STOPPED("■", "Execution Stopped by Platform"),
    BREADCRUMB("💬", "Breadcrumb")
}

@Serializable
data class TimelineItem(
    val id: String,
    val timestamp: Instant,
    val icon: TimelineIcon,
    val title: String,
    val description: String,
    val durationMs: Long? = null,
    val attributes: Map<String, String> = emptyMap(),
    val isWarning: Boolean = false,
    val isFailure: Boolean = false
)

@Serializable
data class TaskTimeline(
    val taskId: String,
    val taskName: String?,
    val items: List<TimelineItem>,
    val totalDurationMs: Long?,
    val hasFailures: Boolean,
    val isOngoing: Boolean
)

interface TimelineBuilder {
    fun build(
        task: ScheduledWork,
        events: List<TaskLensEvent>
    ): TaskTimeline
}

class DefaultTimelineBuilder : TimelineBuilder {

    override fun build(task: ScheduledWork, events: List<TaskLensEvent>): TaskTimeline {
        val sortedEvents = events.filter { it.taskId == task.id || it.attributes["correlation_id"] == task.id }
            .sortedWith(compareBy({ it.timestamp }, { it.sequenceNumber }))

        val items = mutableListOf<TimelineItem>()
        var executionStartTime: Instant? = null
        var hasFailures = false

        for (event in sortedEvents) {
            val (icon, title, desc, isWarn, isFail) = mapEventToVisual(event, task)
            if (isFail) hasFailures = true

            var duration: Long? = null
            if (event.type == EventType.TASK_STARTED || event.type == EventType.TASK_LAUNCHED) {
                executionStartTime = event.timestamp
            } else if (executionStartTime != null && (event.type == EventType.TASK_SUCCEEDED ||
                        event.type == EventType.TASK_FAILED ||
                        event.type == EventType.TASK_STOPPED ||
                        event.type == EventType.TASK_EXPIRED)) {
                duration = event.timestamp.toEpochMilliseconds() - executionStartTime.toEpochMilliseconds()
                executionStartTime = null
            }

            items.add(
                TimelineItem(
                    id = event.id,
                    timestamp = event.timestamp,
                    icon = icon,
                    title = title,
                    description = desc,
                    durationMs = duration,
                    attributes = event.attributes,
                    isWarning = isWarn,
                    isFailure = isFail
                )
            )
        }

        val totalDuration = if (items.isNotEmpty()) {
            val first = items.first().timestamp.toEpochMilliseconds()
            val last = items.last().timestamp.toEpochMilliseconds()
            (last - first).coerceAtLeast(0L)
        } else null

        val isOngoing = sortedEvents.isNotEmpty() && sortedEvents.last().type in setOf(
            EventType.TASK_ENQUEUED,
            EventType.TASK_WAITING,
            EventType.TASK_LAUNCHED,
            EventType.TASK_STARTED
        )

        return TaskTimeline(
            taskId = task.id,
            taskName = task.name,
            items = items,
            totalDurationMs = totalDuration,
            hasFailures = hasFailures,
            isOngoing = isOngoing
        )
    }

    private fun mapEventToVisual(
        event: TaskLensEvent,
        task: ScheduledWork
    ): VisualRepresentation {
        return when (event.type) {
            EventType.TASK_SUBMITTED -> VisualRepresentation(
                icon = TimelineIcon.SUBMITTED,
                title = "Task Submitted",
                description = "Enqueued with scheduler: ${task.scheduler.name}",
                isWarning = false,
                isFailure = false
            )
            EventType.TASK_ENQUEUED -> VisualRepresentation(
                icon = TimelineIcon.SUBMITTED,
                title = "Task Enqueued",
                description = "Waiting in scheduler queue for dispatch",
                isWarning = false,
                isFailure = false
            )
            EventType.TASK_WAITING -> VisualRepresentation(
                icon = TimelineIcon.WAITING,
                title = "Waiting on Constraints",
                description = event.attributes["constraint_details"] ?: "Requirements pending satisfaction",
                isWarning = false,
                isFailure = false
            )
            EventType.TASK_ELIGIBLE -> VisualRepresentation(
                icon = TimelineIcon.WAITING,
                title = "Constraints Satisfied",
                description = "Task eligible for execution",
                isWarning = false,
                isFailure = false
            )
            EventType.TASK_LAUNCHED -> VisualRepresentation(
                icon = TimelineIcon.STARTED,
                title = "Scheduler Dispatched Task",
                description = "Process invoked by system scheduler",
                isWarning = false,
                isFailure = false
            )
            EventType.TASK_STARTED -> VisualRepresentation(
                icon = TimelineIcon.STARTED,
                title = "Execution Started",
                description = "Attempt #${event.attributes["attempt_number"] ?: "1"} running",
                isWarning = false,
                isFailure = false
            )
            EventType.TASK_STOPPED -> VisualRepresentation(
                icon = TimelineIcon.STOPPED,
                title = "Execution Stopped",
                description = "Stop reason: ${event.attributes["stop_reason_name"] ?: event.attributes["stop_reason"] ?: "Platform interrupt"}",
                isWarning = true,
                isFailure = false
            )
            EventType.TASK_EXPIRED -> VisualRepresentation(
                icon = TimelineIcon.EXPIRED,
                title = "Task Expired",
                description = "Operating system background execution quota expired",
                isWarning = true,
                isFailure = true
            )
            EventType.TASK_RETRY_REQUESTED -> VisualRepresentation(
                icon = TimelineIcon.RETRY,
                title = "Retry Scheduled",
                description = "Worker requested retry. Next attempt enqueued.",
                isWarning = true,
                isFailure = false
            )
            EventType.TASK_RESCHEDULED -> VisualRepresentation(
                icon = TimelineIcon.RETRY,
                title = "Task Rescheduled",
                description = "Scheduler will re-run task according to backoff policy",
                isWarning = false,
                isFailure = false
            )
            EventType.TASK_SUCCEEDED -> VisualRepresentation(
                icon = TimelineIcon.SUCCESS,
                title = "Completed Successfully",
                description = "Task reported successful completion",
                isWarning = false,
                isFailure = false
            )
            EventType.TASK_FAILED -> VisualRepresentation(
                icon = TimelineIcon.FAILED,
                title = "Execution Failed",
                description = event.attributes["error_message"] ?: "Task reported failure or threw an exception",
                isWarning = false,
                isFailure = true
            )
            EventType.TASK_CANCELLED -> VisualRepresentation(
                icon = TimelineIcon.CANCELLED,
                title = "Task Cancelled",
                description = "Cancelled by application request or work constraint removal",
                isWarning = true,
                isFailure = false
            )
            EventType.TASK_COMPLETION_REPORTED -> VisualRepresentation(
                icon = TimelineIcon.SUCCESS,
                title = "Completion Reported",
                description = "Platform completion callback returned",
                isWarning = false,
                isFailure = false
            )
            EventType.NETWORK_CHANGED -> VisualRepresentation(
                icon = TimelineIcon.ENV_CHANGE,
                title = "Network Changed",
                description = "Connected: ${event.attributes["connected"] ?: "unknown"}, Transport: ${event.attributes["transport"] ?: "unknown"}",
                isWarning = event.attributes["connected"] == "false",
                isFailure = false
            )
            EventType.BATTERY_CHANGED -> VisualRepresentation(
                icon = TimelineIcon.ENV_CHANGE,
                title = "Battery Changed",
                description = "Level: ${event.attributes["level"] ?: "?"}%, Charging: ${event.attributes["charging"] ?: "false"}",
                isWarning = false,
                isFailure = false
            )
            EventType.POWER_MODE_CHANGED -> VisualRepresentation(
                icon = TimelineIcon.ENV_CHANGE,
                title = "Power Mode Changed",
                description = "Power Save / Low Power Mode: ${event.attributes["power_save"] ?: event.attributes["low_power_mode"] ?: "active"}",
                isWarning = true,
                isFailure = false
            )
            EventType.APP_STATE_CHANGED -> VisualRepresentation(
                icon = TimelineIcon.ENV_CHANGE,
                title = "App Lifecycle Changed",
                description = "State: ${event.attributes["state"] ?: "unknown"}",
                isWarning = false,
                isFailure = false
            )
            EventType.PROCESS_STATE_CHANGED -> VisualRepresentation(
                icon = TimelineIcon.ENV_CHANGE,
                title = "Process State Changed",
                description = "Process: ${event.attributes["process"] ?: "unknown"}",
                isWarning = false,
                isFailure = false
            )
            EventType.BACKGROUND_REFRESH_CHANGED -> VisualRepresentation(
                icon = TimelineIcon.ENV_CHANGE,
                title = "Background Refresh Setting Changed",
                description = "Status: ${event.attributes["status"] ?: "unknown"}",
                isWarning = event.attributes["status"] == "DENIED",
                isFailure = false
            )
            EventType.HTTP_REQUEST_STARTED -> VisualRepresentation(
                icon = TimelineIcon.ENV_CHANGE,
                title = "HTTP Request Started",
                description = "${event.attributes["method"] ?: "GET"} ${event.attributes["url"] ?: ""}",
                isWarning = false,
                isFailure = false
            )
            EventType.HTTP_REQUEST_COMPLETED -> VisualRepresentation(
                icon = TimelineIcon.SUCCESS,
                title = "HTTP Request Succeeded",
                description = "Status code: ${event.attributes["status_code"] ?: "200"}",
                isWarning = false,
                isFailure = false
            )
            EventType.HTTP_REQUEST_FAILED -> VisualRepresentation(
                icon = TimelineIcon.WARNING,
                title = "HTTP Request Failed",
                description = event.attributes["error"] ?: "Network request failed during background execution",
                isWarning = true,
                isFailure = false
            )
            EventType.CUSTOM_BREADCRUMB -> VisualRepresentation(
                icon = TimelineIcon.BREADCRUMB,
                title = event.attributes["title"] ?: "Breadcrumb",
                description = event.attributes["message"] ?: "",
                isWarning = false,
                isFailure = false
            )
            else -> VisualRepresentation(
                icon = TimelineIcon.ENV_CHANGE,
                title = event.type.name,
                description = event.attributes.toString(),
                isWarning = false,
                isFailure = false
            )
        }
    }

    private data class VisualRepresentation(
        val icon: TimelineIcon,
        val title: String,
        val description: String,
        val isWarning: Boolean,
        val isFailure: Boolean
    )
}
