package dev.shushant.tasklens.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CommonCoreTests {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testEventSerializationAndSequence() {
        TaskLensEvent.resetSequence(200L)
        val event1 = TaskLensEvent(
            taskId = "task-kmp-1",
            type = EventType.TASK_ENQUEUED,
            source = EventSource.WORK_MANAGER,
            attributes = mapOf("worker_class" to "SyncWorker")
        )
        val event2 = TaskLensEvent(
            taskId = "task-kmp-1",
            type = EventType.TASK_STARTED,
            source = EventSource.WORK_MANAGER
        )

        assertEquals(201L, event1.sequenceNumber)
        assertEquals(202L, event2.sequenceNumber)

        val serialized = json.encodeToString(event1)
        assertTrue(serialized.contains("task-kmp-1"))
        assertTrue(serialized.contains("TASK_ENQUEUED"))

        val deserialized = json.decodeFromString<TaskLensEvent>(serialized)
        assertEquals(event1.id, deserialized.id)
        assertEquals(event1.taskId, deserialized.taskId)
        assertEquals(event1.type, deserialized.type)
        assertEquals(event1.sequenceNumber, deserialized.sequenceNumber)
        assertEquals(event1.schemaVersion, deserialized.schemaVersion)
    }

    @Test
    fun testDefaultRedactor() {
        val redactor = DefaultTaskLensRedactor

        // Sensitive keys must be redacted
        assertEquals("[REDACTED]", redactor.redact("authorization", "Bearer eyJhbGci..."))
        assertEquals("[REDACTED]", redactor.redact("auth_token", "secret-token-xyz"))
        assertEquals("[REDACTED]", redactor.redact("password", "p@ssword123"))
        assertEquals("[REDACTED]", redactor.redact("apiKey", "key-999"))

        // Safe keys must pass through
        assertEquals(
            "com.example.SyncWorker",
            redactor.redact("worker_class", "com.example.SyncWorker")
        )
        assertEquals("CONNECTED", redactor.redact("network_state", "CONNECTED"))

        // Values > 2 KB must be truncated with suffix
        val largeString = "a".repeat(3000)
        val truncated = redactor.redact("normal_payload", largeString)
        assertEquals(2048, truncated.length)
        assertTrue(truncated.endsWith(" [TRUNCATED]"))
    }

    @Test
    fun testCorrelationEngineAttemptReconstruction() {
        val correlator = DefaultCorrelationEngine()
        val enqueued = Instant.fromEpochMilliseconds(1_000_000L)
        val started  = Instant.fromEpochMilliseconds(1_001_000L)
        val finished = Instant.fromEpochMilliseconds(1_005_000L)

        val events = listOf(
            TaskLensEvent(
                id = "e1",
                taskId = "task-kmp-abc",
                timestamp = enqueued,
                type = EventType.TASK_ENQUEUED,
                source = EventSource.WORK_MANAGER
            ),
            TaskLensEvent(
                id = "e2",
                taskId = "task-kmp-abc",
                timestamp = started,
                type = EventType.TASK_STARTED,
                source = EventSource.WORK_MANAGER
            ),
            TaskLensEvent(
                id = "e3",
                taskId = "task-kmp-abc",
                timestamp = finished,
                type = EventType.TASK_SUCCEEDED,
                source = EventSource.WORK_MANAGER
            )
        )

        val attempts = correlator.reconstructAttempts("task-kmp-abc", events)
        assertEquals(1, attempts.size)
        val attempt = attempts.first()
        assertEquals("task-kmp-abc", attempt.taskId)
        assertEquals(1, attempt.attemptNumber)
        assertEquals(AttemptOutcome.SUCCESS, attempt.outcome)
        assertEquals(started, attempt.startedAt)
        assertEquals(finished, attempt.endedAt)
        assertEquals(2, attempt.evidenceIds.size)
    }

    @Test
    fun testTimelineBuilder() {
        val builder = DefaultTimelineBuilder()
        val task = ScheduledWork(
            id = "task-kmp-test",
            name = "KMPSyncWorker",
            type = TaskType.COROUTINE_WORKER,
            scheduler = SchedulerType.WORK_MANAGER
        )

        val t0 = Instant.fromEpochMilliseconds(10_000L)
        val t1 = Instant.fromEpochMilliseconds(12_000L)
        val t2 = Instant.fromEpochMilliseconds(15_000L)

        val events = listOf(
            TaskLensEvent(id = "e0", taskId = "task-kmp-test", timestamp = t0,
                type = EventType.TASK_SUBMITTED, source = EventSource.WORK_MANAGER),
            TaskLensEvent(id = "e1", taskId = "task-kmp-test", timestamp = t1,
                type = EventType.TASK_STARTED, source = EventSource.WORK_MANAGER),
            TaskLensEvent(id = "e2", taskId = "task-kmp-test", timestamp = t2,
                type = EventType.TASK_SUCCEEDED, source = EventSource.WORK_MANAGER)
        )

        val timeline = builder.build(task, events)
        assertEquals("task-kmp-test", timeline.taskId)
        assertEquals("KMPSyncWorker", timeline.taskName)
        assertEquals(3, timeline.items.size)
        assertFalse(timeline.hasFailures)
        assertFalse(timeline.isOngoing)
        assertEquals(5_000L, timeline.totalDurationMs)

        val last = timeline.items.last()
        assertEquals(TimelineIcon.SUCCESS, last.icon)
        assertEquals(3_000L, last.durationMs)
    }

    @Test
    fun testEventTypeCoverage() {
        // Ensure every EventType can be round-tripped through JSON
        for (type in EventType.entries) {
            val event = TaskLensEvent(
                taskId = "coverage",
                type = type,
                source = EventSource.CUSTOM
            )
            val s = json.encodeToString(event)
            val d = json.decodeFromString<TaskLensEvent>(s)
            assertEquals(type, d.type)
        }
    }

    @Test
    fun testEventSourceCoverage() {
        for (source in EventSource.entries) {
            val event = TaskLensEvent(
                taskId = "coverage",
                type = EventType.CUSTOM_BREADCRUMB,
                source = source
            )
            val s = json.encodeToString(event)
            val d = json.decodeFromString<TaskLensEvent>(s)
            assertEquals(source, d.source)
        }
    }

    @Test
    fun testEventSeverityDefaults() {
        val event = TaskLensEvent(
            taskId = "sev-test",
            type = EventType.INTERNAL_DIAGNOSTIC,
            source = EventSource.APPLICATION
        )
        assertEquals(EventSeverity.INFO, event.severity)
        assertEquals(1, event.schemaVersion)
    }
}
