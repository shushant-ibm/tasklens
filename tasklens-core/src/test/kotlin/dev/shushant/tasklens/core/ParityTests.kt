package dev.shushant.tasklens.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parity tests that assert the tasklens-core-kmp commonMain sources declare
 * exactly the same enum entry counts as the canonical JVM tasklens-core sources.
 *
 * Since both modules share identical source files (copy-under-different-plugin),
 * these counts serve as a regression gate: if someone adds or removes an entry
 * in one module but forgets to update the other, CI will catch it here.
 */
class ParityTests {

    @Test
    fun testEventTypeCount() {
        // 15 task lifecycle + 7 system + 3 URLSession + 3 HTTP + 3 diagnostic/meta + 1 CUSTOM_BREADCRUMB = 32
        assertEquals(
            "EventType entry count mismatch — update tasklens-core-kmp commonMain to match",
            32,
            EventType.entries.size
        )
    }

    @Test
    fun testEventSourceCount() {
        // WORK_MANAGER, BG_TASK_SCHEDULER, JOB_SCHEDULER, FOREGROUND_SERVICE, ALARM_MANAGER,
        // URL_SESSION, SYSTEM_BROADCAST, APPLICATION, KOURIER, CUSTOM = 10
        assertEquals(
            "EventSource entry count mismatch — update tasklens-core-kmp commonMain to match",
            10,
            EventSource.entries.size
        )
    }

    @Test
    fun testEventSeverityCount() {
        // INFO, WARNING, ERROR, CRITICAL = 4
        assertEquals(
            "EventSeverity entry count mismatch — update tasklens-core-kmp commonMain to match",
            4,
            EventSeverity.entries.size
        )
    }

    @Test
    fun testTaskTypeCount() {
        // 11 entries
        assertEquals(
            "TaskType entry count mismatch — update tasklens-core-kmp commonMain to match",
            11,
            TaskType.entries.size
        )
    }

    @Test
    fun testSchedulerTypeCount() {
        // 7 entries
        assertEquals(
            "SchedulerType entry count mismatch — update tasklens-core-kmp commonMain to match",
            7,
            SchedulerType.entries.size
        )
    }

    @Test
    fun testAttemptOutcomeCount() {
        // 8 entries
        assertEquals(
            "AttemptOutcome entry count mismatch — update tasklens-core-kmp commonMain to match",
            8,
            AttemptOutcome.entries.size
        )
    }

    @Test
    fun testDiagnosisClassificationCount() {
        // 12 entries
        assertEquals(
            "DiagnosisClassification entry count mismatch — update tasklens-core-kmp commonMain to match",
            12,
            DiagnosisClassification.entries.size
        )
    }

    @Test
    fun testDiagnosisConfidenceCount() {
        // HIGH, MEDIUM, LOW, SPECULATIVE = 4
        assertEquals(
            "DiagnosisConfidence entry count mismatch — update tasklens-core-kmp commonMain to match",
            4,
            DiagnosisConfidence.entries.size
        )
    }

    @Test
    fun testPlatformLimitationCodeCount() {
        // 8 entries
        assertEquals(
            "PlatformLimitationCode entry count mismatch — update tasklens-core-kmp commonMain to match",
            8,
            PlatformLimitationCode.entries.size
        )
    }

    @Test
    fun testTimelineIconCount() {
        // 12 entries
        assertEquals(
            "TimelineIcon entry count mismatch — update tasklens-core-kmp commonMain to match",
            12,
            TimelineIcon.entries.size
        )
    }
}
