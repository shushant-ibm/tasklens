package dev.shushant.tasklens.workmanager

import android.content.Context
import android.util.Log
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.core.TaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock
import java.util.concurrent.ConcurrentHashMap

data class WorkSnapshot(
    val id: String,
    val workerClass: String?,
    val state: WorkInfo.State,
    val runAttemptCount: Int,
    val stopReason: Int,
    val tags: Set<String>
)

interface WorkManagerAdapter {
    fun start()
    fun stop()
    suspend fun snapshot(): List<WorkSnapshot>
}

class DefaultWorkManagerAdapter(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val taskSink: (suspend (ScheduledWork) -> Unit)? = null,
    private val eventSink: (TaskLensEvent) -> Unit = {}
) : WorkManagerAdapter {

    private val TAG = "WorkManagerAdapter"
    private var workManager: WorkManager? = null
    private val knownWorkStates = ConcurrentHashMap<String, WorkInfo.State>()
    private val knownAttemptCounts = ConcurrentHashMap<String, Int>()

    private var workObserver: Observer<List<WorkInfo>>? = null
    // Cache the LiveData so stop() can call removeObserver on the exact same instance.
    private var workInfoLiveData: androidx.lifecycle.LiveData<List<WorkInfo>>? = null

    override fun start() {
        try {
            val wm = WorkManager.getInstance(context)
            workManager = wm

            val query = WorkQuery.Builder.fromStates(
                listOf(
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.FAILED,
                    WorkInfo.State.BLOCKED,
                    WorkInfo.State.CANCELLED
                )
            ).build()

            val liveData = wm.getWorkInfosLiveData(query)
            workInfoLiveData = liveData  // cache for stop()

            val observer = Observer<List<WorkInfo>> { workInfoList ->
                if (workInfoList != null) {
                    scope.launch {
                        processWorkInfoList(workInfoList)
                    }
                }
            }
            workObserver = observer

            // Must observe on main thread for LiveData
            runOnMainThread {
                liveData.observeForever(observer)
            }

            Log.i(TAG, "WorkManagerAdapter listening to WorkInfo updates.")
        } catch (t: Throwable) {
            Log.w(TAG, "WorkManager not available or not yet initialized: ${t.message}")
        }
    }

    private suspend fun processWorkInfoList(workInfoList: List<WorkInfo>) {
        for (info in workInfoList) {
            val id = info.id.toString()
            val previousState = knownWorkStates[id]
            val currentState = info.state
            val previousAttempts = knownAttemptCounts[id] ?: 0
            val currentAttempts = info.runAttemptCount
            val workerClass = extractWorkerClass(info.tags)

            // Register task if new
            if (previousState == null) {
                val scheduledWork = ScheduledWork(
                    id = id,
                    platformId = id,
                    name = workerClass ?: "Worker-$id",
                    type = TaskType.WORKER,
                    scheduler = SchedulerType.WORK_MANAGER,
                    submittedAt = Clock.System.now(),
                    periodic = info.tags.any { it.contains("PeriodicWorkRequest", ignoreCase = true) },
                    metadata = mapOf(
                        "tags" to info.tags.joinToString(","),
                        "worker_class" to (workerClass ?: "")
                    )
                )
                taskSink?.invoke(scheduledWork)
            }

            // Detect retry
            if (currentAttempts > previousAttempts && previousState != null) {
                eventSink(
                    TaskLensEvent(
                        taskId = id,
                        type = EventType.TASK_RETRY_REQUESTED,
                        source = EventSource.WORK_MANAGER,
                        severity = EventSeverity.WARNING,
                        attributes = mapOf(
                            "attempt_number" to currentAttempts.toString(),
                            "worker_class" to (workerClass ?: "")
                        )
                    )
                )
            }
            knownAttemptCounts[id] = currentAttempts

            // Detect state transitions
            if (previousState != currentState) {
                knownWorkStates[id] = currentState
                val eventType = mapWorkStateToEvent(currentState)

                val attributes = mutableMapOf(
                    "state" to currentState.name,
                    "attempt_number" to currentAttempts.toString(),
                    "worker_class" to (workerClass ?: "")
                )

                if (currentState == WorkInfo.State.CANCELLED || currentState == WorkInfo.State.FAILED) {
                    val stopReasonCode = info.stopReason
                    if (stopReasonCode != WorkManagerStopReasons.STOP_REASON_NOT_STOPPED) {
                        val platformReason = WorkManagerStopReasons.toPlatformReason(stopReasonCode)
                        attributes["stop_reason"] = stopReasonCode.toString()
                        attributes["stop_reason_name"] = platformReason.name
                        platformReason.description?.let { attributes["stop_reason_desc"] = it }
                    }
                }

                eventSink(
                    TaskLensEvent(
                        taskId = id,
                        type = eventType,
                        source = EventSource.WORK_MANAGER,
                        severity = when (currentState) {
                            WorkInfo.State.FAILED -> EventSeverity.ERROR
                            WorkInfo.State.CANCELLED -> EventSeverity.WARNING
                            else -> EventSeverity.INFO
                        },
                        attributes = attributes
                    )
                )

                // If stop reason is present on terminal state, also emit explicit TASK_STOPPED event
                if (info.stopReason != WorkManagerStopReasons.STOP_REASON_NOT_STOPPED &&
                    (currentState == WorkInfo.State.FAILED || currentState == WorkInfo.State.CANCELLED)) {
                    val platformReason = WorkManagerStopReasons.toPlatformReason(info.stopReason)
                    eventSink(
                        TaskLensEvent(
                            taskId = id,
                            type = EventType.TASK_STOPPED,
                            source = EventSource.WORK_MANAGER,
                            severity = EventSeverity.WARNING,
                            attributes = mapOf(
                                "stop_reason" to info.stopReason.toString(),
                                "stop_reason_name" to platformReason.name,
                                "stop_reason_desc" to (platformReason.description ?: "")
                            )
                        )
                    )
                }
            }
        }
    }

    private fun mapWorkStateToEvent(state: WorkInfo.State): EventType {
        return when (state) {
            WorkInfo.State.ENQUEUED -> EventType.TASK_ENQUEUED
            WorkInfo.State.RUNNING -> EventType.TASK_STARTED
            WorkInfo.State.SUCCEEDED -> EventType.TASK_SUCCEEDED
            WorkInfo.State.FAILED -> EventType.TASK_FAILED
            WorkInfo.State.BLOCKED -> EventType.TASK_WAITING
            WorkInfo.State.CANCELLED -> EventType.TASK_CANCELLED
        }
    }

    private fun extractWorkerClass(tags: Set<String>): String? {
        return tags.firstOrNull { tag ->
            tag.contains(".") && !tag.startsWith("androidx.work")
        } ?: tags.firstOrNull()
    }

    override fun stop() {
        workObserver?.let { observer ->
            try {
                // Use the cached LiveData instance so removeObserver targets the
                // exact same object that observeForever was called on.
                runOnMainThread {
                    workInfoLiveData?.removeObserver(observer)
                }
            } catch (_: Throwable) {}
        }
        workObserver = null
        workInfoLiveData = null
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(block)
        }
    }

    override suspend fun snapshot(): List<WorkSnapshot> {
        val wm = workManager ?: return emptyList()
        val query = WorkQuery.Builder.fromStates(
            listOf(
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING,
                WorkInfo.State.SUCCEEDED,
                WorkInfo.State.FAILED,
                WorkInfo.State.BLOCKED,
                WorkInfo.State.CANCELLED
            )
        ).build()

        val list = wm.getWorkInfos(query).get() ?: return emptyList()
        return list.map { info ->
            WorkSnapshot(
                id = info.id.toString(),
                workerClass = extractWorkerClass(info.tags),
                state = info.state,
                runAttemptCount = info.runAttemptCount,
                stopReason = info.stopReason,
                tags = info.tags
            )
        }
    }
}
