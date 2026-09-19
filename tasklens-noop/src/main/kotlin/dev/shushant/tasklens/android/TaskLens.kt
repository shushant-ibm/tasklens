package dev.shushant.tasklens.android

import android.app.Application
import android.content.Context
import java.io.File

// ---------------------------------------------------------------------------
// Retention policy — mirrored from tasklens-storage (noop has no deps on it)
// ---------------------------------------------------------------------------

sealed interface RetentionPolicy {
    data class LastDays(val days: Int = 7) : RetentionPolicy
    data class MaxTasks(val maxCount: Int = 500) : RetentionPolicy
    data class Combined(val days: Int = 7, val maxTasks: Int = 500) : RetentionPolicy
    data object Forever : RetentionPolicy
}

// ---------------------------------------------------------------------------
// Logger — mirrored from tasklens-core (noop has no deps on it)
// ---------------------------------------------------------------------------

enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

interface TaskLensLogger {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}

object NoOpTaskLensLogger : TaskLensLogger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
}

// ---------------------------------------------------------------------------
// Redactor — mirrored from tasklens-core
// ---------------------------------------------------------------------------

interface TaskLensRedactor {
    fun redact(key: String, value: String): String
}

object DefaultTaskLensRedactor : TaskLensRedactor {
    override fun redact(key: String, value: String): String = value
}

// ---------------------------------------------------------------------------
// Event stub — the noop emit() accepts the real type via Any to avoid
// a compile-time dependency on tasklens-core, but is typed precisely
// to match the call-site signature used in tasklens-android.
// ---------------------------------------------------------------------------

/** Marker interface so callers can pass a TaskLensEvent without a hard dep. */
interface TaskLensEventMarker

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// HealthState — mirrored from tasklens-core (noop has no deps on it)
// ---------------------------------------------------------------------------

enum class HealthState {
    /** SDK is running normally; all sub-systems are healthy. */
    READY,
    /** SQLite storage layer failed to initialise or is unavailable. */
    DEGRADED_STORAGE,
    /** Kourier network telemetry bridge failed to start. */
    DEGRADED_KOURIER,
    /** Exporter failed to write the last export bundle. */
    DEGRADED_EXPORT,
    /** SDK has not been installed yet. */
    DISABLED
}

// ---------------------------------------------------------------------------
// TaskLens — release no-op object
// ---------------------------------------------------------------------------

object TaskLens {

    @Volatile private var installed = false
    private var config = TaskLensConfig()

    fun install(
        application: Application,
        config: TaskLensConfig = TaskLensConfig()
    ) {
        installed = true
        this.config = config
    }

    fun getConfig(): TaskLensConfig = config

    fun install(
        application: Application,
        block: TaskLensConfig.Builder.() -> Unit
    ) = install(application, TaskLensConfig.Builder().apply(block).build())

    fun isInstalled(): Boolean = installed

    fun show(context: Context? = null) = Unit

    fun showUI(context: Context? = null) = Unit

    fun hide() = Unit

    fun hideUI() = Unit

    fun clear() = Unit

    /**
     * Always returns [HealthState.DISABLED] in the no-op variant.
     * The real implementation in tasklens-android checks all sub-systems.
     */
    fun health(): HealthState = HealthState.DISABLED

    /** Accepts any object so call-sites pass TaskLensEvent without a hard dep. */
    fun emit(event: Any) = Unit

    fun breadcrumb(
        title: String,
        message: String,
        taskId: String? = null,
        attributes: Map<String, String> = emptyMap()
    ) = Unit

    suspend fun <T> withCorrelation(
        correlationId: String,
        block: suspend () -> T
    ): T = block()

    suspend fun export(taskId: String, destinationFile: File? = null): File {
        return destinationFile ?: File.createTempFile("tasklens_noop", ".tasklens")
    }
}

// ---------------------------------------------------------------------------
// Worker tracing — release no-op with zero allocations
// ---------------------------------------------------------------------------

/**
 * Release no-op trace: directly executes the block with zero overhead and
 * zero allocations. Typed to [Any] so the noop module compiles without a
 * dependency on androidx.work.ListenableWorker.
 */
inline suspend fun <T> TaskLens.trace(
    worker: Any,
    crossinline block: suspend () -> T
): T = block()
