package dev.shushant.tasklens.storage

import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.core.TaskType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class StorageTests {

    @Test
    fun testStoreSaveAndQuery() = runTest {
        val store = InMemoryTaskLensStore()
        val task1 = ScheduledWork(
            id = "t1",
            name = "Work1",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER
        )
        val task2 = ScheduledWork(
            id = "t2",
            name = "Work2",
            type = TaskType.COROUTINE_WORKER,
            scheduler = SchedulerType.BG_TASK_SCHEDULER
        )

        store.saveTask(task1)
        store.saveTask(task2)

        val allTasks = store.tasks()
        assertEquals(2, allTasks.size)

        val workManagerTasks = store.tasks(TaskQuery(scheduler = SchedulerType.WORK_MANAGER))
        assertEquals(1, workManagerTasks.size)
        assertEquals("t1", workManagerTasks.first().id)

        val searchResult = store.tasks(TaskQuery(searchQuery = "Work2"))
        assertEquals(1, searchResult.size)
        assertEquals("t2", searchResult.first().id)
    }

    @Test
    fun testAttemptAndEventStorage() = runTest {
        val store = InMemoryTaskLensStore()
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

        val event = TaskLensEvent(
            taskId = "task-x",
            type = EventType.TASK_STARTED,
            source = EventSource.WORK_MANAGER
        )
        store.append(event)

        val attempt = ExecutionAttempt(
            attemptId = "att-1",
            taskId = "task-x",
            attemptNumber = 1,
            startedAt = now,
            outcome = AttemptOutcome.RUNNING
        )
        store.saveAttempt(attempt)

        val events = store.events("task-x")
        assertEquals(1, events.size)
        assertEquals(EventType.TASK_STARTED, events.first().type)

        val attempts = store.attempts("task-x")
        assertEquals(1, attempts.size)
        assertEquals(AttemptOutcome.RUNNING, attempts.first().outcome)
    }

    @Test
    fun testRetentionPolicy() = runTest {
        val store = InMemoryTaskLensStore()
        val now = System.currentTimeMillis()
        val tenDaysAgo = Instant.fromEpochMilliseconds(now - 10.days.inWholeMilliseconds)
        val oneDayAgo = Instant.fromEpochMilliseconds(now - 1.days.inWholeMilliseconds)

        val oldTask = ScheduledWork(
            id = "old-task",
            submittedAt = tenDaysAgo
        )
        val newTask = ScheduledWork(
            id = "new-task",
            submittedAt = oneDayAgo
        )

        store.saveTask(oldTask)
        store.saveTask(newTask)
        assertEquals(2, store.tasks().size)

        // Apply retention of 7 days
        store.applyRetention(RetentionPolicy.LastDays(7))
        val remaining = store.tasks()
        assertEquals(1, remaining.size)
        assertEquals("new-task", remaining.first().id)
        assertNull(store.task("old-task"))
    }
}
