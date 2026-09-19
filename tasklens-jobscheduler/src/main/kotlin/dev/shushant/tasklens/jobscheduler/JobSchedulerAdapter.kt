package dev.shushant.tasklens.jobscheduler

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import dev.shushant.tasklens.android.TaskLens
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.TaskLensEvent

// ---------------------------------------------------------------------------
// JobServiceTaskLens  — abstract base for instrumented JobService subclasses
// ---------------------------------------------------------------------------

/**
 * An abstract [JobService] subclass that automatically emits TaskLens events
 * for [onStartJob] and [onStopJob] lifecycle transitions.
 *
 * ## Usage
 *
 * 1. Subclass [JobServiceTaskLens] and implement [doWork].
 * 2. Declare the concrete subclass in your app manifest with
 *    `android:permission="android.permission.BIND_JOB_SERVICE"`.
 * 3. Schedule using [JobSchedulerAdapter] or the standard [JobScheduler] API.
 *
 * ```kotlin
 * class MyJobService : JobServiceTaskLens() {
 *     override fun doWork(params: JobParameters): Boolean {
 *         // … long-running sync work (runs on main thread — launch a coroutine) …
 *         return true  // true = more work remaining (call jobFinished later)
 *     }
 * }
 * ```
 */
abstract class JobServiceTaskLens : JobService() {

    /**
     * Perform the actual work.
     *
     * Called on the main thread; launch a background coroutine for I/O-bound work.
     *
     * @return `true` if work is ongoing and [jobFinished] will be called later;
     *         `false` if the work completed synchronously.
     */
    abstract fun doWork(params: JobParameters): Boolean

    /**
     * Override to handle job stop requests (system reschedule or cancellation).
     * @return `true` to reschedule the job; `false` to drop it.
     */
    open fun onJobStopped(params: JobParameters): Boolean = false

    // ── Derived task ID ──────────────────────────────────────────────────────

    /** Produces a stable task ID from the job id and extras bundle. */
    protected open fun taskIdFor(params: JobParameters): String = "job_${params.jobId}"

    // ── JobService overrides ─────────────────────────────────────────────────

    override fun onStartJob(params: JobParameters): Boolean {
        val taskId = taskIdFor(params)

        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_STARTED,
                source = EventSource.JOB_SCHEDULER,
                severity = EventSeverity.INFO,
                attributes = mapOf(
                    "job_id" to params.jobId.toString(),
                    "job_service_class" to javaClass.simpleName
                )
            )
        )

        return try {
            doWork(params)
        } catch (t: Throwable) {
            TaskLens.emit(
                TaskLensEvent(
                    taskId = taskId,
                    type = EventType.TASK_FAILED,
                    source = EventSource.JOB_SCHEDULER,
                    severity = EventSeverity.ERROR,
                    attributes = mapOf(
                        "job_id" to params.jobId.toString(),
                        "job_service_class" to javaClass.simpleName,
                        "error_type" to (t::class.simpleName ?: "Unknown"),
                        "error_message" to (t.message?.take(500) ?: "")
                    )
                )
            )
            false
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val taskId = taskIdFor(params)

        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_STOPPED,
                source = EventSource.JOB_SCHEDULER,
                severity = EventSeverity.WARNING,
                attributes = buildMap {
                    put("job_id", params.jobId.toString())
                    put("job_service_class", javaClass.simpleName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        put("stop_reason", params.stopReason.toString())
                    }
                }
            )
        )

        return onJobStopped(params)
    }

    // ── Convenience helper ───────────────────────────────────────────────────

    /**
     * Call this when async work finishes.  Wraps [jobFinished] and emits the appropriate
     * [EventType.TASK_SUCCEEDED] or [EventType.TASK_FAILED] event.
     */
    fun finishJob(
        params: JobParameters,
        succeeded: Boolean,
        needsReschedule: Boolean = false,
        errorMessage: String? = null
    ) {
        val taskId = taskIdFor(params)
        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = if (succeeded) EventType.TASK_SUCCEEDED else EventType.TASK_FAILED,
                source = EventSource.JOB_SCHEDULER,
                severity = if (succeeded) EventSeverity.INFO else EventSeverity.ERROR,
                attributes = buildMap {
                    put("job_id", params.jobId.toString())
                    put("job_service_class", javaClass.simpleName)
                    if (!errorMessage.isNullOrBlank()) put("error_message", errorMessage.take(500))
                }
            )
        )
        jobFinished(params, needsReschedule)
    }
}

// ---------------------------------------------------------------------------
// JobSchedulerAdapter
// ---------------------------------------------------------------------------

/**
 * Thin wrapper around [JobScheduler] that adds TaskLens event emission when
 * scheduling and cancelling jobs.
 */
class JobSchedulerAdapter(private val context: Context) {

    private val scheduler = context.getSystemService(JobScheduler::class.java)

    /**
     * Schedules a job, emitting a [EventType.TASK_REGISTERED] event.
     *
     * @param jobInfo     The [JobInfo] to schedule.
     * @param taskId      Optional stable task ID; defaults to `"job_<jobId>"`.
     * @return [JobScheduler.RESULT_SUCCESS] or [JobScheduler.RESULT_FAILURE].
     */
    fun schedule(jobInfo: JobInfo, taskId: String? = null): Int {
        val tid = taskId ?: "job_${jobInfo.id}"
        val result = scheduler.schedule(jobInfo)

        val eventType = if (result == JobScheduler.RESULT_SUCCESS) {
            EventType.TASK_REGISTERED
        } else {
            EventType.TASK_FAILED
        }

        TaskLens.emit(
            TaskLensEvent(
                taskId = tid,
                type = eventType,
                source = EventSource.JOB_SCHEDULER,
                severity = if (result == JobScheduler.RESULT_SUCCESS) EventSeverity.INFO else EventSeverity.ERROR,
                attributes = mapOf(
                    "job_id" to jobInfo.id.toString(),
                    "service_class" to jobInfo.service.className,
                    "result" to if (result == JobScheduler.RESULT_SUCCESS) "success" else "failure"
                )
            )
        )

        return result
    }

    /**
     * Cancels a job by [jobId], emitting [EventType.TASK_CANCELLED].
     */
    fun cancel(jobId: Int, taskId: String? = null) {
        val tid = taskId ?: "job_$jobId"
        scheduler.cancel(jobId)

        TaskLens.emit(
            TaskLensEvent(
                taskId = tid,
                type = EventType.TASK_CANCELLED,
                source = EventSource.JOB_SCHEDULER,
                severity = EventSeverity.INFO,
                attributes = mapOf("job_id" to jobId.toString())
            )
        )
    }

    /** Cancels all pending jobs. */
    fun cancelAll() {
        scheduler.cancelAll()
        TaskLens.emit(
            TaskLensEvent(
                type = EventType.TASK_CANCELLED,
                source = EventSource.JOB_SCHEDULER,
                severity = EventSeverity.INFO,
                attributes = mapOf("scope" to "all")
            )
        )
    }

    /** Builds a [JobInfo] for a [JobServiceTaskLens] subclass. */
    fun buildJobInfo(
        jobId: Int,
        serviceClass: Class<out JobServiceTaskLens>,
        builder: JobInfo.Builder.() -> Unit = {}
    ): JobInfo {
        return JobInfo.Builder(jobId, ComponentName(context, serviceClass))
            .apply(builder)
            .build()
    }
}

// ---------------------------------------------------------------------------
// JobSchedulerObserver
// ---------------------------------------------------------------------------

/**
 * Provides a summary of all pending jobs in the [JobScheduler] queue.
 * Useful for diagnostic rules.
 */
object JobSchedulerObserver {

    /**
     * Returns a list of all pending jobs.  Returns empty list if [JobScheduler] is unavailable.
     */
    fun pendingJobs(context: Context): List<JobInfo> =
        context.getSystemService(JobScheduler::class.java)?.allPendingJobs ?: emptyList()

    /**
     * Returns `true` if a job with [jobId] is currently pending.
     */
    fun isJobPending(context: Context, jobId: Int): Boolean =
        pendingJobs(context).any { it.id == jobId }
}
