package dev.shushant.tasklens.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces the architectural rule that tasklens-core-kmp is the single canonical source of truth
 * and that tasklens-core remains a zero-duplicate re-export facade.
 *
 * If duplicate source files are placed back into tasklens-core/src/main, this test fails immediately.
 */
class CanonicalCoreIntegrityTest {

    @Test
    fun testNoDuplicateSourcesInTaskLensCore() {
        val srcMain = File("src/main/kotlin")
        if (srcMain.exists()) {
            val kotlinFiles = srcMain.walkTopDown().filter { it.extension == "kt" }.toList()
            assertTrue(
                "Found duplicate source files in tasklens-core/src/main/kotlin: ${kotlinFiles.map { it.name }}. " +
                    "tasklens-core-kmp is the canonical source of truth; tasklens-core must remain a zero-duplicate facade.",
                kotlinFiles.isEmpty()
            )
        }
    }

    @Test
    fun testCanonicalModelResolution() {
        // Assert domain models are fully functional and instantiate properly via tasklens-core-kmp
        val event = TaskLensEvent(
            taskId = "test-canonical-1",
            type = EventType.TASK_SUBMITTED,
            source = EventSource.WORK_MANAGER
        )
        assertEquals("test-canonical-1", event.taskId)
        assertEquals(EventType.TASK_SUBMITTED, event.type)
        assertEquals(EventSource.WORK_MANAGER, event.source)

        val work = ScheduledWork(
            id = "test-work-1",
            name = "CanonicalWork",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER
        )
        assertEquals("test-work-1", work.id)
        assertEquals("CanonicalWork", work.name)
    }
}
