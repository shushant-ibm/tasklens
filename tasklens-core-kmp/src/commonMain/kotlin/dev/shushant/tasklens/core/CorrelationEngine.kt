package dev.shushant.tasklens.core

import kotlin.time.Instant

data class CorrelationResult(
    val matchedTaskId: String?,
    val matchedAttemptId: String?,
    val confidence: CorrelationConfidence,
    val method: CorrelationMethod
)

enum class CorrelationConfidence {
    EXACT,
    HIGH,
    MEDIUM,
    FALLBACK_TIME_WINDOW,
    NONE
}

enum class CorrelationMethod {
    EXPLICIT_CORRELATION_ID,
    PLATFORM_TASK_ID,
    WORKER_IDENTIFIER,
    EXECUTION_CONTEXT,
    TIME_WINDOW_FALLBACK,
    UNMATCHED
}

data class CorrelatedTaskStory(
    val task: ScheduledWork,
    val attempts: List<ExecutionAttempt>,
    val events: List<TaskLensEvent>,
    val environmentChanges: List<TaskLensEvent>,
    val networkTransfers: List<TaskLensEvent>
)

interface CorrelationEngine {
    fun correlate(event: TaskLensEvent): CorrelationResult
    fun buildTaskStory(task: ScheduledWork, events: List<TaskLensEvent>): CorrelatedTaskStory
    fun reconstructAttempts(taskId: String, events: List<TaskLensEvent>): List<ExecutionAttempt>
}

class DefaultCorrelationEngine(
    private val timeWindowToleranceMs: Long = 5000L
) : CorrelationEngine {

    override fun correlate(event: TaskLensEvent): CorrelationResult {
        // 1. Explicit correlation ID in attributes
        val explicitId = event.attributes["correlation_id"] ?: event.attributes["tasklens.correlation_id"]
        if (!explicitId.isNullOrBlank()) {
            return CorrelationResult(
                matchedTaskId = explicitId,
                matchedAttemptId = event.attemptId,
                confidence = CorrelationConfidence.EXACT,
                method = CorrelationMethod.EXPLICIT_CORRELATION_ID
            )
        }

        // 2. Direct Task ID
        if (!event.taskId.isNullOrBlank()) {
            return CorrelationResult(
                matchedTaskId = event.taskId,
                matchedAttemptId = event.attemptId,
                confidence = CorrelationConfidence.EXACT,
                method = CorrelationMethod.PLATFORM_TASK_ID
            )
        }

        // 3. Worker identifier tag
        val workerTag = event.attributes["worker_class"] ?: event.attributes["task_identifier"]
        if (!workerTag.isNullOrBlank()) {
            return CorrelationResult(
                matchedTaskId = workerTag,
                matchedAttemptId = event.attemptId,
                confidence = CorrelationConfidence.HIGH,
                method = CorrelationMethod.WORKER_IDENTIFIER
            )
        }

        return CorrelationResult(
            matchedTaskId = null,
            matchedAttemptId = null,
            confidence = CorrelationConfidence.NONE,
            method = CorrelationMethod.UNMATCHED
        )
    }

    override fun reconstructAttempts(taskId: String, events: List<TaskLensEvent>): List<ExecutionAttempt> {
        val taskEvents = events.filter { it.taskId == taskId }
            .sortedWith(compareBy({ it.timestamp }, { it.sequenceNumber }))

        val attempts = mutableListOf<ExecutionAttempt>()
        var currentAttemptNumber = 0
        var currentAttemptId: String? = null
        var currentStartedAt: Instant? = null
        var currentEvidenceIds = mutableListOf<String>()

        for (event in taskEvents) {
            when (event.type) {
                EventType.TASK_STARTED, EventType.TASK_LAUNCHED -> {
                    currentAttemptNumber++
                    currentAttemptId = event.attemptId ?: "attempt-$currentAttemptNumber"
                    currentStartedAt = event.timestamp
                    currentEvidenceIds = mutableListOf(event.id)
                }

                EventType.TASK_SUCCEEDED -> {
                    val attemptId = currentAttemptId ?: "attempt-${currentAttemptNumber + 1}"
                    currentEvidenceIds.add(event.id)
                    attempts.add(
                        ExecutionAttempt(
                            attemptId = attemptId,
                            taskId = taskId,
                            attemptNumber = if (currentAttemptNumber == 0) 1 else currentAttemptNumber,
                            startedAt = currentStartedAt,
                            endedAt = event.timestamp,
                            outcome = AttemptOutcome.SUCCESS,
                            platformReason = null,
                            evidenceIds = currentEvidenceIds.toList()
                        )
                    )
                    currentStartedAt = null
                    currentAttemptId = null
                }

                EventType.TASK_FAILED -> {
                    val attemptId = currentAttemptId ?: "attempt-${currentAttemptNumber + 1}"
                    currentEvidenceIds.add(event.id)
                    val stopReasonCode = event.attributes["stop_reason"]?.toIntOrNull()
                    val platformReason = if (stopReasonCode != null) {
                        PlatformReason(
                            code = stopReasonCode,
                            name = event.attributes["stop_reason_name"] ?: "STOP_REASON_$stopReasonCode",
                            description = event.attributes["stop_reason_desc"]
                        )
                    } else null

                    attempts.add(
                        ExecutionAttempt(
                            attemptId = attemptId,
                            taskId = taskId,
                            attemptNumber = if (currentAttemptNumber == 0) 1 else currentAttemptNumber,
                            startedAt = currentStartedAt,
                            endedAt = event.timestamp,
                            outcome = AttemptOutcome.FAILED,
                            platformReason = platformReason,
                            evidenceIds = currentEvidenceIds.toList()
                        )
                    )
                    currentStartedAt = null
                    currentAttemptId = null
                }

                EventType.TASK_STOPPED -> {
                    val attemptId = currentAttemptId ?: "attempt-${currentAttemptNumber + 1}"
                    currentEvidenceIds.add(event.id)
                    val stopReasonCode = event.attributes["stop_reason"]?.toIntOrNull()
                    val platformReason = if (stopReasonCode != null) {
                        PlatformReason(
                            code = stopReasonCode,
                            name = event.attributes["stop_reason_name"] ?: "STOP_REASON_$stopReasonCode",
                            description = event.attributes["stop_reason_desc"]
                        )
                    } else null

                    attempts.add(
                        ExecutionAttempt(
                            attemptId = attemptId,
                            taskId = taskId,
                            attemptNumber = if (currentAttemptNumber == 0) 1 else currentAttemptNumber,
                            startedAt = currentStartedAt,
                            endedAt = event.timestamp,
                            outcome = AttemptOutcome.STOPPED,
                            platformReason = platformReason,
                            evidenceIds = currentEvidenceIds.toList()
                        )
                    )
                    currentStartedAt = null
                    currentAttemptId = null
                }

                EventType.TASK_EXPIRED -> {
                    val attemptId = currentAttemptId ?: "attempt-${currentAttemptNumber + 1}"
                    currentEvidenceIds.add(event.id)
                    attempts.add(
                        ExecutionAttempt(
                            attemptId = attemptId,
                            taskId = taskId,
                            attemptNumber = if (currentAttemptNumber == 0) 1 else currentAttemptNumber,
                            startedAt = currentStartedAt,
                            endedAt = event.timestamp,
                            outcome = AttemptOutcome.EXPIRED,
                            platformReason = PlatformReason(code = -1, name = "EXPIRED", description = "Task exceeded allocated background execution time"),
                            evidenceIds = currentEvidenceIds.toList()
                        )
                    )
                    currentStartedAt = null
                    currentAttemptId = null
                }

                EventType.TASK_RETRY_REQUESTED -> {
                    val attemptId = currentAttemptId ?: "attempt-${currentAttemptNumber + 1}"
                    currentEvidenceIds.add(event.id)
                    attempts.add(
                        ExecutionAttempt(
                            attemptId = attemptId,
                            taskId = taskId,
                            attemptNumber = if (currentAttemptNumber == 0) 1 else currentAttemptNumber,
                            startedAt = currentStartedAt,
                            endedAt = event.timestamp,
                            outcome = AttemptOutcome.RETRY,
                            platformReason = null,
                            evidenceIds = currentEvidenceIds.toList()
                        )
                    )
                    currentStartedAt = null
                    currentAttemptId = null
                }

                EventType.TASK_CANCELLED -> {
                    val attemptId = currentAttemptId ?: "attempt-${currentAttemptNumber + 1}"
                    currentEvidenceIds.add(event.id)
                    attempts.add(
                        ExecutionAttempt(
                            attemptId = attemptId,
                            taskId = taskId,
                            attemptNumber = if (currentAttemptNumber == 0) 1 else currentAttemptNumber,
                            startedAt = currentStartedAt,
                            endedAt = event.timestamp,
                            outcome = AttemptOutcome.CANCELLED,
                            platformReason = null,
                            evidenceIds = currentEvidenceIds.toList()
                        )
                    )
                    currentStartedAt = null
                    currentAttemptId = null
                }

                else -> {
                    if (currentStartedAt != null) {
                        currentEvidenceIds.add(event.id)
                    }
                }
            }
        }

        // If an attempt was started but has no terminal event, record as RUNNING
        if (currentStartedAt != null && currentAttemptId != null) {
            attempts.add(
                ExecutionAttempt(
                    attemptId = currentAttemptId,
                    taskId = taskId,
                    attemptNumber = currentAttemptNumber,
                    startedAt = currentStartedAt,
                    endedAt = null,
                    outcome = AttemptOutcome.RUNNING,
                    platformReason = null,
                    evidenceIds = currentEvidenceIds.toList()
                )
            )
        }

        return attempts
    }

    override fun buildTaskStory(task: ScheduledWork, events: List<TaskLensEvent>): CorrelatedTaskStory {
        val taskEvents = events.filter { it.taskId == task.id }
        val attempts = reconstructAttempts(task.id, taskEvents)
        val envEvents = events.filter {
            it.type in setOf(
                EventType.NETWORK_CHANGED,
                EventType.BATTERY_CHANGED,
                EventType.POWER_MODE_CHANGED,
                EventType.APP_STATE_CHANGED,
                EventType.PROCESS_STATE_CHANGED
            )
        }
        val networkTransfers = events.filter {
            it.type in setOf(
                EventType.URLSESSION_TRANSFER_STARTED,
                EventType.URLSESSION_TRANSFER_COMPLETED,
                EventType.URLSESSION_TRANSFER_FAILED,
                EventType.HTTP_REQUEST_STARTED,
                EventType.HTTP_REQUEST_COMPLETED,
                EventType.HTTP_REQUEST_FAILED
            ) && (it.taskId == task.id || it.attributes["correlation_id"] == task.id)
        }

        return CorrelatedTaskStory(
            task = task,
            attempts = attempts,
            events = taskEvents,
            environmentChanges = envEvents,
            networkTransfers = networkTransfers
        )
    }
}
