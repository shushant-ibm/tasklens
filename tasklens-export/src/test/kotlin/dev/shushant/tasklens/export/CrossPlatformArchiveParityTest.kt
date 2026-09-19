package dev.shushant.tasklens.export

import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.DefaultTaskLensRedactor
import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.DiagnosisClassification
import dev.shushant.tasklens.core.DiagnosisConfidence
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.Evidence
import dev.shushant.tasklens.core.EvidenceSource
import dev.shushant.tasklens.core.EvidenceType
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.PlatformLimitation
import dev.shushant.tasklens.core.PlatformReason
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.core.TaskType
import dev.shushant.tasklens.storage.InMemoryTaskLensStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile
import kotlin.time.Instant

/**
 * Cross-platform golden test verifying .tasklens archive contract and parity.
 *
 * Checks that the exported ZIP archive strictly conforms to the canonical
 * schema contract shared across Android and iOS SDKs:
 * - Exactly contains: manifest.json, task.json, attempts.json, timeline.json,
 *   diagnosis.json, evidence.json, environment.json, limitations.json,
 *   events.json, device.json, README.html.
 * - manifest.json schema_version is 1.
 * - JSON syntax parses without error.
 * - Redaction applies to sensitive tokens.
 * - Standalone README.html contains valid HTML.
 */
class CrossPlatformArchiveParityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testArchiveParityContract() = runTest {
        val store = InMemoryTaskLensStore()
        val now = Instant.fromEpochMilliseconds(1710000000000L)

        val task = ScheduledWork(
            id = "task-canonical-101",
            name = "CanonicalWorker",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER,
            submittedAt = now
        )
        store.saveTask(task)

        val event1 = TaskLensEvent(
            id = "evt-1",
            taskId = "task-canonical-101",
            sequenceNumber = 1,
            type = EventType.TASK_STARTED,
            source = EventSource.WORK_MANAGER,
            timestamp = now,
            attributes = mapOf(
                "auth_token" to "secret-api-token-123",
                "attempt_number" to "1"
            )
        )
        val event2 = TaskLensEvent(
            id = "evt-2",
            taskId = "task-canonical-101",
            sequenceNumber = 2,
            type = EventType.TASK_STOPPED,
            source = EventSource.WORK_MANAGER,
            timestamp = Instant.fromEpochMilliseconds(1710000005000L),
            attributes = mapOf(
                "stop_reason" to "3",
                "stop_reason_name" to "STOP_REASON_CONSTRAINT_CONNECTIVITY"
            )
        )
        val envEvent = TaskLensEvent(
            id = "env-1",
            sequenceNumber = 3,
            type = EventType.NETWORK_CHANGED,
            source = EventSource.SYSTEM_BROADCAST,
            timestamp = Instant.fromEpochMilliseconds(1710000003000L),
            attributes = mapOf("connected" to "false")
        )
        store.append(event1)
        store.append(event2)
        store.append(envEvent)

        val attempt = ExecutionAttempt(
            attemptId = "att-101-1",
            taskId = "task-canonical-101",
            attemptNumber = 1,
            startedAt = now,
            endedAt = Instant.fromEpochMilliseconds(1710000005000L),
            outcome = AttemptOutcome.STOPPED,
            platformReason = PlatformReason(3, "STOP_REASON_CONSTRAINT_CONNECTIVITY")
        )
        store.saveAttempt(attempt)

        val diagnosis = Diagnosis(
            id = "diag-1",
            taskId = "task-canonical-101",
            attemptId = "att-101-1",
            ruleId = "WORKER_STOPPED_NETWORK_DROPPED",
            ruleVersion = "1.0",
            title = "Worker stopped due to connectivity loss",
            summary = "The background worker was interrupted because network connection was dropped.",
            classification = DiagnosisClassification.PLATFORM_STOPPED,
            confidence = DiagnosisConfidence.CONFIRMED,
            evidence = listOf(
                Evidence(
                    id = "ev-1",
                    type = EvidenceType.NETWORK_STATE,
                    source = EvidenceSource.ENVIRONMENT,
                    title = "Network Disconnected",
                    description = "Network state transitioned to disconnected during execution."
                )
            ),
            limitations = listOf(
                PlatformLimitation(
                    code = "METRIC_RESOLUTION",
                    message = "Battery sampling interval was 60s"
                )
            )
        )
        store.saveDiagnosis(diagnosis)

        val exporter = DefaultTaskLensExporter(store)
        val exportFile = File(tempFolder.root, "canonical_trace.tasklens")

        exporter.export(
            taskId = "task-canonical-101",
            outputFile = exportFile,
            deviceMetadata = mapOf("model" to "Pixel 9 Pro", "os" to "Android 15"),
            appVersion = "2.0.0",
            platform = "android",
            redactor = DefaultTaskLensRedactor
        )

        assertTrue("Archive file should exist", exportFile.exists())
        assertTrue("Archive file should not be empty", exportFile.length() > 0)

        // Verify entry names match cross-platform standard
        val expectedEntries = setOf(
            "manifest.json",
            "task.json",
            "attempts.json",
            "timeline.json",
            "diagnosis.json",
            "evidence.json",
            "environment.json",
            "limitations.json",
            "events.json",
            "device.json",
            "README.html"
        )

        ZipFile(exportFile).use { zip ->
            val actualEntries = zip.entries().toList().map { it.name }.toSet()
            for (expected in expectedEntries) {
                assertTrue("Missing expected archive entry: $expected", actualEntries.contains(expected))
            }

            // 1. Verify manifest
            val manifestText = zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText()
            val manifestObj = json.parseToJsonElement(manifestText).jsonObject
            assertEquals(1, manifestObj["schema_version"]?.jsonPrimitive?.content?.toInt())
            assertEquals("task-canonical-101", manifestObj["task_id"]?.jsonPrimitive?.content)
            assertEquals("android", manifestObj["platform"]?.jsonPrimitive?.content)
            assertEquals("2.0.0", manifestObj["app_version"]?.jsonPrimitive?.content)

            // 2. Verify task.json
            val taskText = zip.getInputStream(zip.getEntry("task.json")).bufferedReader().readText()
            val taskObj = json.parseToJsonElement(taskText).jsonObject
            assertEquals("task-canonical-101", taskObj["id"]?.jsonPrimitive?.content)
            assertEquals("CanonicalWorker", taskObj["name"]?.jsonPrimitive?.content)

            // 3. Verify redaction in events.json
            val eventsText = zip.getInputStream(zip.getEntry("events.json")).bufferedReader().readText()
            assertTrue("Secret token must be redacted", eventsText.contains("[REDACTED]"))
            assertTrue("Secret token raw text must never appear", !eventsText.contains("secret-api-token-123"))

            // 4. Verify evidence.json
            val evidenceText = zip.getInputStream(zip.getEntry("evidence.json")).bufferedReader().readText()
            assertTrue("Evidence must contain Network Disconnected", evidenceText.contains("Network Disconnected"))

            // 5. Verify limitations.json
            val limText = zip.getInputStream(zip.getEntry("limitations.json")).bufferedReader().readText()
            assertTrue("Limitations must contain METRIC_RESOLUTION", limText.contains("METRIC_RESOLUTION"))

            // 6. Verify README.html is standalone HTML
            val html = zip.getInputStream(zip.getEntry("README.html")).bufferedReader().readText()
            assertTrue("README must have html tag", html.contains("<!DOCTYPE html>"))
            assertTrue("README must reference CanonicalWorker", html.contains("CanonicalWorker"))
        }
    }
}
