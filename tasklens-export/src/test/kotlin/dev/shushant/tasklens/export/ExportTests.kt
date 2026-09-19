package dev.shushant.tasklens.export

import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.DefaultTaskLensRedactor
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.PlatformReason
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.core.TaskType
import dev.shushant.tasklens.storage.InMemoryTaskLensStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile
import kotlin.time.Instant

class ExportTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testExportToZip() = runTest {
        val store = InMemoryTaskLensStore()
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

        val task = ScheduledWork(
            id = "task-export-1",
            name = "SyncUploadWorker",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER,
            submittedAt = now
        )
        store.saveTask(task)

        val event1 = TaskLensEvent(
            taskId = "task-export-1",
            type = EventType.TASK_STARTED,
            source = EventSource.WORK_MANAGER,
            attributes = mapOf("token" to "super-secret-token", "device" to "Pixel 7 Pro")
        )
        val event2 = TaskLensEvent(
            taskId = "task-export-1",
            type = EventType.TASK_STOPPED,
            source = EventSource.WORK_MANAGER,
            attributes = mapOf("stop_reason" to "3", "stop_reason_name" to "STOP_REASON_CONSTRAINT_CONNECTIVITY")
        )
        store.append(event1)
        store.append(event2)

        val attempt = ExecutionAttempt(
            attemptId = "att-1",
            taskId = "task-export-1",
            attemptNumber = 1,
            startedAt = now,
            outcome = AttemptOutcome.STOPPED,
            platformReason = PlatformReason(3, "STOP_REASON_CONSTRAINT_CONNECTIVITY")
        )
        store.saveAttempt(attempt)

        val exporter = DefaultTaskLensExporter(store)
        val exportFile = File(tempFolder.root, "test_trace.tasklens")

        exporter.export(
            taskId = "task-export-1",
            outputFile = exportFile,
            deviceMetadata = mapOf("model" to "Pixel 7 Pro", "os" to "Android 15"),
            appVersion = "1.2.3",
            platform = "android",
            redactor = DefaultTaskLensRedactor
        )

        assertTrue(exportFile.exists())
        assertTrue(exportFile.length() > 0)

        // Verify ZIP contents
        ZipFile(exportFile).use { zip ->
            val entries = zip.entries().toList().map { it.name }.toSet()
            assertTrue(entries.contains("manifest.json"))
            assertTrue(entries.contains("task.json"))
            assertTrue(entries.contains("attempts.json"))
            assertTrue(entries.contains("timeline.json"))
            assertTrue(entries.contains("diagnosis.json"))
            assertTrue(entries.contains("events.json"))
            assertTrue(entries.contains("device.json"))
            assertTrue(entries.contains("README.html"))

            // Verify README.html contains task name and stop reason
            val htmlContent = zip.getInputStream(zip.getEntry("README.html")).bufferedReader().readText()
            assertTrue(htmlContent.contains("SyncUploadWorker"))
            assertTrue(htmlContent.contains("STOP_REASON_CONSTRAINT_CONNECTIVITY"))

            // Verify sensitive token was redacted in events.json
            val eventsJson = zip.getInputStream(zip.getEntry("events.json")).bufferedReader().readText()
            assertTrue(eventsJson.contains("[REDACTED]"))
            assertTrue(!eventsJson.contains("super-secret-token"))
        }
    }
}
