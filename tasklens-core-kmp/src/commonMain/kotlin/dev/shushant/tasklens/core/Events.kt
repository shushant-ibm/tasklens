package dev.shushant.tasklens.core

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.atomicfu.atomic
import kotlinx.serialization.Serializable

@Serializable
enum class EventType {
    TASK_REGISTERED,
    TASK_SUBMITTED,
    TASK_ENQUEUED,
    TASK_WAITING,
    TASK_ELIGIBLE,
    TASK_LAUNCHED,
    TASK_STARTED,
    TASK_STOPPED,
    TASK_EXPIRED,
    TASK_RETRY_REQUESTED,
    TASK_RESCHEDULED,
    TASK_SUCCEEDED,
    TASK_FAILED,
    TASK_CANCELLED,
    TASK_COMPLETION_REPORTED,

    CONSTRAINT_CHANGED,
    NETWORK_CHANGED,
    BATTERY_CHANGED,
    POWER_MODE_CHANGED,
    APP_STATE_CHANGED,
    PROCESS_STATE_CHANGED,
    BACKGROUND_REFRESH_CHANGED,

    URLSESSION_TRANSFER_STARTED,
    URLSESSION_TRANSFER_COMPLETED,
    URLSESSION_TRANSFER_FAILED,

    HTTP_REQUEST_STARTED,
    HTTP_REQUEST_COMPLETED,
    HTTP_REQUEST_FAILED,

    CLOCK_SHIFT_DETECTED,
    CONFIGURATION_WARNING,
    INTERNAL_DIAGNOSTIC,

    CUSTOM_BREADCRUMB
}

@Serializable
enum class EventSource {
    WORK_MANAGER,
    BG_TASK_SCHEDULER,
    JOB_SCHEDULER,
    FOREGROUND_SERVICE,
    ALARM_MANAGER,
    URL_SESSION,
    SYSTEM_BROADCAST,
    APPLICATION,
    KOURIER,
    CUSTOM
}

@Serializable
enum class EventSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/** Monotonically incrementing sequence generator backed by atomicfu — safe for all KMP targets. */
private object SequenceCounter {
    private val value = atomic(0L)
    fun next(): Long = value.incrementAndGet()
    fun reset(initialValue: Long = 0L) { value.value = initialValue }
}

@Serializable
data class TaskLensEvent(
    val id: String = generateId(),
    val taskId: String? = null,
    val attemptId: String? = null,
    val timestamp: Instant = Clock.System.now(),
    val sequenceNumber: Long = SequenceCounter.next(),
    val type: EventType,
    val source: EventSource,
    val severity: EventSeverity = EventSeverity.INFO,
    val attributes: Map<String, String> = emptyMap(),
    val schemaVersion: Int = 1
) {
    companion object {
        fun nextSequence(): Long = SequenceCounter.next()
        fun resetSequence(initialValue: Long = 0L) {
            SequenceCounter.reset(initialValue)
        }
    }
}

/** Pure-Kotlin UUID v4-like ID generator using Random — no java.util.UUID dependency. */
private fun generateId(): String {
    val bytes = ByteArray(16) { (kotlin.random.Random.nextInt(256).toByte()) }
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    return bytes.toHex()
}

private fun ByteArray.toHex(): String {
    val sb = StringBuilder(36)
    forEachIndexed { i, b ->
        if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
        sb.append(b.toInt().and(0xff).toString(16).padStart(2, '0'))
    }
    return sb.toString()
}
