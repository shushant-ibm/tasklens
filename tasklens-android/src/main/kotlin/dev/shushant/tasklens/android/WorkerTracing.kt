package dev.shushant.tasklens.android

import androidx.work.ListenableWorker
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.TaskLensEvent
/**
 * Traces a [ListenableWorker] execution block with TaskLens instrumentation.
 *
 * Emits [EventType.TASK_STARTED] before the block runs and either
 * [EventType.TASK_SUCCEEDED] or [EventType.TASK_FAILED] after it completes.
 * The task ID is derived from `worker.id.toString()`.
 *
 * Usage:
 * ```kotlin
 * class MyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
 *     override suspend fun doWork(): Result = TaskLens.trace(this) {
 *         // ... your work ...
 *         Result.success()
 *     }
 * }
 * ```
 */
suspend fun <T> TaskLens.trace(
    worker: ListenableWorker,
    block: suspend () -> T
): T {
    val taskId = worker.id.toString()
    val workerClass = worker.javaClass.simpleName

    emit(
        TaskLensEvent(
            taskId = taskId,
            type = EventType.TASK_STARTED,
            source = EventSource.WORK_MANAGER,
            severity = EventSeverity.INFO,
            attributes = mapOf(
                "worker_class" to workerClass,
                "run_attempt_count" to worker.runAttemptCount.toString()
            )
        )
    )

    return try {
        val result = block()
        emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_SUCCEEDED,
                source = EventSource.WORK_MANAGER,
                severity = EventSeverity.INFO,
                attributes = mapOf(
                    "worker_class" to workerClass,
                    "run_attempt_count" to worker.runAttemptCount.toString()
                )
            )
        )
        result
    } catch (t: Throwable) {
        emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_FAILED,
                source = EventSource.WORK_MANAGER,
                severity = EventSeverity.ERROR,
                attributes = mapOf(
                    "worker_class" to workerClass,
                    "run_attempt_count" to worker.runAttemptCount.toString(),
                    "error_type" to (t::class.simpleName ?: "Unknown"),
                    "error_message" to (t.message?.take(500) ?: "")
                )
            )
        )
        throw t
    }
}
