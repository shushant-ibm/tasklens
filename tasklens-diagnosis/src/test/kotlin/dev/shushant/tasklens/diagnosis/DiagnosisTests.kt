package dev.shushant.tasklens.diagnosis

import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.DiagnosisClassification
import dev.shushant.tasklens.core.DiagnosisConfidence
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.PlatformReason
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.core.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class DiagnosisTests {

    private val engine = DefaultDiagnosisEngine()

    @Test
    fun testBlockedByNetworkDiagnosis() {
        val task = ScheduledWork(
            id = "task-sync",
            name = "OrderSyncWorker",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER
        )

        val waitingEvent = TaskLensEvent(
            taskId = "task-sync",
            type = EventType.TASK_WAITING,
            source = EventSource.WORK_MANAGER,
            attributes = mapOf(
                "required_network" to "CONNECTED",
                "current_network" to "DISCONNECTED",
                "network_satisfied" to "false"
            )
        )

        val context = DiagnosisContext(
            task = task,
            attempts = emptyList(),
            events = listOf(waitingEvent)
        )

        val diagnoses = engine.diagnose(context)
        assertFalse(diagnoses.isEmpty())

        val diagnosis = diagnoses.first()
        assertEquals(DiagnosisClassification.WAITING_ON_CONSTRAINT, diagnosis.classification)
        assertEquals(DiagnosisConfidence.CONFIRMED, diagnosis.confidence)
        assertEquals("Waiting on Network Constraint", diagnosis.title)
        assertEquals(1, diagnosis.evidence.size)
        assertTrue(diagnosis.evidence.first().description.contains("CONNECTED"))
    }

    @Test
    fun testPlatformStopReasonDiagnosis() {
        val task = ScheduledWork(
            id = "task-stopped",
            name = "UploadWorker",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER
        )

        val stopEvent = TaskLensEvent(
            taskId = "task-stopped",
            type = EventType.TASK_STOPPED,
            source = EventSource.WORK_MANAGER,
            attributes = mapOf(
                "stop_reason" to "3",
                "stop_reason_name" to "STOP_REASON_CONSTRAINT_CONNECTIVITY",
                "stop_reason_desc" to "Connectivity lost during expedited work"
            )
        )

        val attempt = ExecutionAttempt(
            attemptId = "att-1",
            taskId = "task-stopped",
            attemptNumber = 1,
            outcome = AttemptOutcome.STOPPED,
            platformReason = PlatformReason(
                code = 3,
                name = "STOP_REASON_CONSTRAINT_CONNECTIVITY",
                description = "Connectivity lost during expedited work"
            )
        )

        val context = DiagnosisContext(
            task = task,
            attempts = listOf(attempt),
            events = listOf(stopEvent)
        )

        val diagnoses = engine.diagnose(context)
        val diagnosis = diagnoses.first()
        assertEquals(DiagnosisClassification.PLATFORM_STOPPED, diagnosis.classification)
        assertEquals(DiagnosisConfidence.CONFIRMED, diagnosis.confidence)
        assertEquals(1, diagnosis.evidence.size)
        assertEquals("Platform Stop Reason: STOP_REASON_CONSTRAINT_CONNECTIVITY", diagnosis.evidence.first().title)
    }

    @Test
    fun testTaskExpiredDiagnosis() {
        val task = ScheduledWork(
            id = "com.example.refresh",
            name = "CatalogRefresh",
            type = TaskType.BG_APP_REFRESH,
            scheduler = SchedulerType.BG_TASK_SCHEDULER
        )

        val expireEvent = TaskLensEvent(
            taskId = "com.example.refresh",
            type = EventType.TASK_EXPIRED,
            source = EventSource.BG_TASK_SCHEDULER
        )

        val context = DiagnosisContext(
            task = task,
            attempts = emptyList(),
            events = listOf(expireEvent)
        )

        val diagnoses = engine.diagnose(context)
        val diagnosis = diagnoses.first()
        assertEquals(DiagnosisClassification.TASK_EXPIRED, diagnosis.classification)
        assertEquals(DiagnosisConfidence.CONFIRMED, diagnosis.confidence)
        assertEquals(1, diagnosis.limitations.size)
        assertEquals("BG_TASK_MAX_EXECUTION_TIME", diagnosis.limitations.first().code)
    }

    @Test
    fun testRetryRequestedDiagnosis() {
        val task = ScheduledWork(
            id = "task-retry",
            name = "PaymentSync",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER
        )

        val retryEvent = TaskLensEvent(
            taskId = "task-retry",
            type = EventType.TASK_RETRY_REQUESTED,
            source = EventSource.WORK_MANAGER
        )

        val context = DiagnosisContext(
            task = task,
            attempts = listOf(
                ExecutionAttempt(attemptId = "a1", taskId = "task-retry", attemptNumber = 1, outcome = AttemptOutcome.RETRY),
                ExecutionAttempt(attemptId = "a2", taskId = "task-retry", attemptNumber = 2, outcome = AttemptOutcome.RUNNING)
            ),
            events = listOf(retryEvent)
        )

        val diagnoses = engine.diagnose(context)
        val diagnosis = diagnoses.first()
        assertEquals(DiagnosisClassification.RETRY_REQUESTED, diagnosis.classification)
        assertEquals(DiagnosisConfidence.CONFIRMED, diagnosis.confidence)
    }
}
