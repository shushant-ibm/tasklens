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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile
import kotlin.time.Instant

/**
 * Semantic cross-platform .tasklens parity test.
 *
 * Compares an exported .tasklens archive against the canonical cross-platform
 * specification contract after normalization:
 * - manifest.json
 * - task.json
 * - attempts.json
 * - events.json
 * - timeline.json
 * - diagnosis.json
 * - evidence.json
 * - environment.json
 * - limitations.json
 * - device.json
 * - README.html
 *
 * Platform-specific metadata may differ ONLY through an explicit allowlist.
 * Any unexpected structural deviation fails CI.
 */
class SemanticArchiveParityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = false }

    companion object {
        val REQUIRED_ARCHIVE_ENTRIES = setOf(
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

        val PLATFORM_METADATA_ALLOWLIST = setOf(
            "platform",
            "model",
            "os",
            "android_version",
            "sdk_int",
            "brand",
            "manufacturer"
        )
    }

    @Test
    fun testSemanticArchiveParityAgainstGoldenSpecification() = runTest {
        val store = InMemoryTaskLensStore()
        val goldenTaskId = "golden-sync-task-42"

        val goldenTask = ScheduledWork(
            id = goldenTaskId,
            name = "PeriodicSyncWorker",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER,
            submittedAt = Instant.fromEpochMilliseconds(1710000000000L),
            periodic = true
        )
        store.saveTask(goldenTask)

        val attempt1 = ExecutionAttempt(
            attemptId = "att-golden-1",
            taskId = goldenTaskId,
            attemptNumber = 1,
            startedAt = Instant.fromEpochMilliseconds(1710000000000L),
            endedAt = Instant.fromEpochMilliseconds(1710000005000L),
            outcome = AttemptOutcome.RETRY
        )
        val attempt2 = ExecutionAttempt(
            attemptId = "att-golden-2",
            taskId = goldenTaskId,
            attemptNumber = 2,
            startedAt = Instant.fromEpochMilliseconds(1710000010000L),
            endedAt = Instant.fromEpochMilliseconds(1710000015000L),
            outcome = AttemptOutcome.STOPPED,
            platformReason = PlatformReason(3, "STOP_REASON_CONSTRAINT_CONNECTIVITY")
        )
        store.saveAttempt(attempt1)
        store.saveAttempt(attempt2)

        val event1 = TaskLensEvent(
            id = "evt-1",
            taskId = goldenTaskId,
            attemptId = "att-golden-1",
            sequenceNumber = 1,
            type = EventType.TASK_STARTED,
            source = EventSource.WORK_MANAGER,
            timestamp = Instant.fromEpochMilliseconds(1710000000000L),
            attributes = mapOf("api_token" to "super-secret-token", "attempt" to "1")
        )
        val event2 = TaskLensEvent(
            id = "evt-2",
            taskId = goldenTaskId,
            attemptId = "att-golden-1",
            sequenceNumber = 2,
            type = EventType.TASK_RETRY_REQUESTED,
            source = EventSource.WORK_MANAGER,
            timestamp = Instant.fromEpochMilliseconds(1710000005000L),
            attributes = mapOf("retry_delay_ms" to "5000")
        )
        val event3 = TaskLensEvent(
            id = "evt-3",
            taskId = goldenTaskId,
            attemptId = "att-golden-2",
            sequenceNumber = 3,
            type = EventType.TASK_STARTED,
            source = EventSource.WORK_MANAGER,
            timestamp = Instant.fromEpochMilliseconds(1710000010000L),
            attributes = mapOf("attempt" to "2")
        )
        val event4 = TaskLensEvent(
            id = "evt-4",
            taskId = goldenTaskId,
            attemptId = "att-golden-2",
            sequenceNumber = 4,
            type = EventType.TASK_STOPPED,
            source = EventSource.WORK_MANAGER,
            timestamp = Instant.fromEpochMilliseconds(1710000015000L),
            attributes = mapOf("stop_reason" to "3")
        )
        val envEvent = TaskLensEvent(
            id = "env-evt-1",
            sequenceNumber = 5,
            type = EventType.NETWORK_CHANGED,
            source = EventSource.SYSTEM_BROADCAST,
            timestamp = Instant.fromEpochMilliseconds(1710000012000L),
            attributes = mapOf("connected" to "false")
        )

        store.append(event1)
        store.append(event2)
        store.append(event3)
        store.append(event4)
        store.append(envEvent)

        val diagnosis = Diagnosis(
            id = "diag-golden-1",
            taskId = goldenTaskId,
            attemptId = "att-golden-2",
            ruleId = "RULE_PLATFORM_STOPPED",
            ruleVersion = "1.0",
            title = "Execution Stopped by Platform",
            summary = "The operating system stopped background task execution.",
            classification = DiagnosisClassification.PLATFORM_STOPPED,
            confidence = DiagnosisConfidence.CONFIRMED,
            evidence = listOf(
                Evidence(
                    id = "ev-golden-1",
                    type = EvidenceType.PLATFORM_REASON,
                    source = EvidenceSource.PLATFORM,
                    title = "Constraint Dropped",
                    description = "STOP_REASON_CONSTRAINT_CONNECTIVITY"
                )
            ),
            limitations = listOf(
                PlatformLimitation(
                    code = "BG_TASK_LIMIT",
                    message = "Platform execution quota was reached."
                )
            )
        )
        store.saveDiagnosis(diagnosis)

        val exporter = DefaultTaskLensExporter(store)
        val archiveFile = File(tempFolder.root, "golden_semantic.tasklens")

        exporter.export(
            taskId = goldenTaskId,
            outputFile = archiveFile,
            deviceMetadata = mapOf(
                "model" to "Pixel 9",
                "os" to "Android 15",
                "android_version" to "15",
                "sdk_int" to "35",
                "brand" to "Google",
                "manufacturer" to "Google"
            ),
            appVersion = "1.0.0",
            platform = "android",
            redactor = DefaultTaskLensRedactor
        )

        assertTrue(archiveFile.exists())

        ZipFile(archiveFile).use { zip ->
            val actualEntries = zip.entries().toList().map { it.name }.toSet()

            // 1. Verify exact archive file set
            assertEquals("Archive file set must exactly match specification", REQUIRED_ARCHIVE_ENTRIES, actualEntries)

            // 2. Validate manifest.json schema
            val manifest = parseJsonObject(zip, "manifest.json")
            assertRequiredFields(manifest, setOf("schema_version", "task_id", "platform", "app_version", "exported_at_epoch_ms"))
            assertEquals(1, manifest["schema_version"]?.jsonPrimitive?.content?.toInt())
            assertEquals(goldenTaskId, manifest["task_id"]?.jsonPrimitive?.content)

            // 3. Validate task.json schema
            val task = parseJsonObject(zip, "task.json")
            assertRequiredFields(task, setOf("id", "name", "scheduler_type", "type", "periodic"))
            assertEquals(goldenTaskId, task["id"]?.jsonPrimitive?.content)
            assertEquals("PeriodicSyncWorker", task["name"]?.jsonPrimitive?.content)
            assertEquals(true, task["periodic"]?.jsonPrimitive?.content?.toBoolean())

            // 4. Validate attempts.json schema & counts
            val attempts = parseJsonArray(zip, "attempts.json")
            assertEquals(2, attempts.size)
            for (att in attempts) {
                assertRequiredFields(att.jsonObject, setOf("id", "task_id", "attempt_number", "outcome", "started_at_epoch_ms", "ended_at_epoch_ms", "duration_ms", "stop_reason"))
            }

            // 5. Validate events.json & sensitive data redaction
            val events = parseJsonArray(zip, "events.json")
            assertEquals(4, events.size)
            for (evt in events) {
                val obj = evt.jsonObject
                assertRequiredFields(obj, setOf("id", "task_id", "attempt_id", "timestamp_epoch_ms", "sequence_number", "type", "source", "severity", "schema_version", "attributes"))
            }
            val eventsRaw = zip.getInputStream(zip.getEntry("events.json")).bufferedReader().readText()
            assertTrue("Secret api_token must be redacted", eventsRaw.contains("[REDACTED]"))
            assertTrue("Secret token plain text must never appear in export", !eventsRaw.contains("super-secret-token"))

            // 6. Validate timeline.json
            val timeline = parseJsonArray(zip, "timeline.json")
            assertEquals(4, timeline.size)
            for (entry in timeline) {
                assertRequiredFields(entry.jsonObject, setOf("type", "timestamp_epoch_ms", "attributes"))
            }

            // 7. Validate diagnosis.json
            val diagnoses = parseJsonArray(zip, "diagnosis.json")
            assertEquals(1, diagnoses.size)
            val diagObj = diagnoses.first().jsonObject
            assertRequiredFields(diagObj, setOf("id", "task_id", "rule_id", "title", "summary", "confidence", "classification"))
            assertEquals("RULE_PLATFORM_STOPPED", diagObj["rule_id"]?.jsonPrimitive?.content)

            // 8. Validate evidence.json
            val evidence = parseJsonArray(zip, "evidence.json")
            assertEquals(1, evidence.size)

            // 9. Validate limitations.json
            val limitations = parseJsonArray(zip, "limitations.json")
            assertEquals(1, limitations.size)
            assertEquals("BG_TASK_LIMIT", limitations.first().jsonObject["code"]?.jsonPrimitive?.content)

            // 10. Validate device.json
            val device = parseJsonObject(zip, "device.json")
            for (k in device.keys) {
                assertTrue("Device metadata key '$k' must be in PLATFORM_METADATA_ALLOWLIST", PLATFORM_METADATA_ALLOWLIST.contains(k))
            }

            // 11. Validate README.html
            val readme = zip.getInputStream(zip.getEntry("README.html")).bufferedReader().readText()
            assertTrue("README must have DOCTYPE", readme.contains("<!DOCTYPE html>"))
            assertTrue("README must include task name", readme.contains("PeriodicSyncWorker"))
            assertTrue("README must close html", readme.contains("</html>"))
        }
    }

    private fun parseJsonObject(zip: ZipFile, entryName: String): JsonObject {
        val entry = zip.getEntry(entryName) ?: throw AssertionError("Missing entry: $entryName")
        val text = zip.getInputStream(entry).bufferedReader().readText()
        return json.parseToJsonElement(text).jsonObject
    }

    private fun parseJsonArray(zip: ZipFile, entryName: String): JsonArray {
        val entry = zip.getEntry(entryName) ?: throw AssertionError("Missing entry: $entryName")
        val text = zip.getInputStream(entry).bufferedReader().readText()
        return json.parseToJsonElement(text).jsonArray
    }

    private fun assertRequiredFields(obj: JsonObject, expectedFields: Set<String>) {
        for (field in expectedFields) {
            assertTrue("JSON Object missing required field '$field'. Present keys: ${obj.keys}", obj.containsKey(field))
        }
    }
}
