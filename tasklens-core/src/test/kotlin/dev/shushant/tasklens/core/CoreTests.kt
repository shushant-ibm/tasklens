package dev.shushant.tasklens.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class CoreTests {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testEventSerializationAndSequence() {
        TaskLensEvent.resetSequence(100L)
        val event1 = TaskLensEvent(
            taskId = "task-123",
            type = EventType.TASK_ENQUEUED,
            source = EventSource.WORK_MANAGER,
            attributes = mapOf("worker_class" to "SyncWorker")
        )
        val event2 = TaskLensEvent(
            taskId = "task-123",
            type = EventType.TASK_STARTED,
            source = EventSource.WORK_MANAGER
        )

        assertEquals(101L, event1.sequenceNumber)
        assertEquals(102L, event2.sequenceNumber)

        val serialized = json.encodeToString(event1)
        assertTrue(serialized.contains("task-123"))
        assertTrue(serialized.contains("TASK_ENQUEUED"))

        val deserialized = json.decodeFromString<TaskLensEvent>(serialized)
        assertEquals(event1.id, deserialized.id)
        assertEquals(event1.taskId, deserialized.taskId)
        assertEquals(event1.type, deserialized.type)
        assertEquals(event1.sequenceNumber, deserialized.sequenceNumber)
    }

    @Test
    fun testDefaultRedactor() {
        val redactor = DefaultTaskLensRedactor

        // Sensitive keys
        assertEquals("[REDACTED]", redactor.redact("authorization", "Bearer eyJhbGci..."))
        assertEquals("[REDACTED]", redactor.redact("auth_token", "secret-token-xyz"))
        assertEquals("[REDACTED]", redactor.redact("password", "p@ssword123"))
        assertEquals("[REDACTED]", redactor.redact("apiKey", "key-999"))

        // Safe keys
        assertEquals("com.example.SyncWorker", redactor.redact("worker_class", "com.example.SyncWorker"))
        assertEquals("CONNECTED", redactor.redact("network_state", "CONNECTED"))

        // Truncation on oversize attributes (> 2 KB)
        val largeString = "a".repeat(3000)
        val redacted = redactor.redact("normal_payload", largeString)
        assertEquals(2048, redacted.length)
        assertTrue(redacted.endsWith(" [TRUNCATED]"))
    }

    @Test
    fun testCorrelationEngineAttemptReconstruction() {
        val correlator = DefaultCorrelationEngine()
        val now = Instant.fromEpochMilliseconds(1000000L)
        val t1 = Instant.fromEpochMilliseconds(1001000L)
        val t2 = Instant.fromEpochMilliseconds(1005000L)

        val events = listOf(
            TaskLensEvent(
                id = "e1",
                taskId = "task-abc",
                timestamp = now,
                type = EventType.TASK_ENQUEUED,
                source = EventSource.WORK_MANAGER
            ),
            TaskLensEvent(
                id = "e2",
                taskId = "task-abc",
                timestamp = t1,
                type = EventType.TASK_STARTED,
                source = EventSource.WORK_MANAGER
            ),
            TaskLensEvent(
                id = "e3",
                taskId = "task-abc",
                timestamp = t2,
                type = EventType.TASK_SUCCEEDED,
                source = EventSource.WORK_MANAGER
            )
        )

        val attempts = correlator.reconstructAttempts("task-abc", events)
        assertEquals(1, attempts.size)
        val attempt = attempts.first()
        assertEquals("task-abc", attempt.taskId)
        assertEquals(1, attempt.attemptNumber)
        assertEquals(AttemptOutcome.SUCCESS, attempt.outcome)
        assertEquals(t1, attempt.startedAt)
        assertEquals(t2, attempt.endedAt)
        assertEquals(2, attempt.evidenceIds.size)
    }

    @Test
    fun testTimelineBuilder() {
        val builder = DefaultTimelineBuilder()
        val task = ScheduledWork(
            id = "task-test",
            name = "CatalogSyncWorker",
            type = TaskType.COROUTINE_WORKER,
            scheduler = SchedulerType.WORK_MANAGER
        )

        val t0 = Instant.fromEpochMilliseconds(10000L)
        val t1 = Instant.fromEpochMilliseconds(12000L)
        val t2 = Instant.fromEpochMilliseconds(15000L)

        val events = listOf(
            TaskLensEvent(id = "e0", taskId = "task-test", timestamp = t0, type = EventType.TASK_SUBMITTED, source = EventSource.WORK_MANAGER),
            TaskLensEvent(id = "e1", taskId = "task-test", timestamp = t1, type = EventType.TASK_STARTED, source = EventSource.WORK_MANAGER),
            TaskLensEvent(id = "e2", taskId = "task-test", timestamp = t2, type = EventType.TASK_SUCCEEDED, source = EventSource.WORK_MANAGER)
        )

        val timeline = builder.build(task, events)
        assertEquals("task-test", timeline.taskId)
        assertEquals("CatalogSyncWorker", timeline.taskName)
        assertEquals(3, timeline.items.size)
        assertFalse(timeline.hasFailures)
        assertFalse(timeline.isOngoing)
        assertEquals(5000L, timeline.totalDurationMs)

        val successItem = timeline.items.last()
        assertEquals(TimelineIcon.SUCCESS, successItem.icon)
        assertEquals(3000L, successItem.durationMs) // 15000 - 12000
    }
}
