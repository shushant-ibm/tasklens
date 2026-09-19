package dev.shushant.tasklens.android

import dev.shushant.tasklens.core.DefaultTaskLensRedactor
import dev.shushant.tasklens.core.NoOpTaskLensLogger
import dev.shushant.tasklens.core.TaskLensLogger
import dev.shushant.tasklens.core.TaskLensRedactor
import dev.shushant.tasklens.storage.RetentionPolicy

data class TaskLensConfig(
    val retentionPolicy: RetentionPolicy = RetentionPolicy.LastDays(7),
    val captureWorkerPayloads: Boolean = false,
    val captureEnvironment: Boolean = true,
    val enableKourierBridge: Boolean = true,
    val autoObserveWorkManager: Boolean = true,
    val diagnosticsEnabled: Boolean = true,
    val redactor: TaskLensRedactor = DefaultTaskLensRedactor,
    val logger: TaskLensLogger = NoOpTaskLensLogger
) {
    class Builder {
        var retentionPolicy: RetentionPolicy = RetentionPolicy.LastDays(7)
        var captureWorkerPayloads: Boolean = false
        var captureEnvironment: Boolean = true
        var enableKourierBridge: Boolean = true
        var autoObserveWorkManager: Boolean = true
        var diagnosticsEnabled: Boolean = true
        var redactor: TaskLensRedactor = DefaultTaskLensRedactor
        var logger: TaskLensLogger = NoOpTaskLensLogger

        fun retentionDays(days: Int) = apply {
            retentionPolicy = RetentionPolicy.LastDays(days)
        }

        fun retention(days: Int, maxTasks: Int) = apply {
            retentionPolicy = RetentionPolicy.Combined(days, maxTasks)
        }

        fun build(): TaskLensConfig = TaskLensConfig(
            retentionPolicy = retentionPolicy,
            captureWorkerPayloads = captureWorkerPayloads,
            captureEnvironment = captureEnvironment,
            enableKourierBridge = enableKourierBridge,
            autoObserveWorkManager = autoObserveWorkManager,
            diagnosticsEnabled = diagnosticsEnabled,
            redactor = redactor,
            logger = logger
        )
    }
}
