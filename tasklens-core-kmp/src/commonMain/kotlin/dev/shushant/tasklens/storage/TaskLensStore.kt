package dev.shushant.tasklens.storage

import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.core.TaskLensEvent
import kotlinx.serialization.Serializable

@Serializable
sealed interface RetentionPolicy {
    @Serializable
    data class LastDays(val days: Int = 7) : RetentionPolicy

    @Serializable
    data class MaxTasks(val maxCount: Int = 500) : RetentionPolicy

    @Serializable
    data class Combined(val days: Int = 7, val maxTasks: Int = 500) : RetentionPolicy

    @Serializable
    data object Forever : RetentionPolicy
}

@Serializable
data class TaskQuery(
    val limit: Int = 100,
    val offset: Int = 0,
    val status: AttemptOutcome? = null,
    val scheduler: SchedulerType? = null,
    val searchQuery: String? = null
)

interface EventSink {
    suspend fun emit(event: TaskLensEvent)
}

interface TaskLensStore : EventSink {
    override suspend fun emit(event: TaskLensEvent) = append(event)
    suspend fun append(event: TaskLensEvent)
    suspend fun saveTask(task: ScheduledWork)
    suspend fun saveAttempt(attempt: ExecutionAttempt)
    suspend fun saveDiagnosis(diagnosis: Diagnosis)
    suspend fun tasks(query: TaskQuery = TaskQuery()): List<ScheduledWork>
    suspend fun task(taskId: String): ScheduledWork?
    suspend fun attempts(taskId: String): List<ExecutionAttempt>
    suspend fun events(taskId: String): List<TaskLensEvent>
    suspend fun diagnoses(taskId: String): List<Diagnosis>
    suspend fun environmentEvents(limit: Int = 100): List<TaskLensEvent>
    suspend fun delete(taskId: String)
    suspend fun clear()
    suspend fun applyRetention(policy: RetentionPolicy)
}
