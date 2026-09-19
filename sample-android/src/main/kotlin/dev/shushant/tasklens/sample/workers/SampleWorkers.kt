package dev.shushant.tasklens.sample.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.shushant.tasklens.android.TaskLens
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.android.trace
import kotlinx.coroutines.delay

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return TaskLens.trace(this) {
            TaskLens.emit(
                TaskLensEvent(
                    taskId = id.toString(),
                    type = EventType.CUSTOM_BREADCRUMB,
                    source = EventSource.APPLICATION,
                    attributes = mapOf(
                        "title" to "Sync Step",
                        "description" to "Beginning cache synchronization",
                        "step" to "1_prepare"
                    )
                )
            )
            delay(1200)

            TaskLens.emit(
                TaskLensEvent(
                    taskId = id.toString(),
                    type = EventType.CUSTOM_BREADCRUMB,
                    source = EventSource.APPLICATION,
                    attributes = mapOf(
                        "title" to "Sync Complete",
                        "description" to "Synchronized 42 local entities successfully",
                        "step" to "2_finish",
                        "entities_synced" to "42"
                    )
                )
            )
            Result.success()
        }
    }
}

class NetworkRequiredWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return TaskLens.trace(this) {
            TaskLens.emit(
                TaskLensEvent(
                    taskId = id.toString(),
                    type = EventType.NETWORK_CHANGED,
                    source = EventSource.APPLICATION,
                    attributes = mapOf(
                        "title" to "Network Request Dispatched",
                        "description" to "Verifying external cloud reachability",
                        "endpoint" to "https://api.tasklens.dev/v1/ping"
                    )
                )
            )
            delay(1500)
            Result.success(workDataOf("status" to "online", "bytes_downloaded" to 1048576L))
        }
    }
}

class FlakyRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return TaskLens.trace(this) {
            val attempt = runAttemptCount
            TaskLens.emit(
                TaskLensEvent(
                    taskId = id.toString(),
                    type = EventType.TASK_RETRY_REQUESTED,
                    source = EventSource.APPLICATION,
                    severity = EventSeverity.WARNING,
                    attributes = mapOf(
                        "title" to "Simulating Flaky Service Attempt #$attempt",
                        "description" to "Service returned HTTP 503 Service Unavailable. Requesting retry.",
                        "attempt" to attempt.toString(),
                        "http_status" to "503"
                    )
                )
            )

            if (attempt < 2) {
                delay(800)
                Result.retry()
            } else {
                delay(800)
                Result.success(workDataOf("recovered_on_attempt" to attempt.toLong()))
            }
        }
    }
}

class FatalFailureWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return TaskLens.trace(this) {
            delay(1000)
            TaskLens.emit(
                TaskLensEvent(
                    taskId = id.toString(),
                    type = EventType.TASK_FAILED,
                    source = EventSource.APPLICATION,
                    severity = EventSeverity.ERROR,
                    attributes = mapOf(
                        "title" to "Unrecoverable Schema Corruption",
                        "description" to "Corrupted SQLite table state encountered during migration step 4.",
                        "error" to "Corrupted table state in SQLite migration"
                    )
                )
            )
            Result.failure(workDataOf("error" to "Corrupted table state in SQLite migration"))
        }
    }
}

class LongRunningWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return TaskLens.trace(this) {
            TaskLens.emit(
                TaskLensEvent(
                    taskId = id.toString(),
                    type = EventType.TASK_STARTED,
                    source = EventSource.APPLICATION,
                    attributes = mapOf(
                        "title" to "Long Processing Started",
                        "description" to "Worker is processing a long streaming batch. Cancel me from the UI to test stop reason diagnostics!",
                        "status" to "processing"
                    )
                )
            )

            for (i in 1..60) {
                if (isStopped) {
                    TaskLens.emit(
                        TaskLensEvent(
                            taskId = id.toString(),
                            type = EventType.TASK_STOPPED,
                            source = EventSource.APPLICATION,
                            attributes = mapOf(
                                "title" to "Worker Interrupted via isStopped",
                                "description" to "LongRunningWorker acknowledged stop flag at iteration $i",
                                "iteration" to i.toString()
                            )
                        )
                    )
                    return@trace Result.failure()
                }
                delay(1000)
            }
            Result.success()
        }
    }
}
