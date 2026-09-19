package dev.shushant.tasklens.storage

import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.TaskLensEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class InMemoryTaskLensStore : TaskLensStore {

    private val mutex = Mutex()
    private val tasksMap = LinkedHashMap<String, ScheduledWork>()
    private val attemptsMap = LinkedHashMap<String, MutableList<ExecutionAttempt>>()
    private val eventsList = mutableListOf<TaskLensEvent>()
    private val diagnosesMap = LinkedHashMap<String, MutableList<Diagnosis>>()

    override suspend fun append(event: TaskLensEvent): Unit = mutex.withLock {
        eventsList.add(event)
    }

    override suspend fun saveTask(task: ScheduledWork): Unit = mutex.withLock {
        tasksMap[task.id] = task
    }

    override suspend fun saveAttempt(attempt: ExecutionAttempt): Unit = mutex.withLock {
        val list = attemptsMap.getOrPut(attempt.taskId) { mutableListOf() }
        val index = list.indexOfFirst { it.attemptId == attempt.attemptId }
        if (index != -1) {
            list[index] = attempt
        } else {
            list.add(attempt)
        }
    }

    override suspend fun saveDiagnosis(diagnosis: Diagnosis): Unit = mutex.withLock {
        val list = diagnosesMap.getOrPut(diagnosis.taskId) { mutableListOf() }
        val index = list.indexOfFirst { it.id == diagnosis.id }
        if (index != -1) {
            list[index] = diagnosis
        } else {
            list.add(diagnosis)
        }
    }

    override suspend fun tasks(query: TaskQuery): List<ScheduledWork> = mutex.withLock {
        var sequence = tasksMap.values.asSequence()

        if (query.scheduler != null) {
            sequence = sequence.filter { it.scheduler == query.scheduler }
        }

        val search = query.searchQuery
        if (!search.isNullOrBlank()) {
            val q = search.lowercase()
            sequence = sequence.filter {
                it.id.lowercase().contains(q) ||
                    (it.name?.lowercase()?.contains(q) == true) ||
                    (it.platformId?.lowercase()?.contains(q) == true)
            }
        }

        if (query.status != null) {
            sequence = sequence.filter { task ->
                val attempts = attemptsMap[task.id]
                attempts?.lastOrNull()?.outcome == query.status
            }
        }

        sequence.drop(query.offset)
            .take(query.limit)
            .toList()
    }

    override suspend fun task(taskId: String): ScheduledWork? = mutex.withLock {
        tasksMap[taskId]
    }

    override suspend fun attempts(taskId: String): List<ExecutionAttempt> = mutex.withLock {
        attemptsMap[taskId]?.toList() ?: emptyList()
    }

    override suspend fun events(taskId: String): List<TaskLensEvent> = mutex.withLock {
        eventsList.filter { it.taskId == taskId || it.attributes["correlation_id"] == taskId }
            .sortedWith(compareBy({ it.timestamp }, { it.sequenceNumber }))
    }

    override suspend fun diagnoses(taskId: String): List<Diagnosis> = mutex.withLock {
        diagnosesMap[taskId]?.toList() ?: emptyList()
    }

    override suspend fun environmentEvents(limit: Int): List<TaskLensEvent> = mutex.withLock {
        eventsList.filter {
            it.type in setOf(
                EventType.NETWORK_CHANGED,
                EventType.BATTERY_CHANGED,
                EventType.POWER_MODE_CHANGED,
                EventType.APP_STATE_CHANGED,
                EventType.PROCESS_STATE_CHANGED,
                EventType.BACKGROUND_REFRESH_CHANGED
            )
        }.takeLast(limit)
    }

    override suspend fun delete(taskId: String): Unit = mutex.withLock {
        tasksMap.remove(taskId)
        attemptsMap.remove(taskId)
        eventsList.removeAll { it.taskId == taskId }
        diagnosesMap.remove(taskId)
    }

    override suspend fun clear(): Unit = mutex.withLock {
        tasksMap.clear()
        attemptsMap.clear()
        eventsList.clear()
        diagnosesMap.clear()
    }

    override suspend fun applyRetention(policy: RetentionPolicy): Unit = mutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        when (policy) {
            is RetentionPolicy.Forever -> Unit

            is RetentionPolicy.LastDays -> {
                val cutoff = now - policy.days.days.inWholeMilliseconds
                val expiredTaskIds = tasksMap.values.filter {
                    (it.submittedAt?.toEpochMilliseconds() ?: 0L) < cutoff
                }.map { it.id }.toSet()

                expiredTaskIds.forEach { id ->
                    tasksMap.remove(id)
                    attemptsMap.remove(id)
                    eventsList.removeAll { it.taskId == id }
                    diagnosesMap.remove(id)
                }
            }

            is RetentionPolicy.MaxTasks -> {
                if (tasksMap.size > policy.maxCount) {
                    val removeCount = tasksMap.size - policy.maxCount
                    val toRemove = tasksMap.keys.take(removeCount)
                    toRemove.forEach { id ->
                        tasksMap.remove(id)
                        attemptsMap.remove(id)
                        eventsList.removeAll { it.taskId == id }
                        diagnosesMap.remove(id)
                    }
                }
            }

            is RetentionPolicy.Combined -> {
                val cutoff = now - policy.days.days.inWholeMilliseconds
                val expiredTaskIds = tasksMap.values.filter {
                    (it.submittedAt?.toEpochMilliseconds() ?: 0L) < cutoff
                }.map { it.id }.toSet()

                expiredTaskIds.forEach { id ->
                    tasksMap.remove(id)
                    attemptsMap.remove(id)
                    eventsList.removeAll { it.taskId == id }
                    diagnosesMap.remove(id)
                }

                if (tasksMap.size > policy.maxTasks) {
                    val removeCount = tasksMap.size - policy.maxTasks
                    val toRemove = tasksMap.keys.take(removeCount)
                    toRemove.forEach { id ->
                        tasksMap.remove(id)
                        attemptsMap.remove(id)
                        eventsList.removeAll { it.taskId == id }
                        diagnosesMap.remove(id)
                    }
                }
            }
        }
    }
}
