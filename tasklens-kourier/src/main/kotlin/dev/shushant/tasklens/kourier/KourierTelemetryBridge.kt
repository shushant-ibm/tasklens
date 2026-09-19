package dev.shushant.tasklens.kourier

import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.NetworkTelemetryBridge
import dev.shushant.tasklens.core.TaskLensEvent
import java.util.concurrent.atomic.AtomicBoolean

class KourierTelemetryBridge(
    private val eventSink: (TaskLensEvent) -> Unit
) : NetworkTelemetryBridge {

    private val isRunning = AtomicBoolean(false)

    override fun start() {
        isRunning.set(true)
    }

    override fun stop() {
        isRunning.set(false)
    }

    override fun isConnected(): Boolean = isRunning.get()

    fun onHttpRequestStart(
        requestId: String,
        url: String,
        method: String,
        taskId: String? = null,
        correlationId: String? = null
    ) {
        if (!isRunning.get()) return

        val attributes = mutableMapOf(
            "request_id" to requestId,
            "url" to url,
            "method" to method
        )
        if (!correlationId.isNullOrBlank()) {
            attributes["correlation_id"] = correlationId
        }

        eventSink(
            TaskLensEvent(
                taskId = taskId ?: correlationId,
                type = EventType.HTTP_REQUEST_STARTED,
                source = EventSource.KOURIER,
                attributes = attributes
            )
        )
    }

    fun onHttpRequestSuccess(
        requestId: String,
        statusCode: Int,
        durationMs: Long,
        taskId: String? = null,
        correlationId: String? = null
    ) {
        if (!isRunning.get()) return

        val attributes = mutableMapOf(
            "request_id" to requestId,
            "status_code" to statusCode.toString(),
            "duration_ms" to durationMs.toString()
        )
        if (!correlationId.isNullOrBlank()) {
            attributes["correlation_id"] = correlationId
        }

        eventSink(
            TaskLensEvent(
                taskId = taskId ?: correlationId,
                type = EventType.HTTP_REQUEST_COMPLETED,
                source = EventSource.KOURIER,
                attributes = attributes
            )
        )
    }

    fun onHttpRequestFailure(
        requestId: String,
        errorMessage: String,
        durationMs: Long,
        taskId: String? = null,
        correlationId: String? = null
    ) {
        if (!isRunning.get()) return

        val attributes = mutableMapOf(
            "request_id" to requestId,
            "error" to errorMessage,
            "duration_ms" to durationMs.toString()
        )
        if (!correlationId.isNullOrBlank()) {
            attributes["correlation_id"] = correlationId
        }

        eventSink(
            TaskLensEvent(
                taskId = taskId ?: correlationId,
                type = EventType.HTTP_REQUEST_FAILED,
                source = EventSource.KOURIER,
                severity = EventSeverity.WARNING,
                attributes = attributes
            )
        )
    }
}
