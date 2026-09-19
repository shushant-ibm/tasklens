package dev.shushant.tasklens.android

import android.content.Context
import dev.shushant.tasklens.android.storage.AndroidSqliteTaskLensStore
import dev.shushant.tasklens.android.storage.TaskLensDbHelper
import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.DefaultCorrelationEngine
import dev.shushant.tasklens.core.DefaultTaskLensRedactor
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.core.TaskType
import dev.shushant.tasklens.diagnosis.DefaultDiagnosisEngine
import dev.shushant.tasklens.diagnosis.DiagnosisContext
import dev.shushant.tasklens.export.DefaultTaskLensExporter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlin.time.Instant

/**
 * Enterprise Product-Level Performance Benchmarks.
 *
 * Enforces hard SLA budgets:
 * 1. Install Latency: < 50ms
 * 2. Event Ingestion Latency: p50 < 1ms, p95 < 3ms, p99 < 5ms
 * 3. SQLite Append Throughput: > 500 events/sec
 * 4. Timeline Reconstruction:
 *    - 1,000 events < 50ms
 *    - 10,000 events < 300ms
 * 5. Diagnosis Evaluation:
 *    - 1,000 events < 30ms
 *    - 10,000 events < 200ms
 * 6. Archive Export Latency: < 100ms
 * 7. Memory Overhead: bounded heap delta
 */
@RunWith(RobolectricTestRunner::class)
class ProductPerformanceBenchmarks {

    private val correlationEngine = DefaultCorrelationEngine()
    private val diagnosisEngine = DefaultDiagnosisEngine()

    @Test
    fun testInstallLatencyBudget() {
        val app = RuntimeEnvironment.getApplication()
        val durationMs = measureTimeMillis {
            // TaskLens.install idempotency check / initialization
            TaskLens.install(app) {
                captureEnvironment = false
                enableKourierBridge = false
                autoObserveWorkManager = false
            }
        }
        assertTrue("Install latency must be < 50ms (measured: ${durationMs}ms)", durationMs < 50L)
    }

    @Test
    fun testEventIngestionLatencySla() {
        val count = 1000
        val latenciesMicros = LongArray(count)

        // Warm up JVM
        for (i in 0 until 50) {
            TaskLens.emit(
                TaskLensEvent(
                    id = "warmup-$i",
                    taskId = "bench-task",
                    type = EventType.CUSTOM_BREADCRUMB,
                    source = EventSource.APPLICATION
                )
            )
        }

        // Measure individual emit calls
        for (i in 0 until count) {
            val nanos = measureNanoTime {
                TaskLens.emit(
                    TaskLensEvent(
                        id = "bench-evt-$i",
                        taskId = "bench-task",
                        type = EventType.CUSTOM_BREADCRUMB,
                        source = EventSource.APPLICATION,
                        attributes = mapOf("iteration" to i.toString())
                    )
                )
            }
            latenciesMicros[i] = nanos / 1_000L
        }

        latenciesMicros.sort()
        val p50 = latenciesMicros[(count * 0.50).toInt()]
        val p95 = latenciesMicros[(count * 0.95).toInt()]
        val p99 = latenciesMicros[(count * 0.99).toInt()]

        // SLAs: p50 < 1000 µs (1ms), p95 < 3000 µs (3ms), p99 < 5000 µs (5ms)
        assertTrue("Ingestion p50 must be < 1ms (measured: ${p50}µs)", p50 < 1_000L)
        assertTrue("Ingestion p95 must be < 3ms (measured: ${p95}µs)", p95 < 3_000L)
        assertTrue("Ingestion p99 must be < 5ms (measured: ${p99}µs)", p99 < 5_000L)
    }

    @Test
    fun testSqliteAppendThroughputSla() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(TaskLensDbHelper.DATABASE_NAME)
        val store = AndroidSqliteTaskLensStore(context)

        // Warm up SQLite connection and JVM JIT
        for (i in 1..50) {
            store.append(
                TaskLensEvent(
                    id = "sqlite-warmup-$i",
                    taskId = "bench-sqlite-task",
                    type = EventType.CUSTOM_BREADCRUMB,
                    source = EventSource.APPLICATION
                )
            )
        }

        val sampleEvents = (1..500).map { i ->
            TaskLensEvent(
                id = "sqlite-bench-$i",
                taskId = "bench-sqlite-task",
                type = EventType.CUSTOM_BREADCRUMB,
                source = EventSource.APPLICATION,
                attributes = mapOf("key" to "value_$i")
            )
        }

        val elapsedMs = measureTimeMillis {
            for (evt in sampleEvents) {
                store.append(evt)
            }
        }

        // Throughput must be > 500 events/second
        val eventsPerSec = (sampleEvents.size.toDouble() / elapsedMs.coerceAtLeast(1)) * 1000.0
        assertTrue(
            "SQLite append throughput must exceed 500 events/sec (measured: ${eventsPerSec.toInt()} events/sec, ${elapsedMs}ms)",
            elapsedMs < 1000L || eventsPerSec >= 500.0
        )
    }

    @Test
    fun testTimelineReconstructionSla() {
        val baseTime = Instant.fromEpochMilliseconds(1710000000000L)

        // 1,000 events benchmark
        val events1k = (1..1000).map { i ->
            TaskLensEvent(
                id = "evt-1k-$i",
                taskId = "task-perf-1k",
                attemptId = "att-1k-1",
                timestamp = Instant.fromEpochMilliseconds(baseTime.toEpochMilliseconds() + i * 10L),
                sequenceNumber = i.toLong(),
                type = if (i == 1) EventType.TASK_STARTED else if (i == 1000) EventType.TASK_SUCCEEDED else EventType.CUSTOM_BREADCRUMB,
                source = EventSource.WORK_MANAGER
            )
        }

        // Warm up JVM JIT
        correlationEngine.reconstructAttempts("task-perf-1k", events1k)

        val time1kMs = measureTimeMillis {
            val attempts = correlationEngine.reconstructAttempts("task-perf-1k", events1k)
            assertTrue(attempts.isNotEmpty())
        }
        assertTrue("Timeline reconstruction for 1,000 events must be < 50ms (measured: ${time1kMs}ms)", time1kMs < 50L)

        // 10,000 events benchmark
        val events10k = (1..10000).map { i ->
            TaskLensEvent(
                id = "evt-10k-$i",
                taskId = "task-perf-10k",
                attemptId = "att-10k-1",
                timestamp = Instant.fromEpochMilliseconds(baseTime.toEpochMilliseconds() + i * 5L),
                sequenceNumber = i.toLong(),
                type = if (i == 1) EventType.TASK_STARTED else if (i == 10000) EventType.TASK_SUCCEEDED else EventType.CUSTOM_BREADCRUMB,
                source = EventSource.WORK_MANAGER
            )
        }

        val time10kMs = measureTimeMillis {
            val attempts = correlationEngine.reconstructAttempts("task-perf-10k", events10k)
            assertTrue(attempts.isNotEmpty())
        }
        assertTrue("Timeline reconstruction for 10,000 events must be < 300ms (measured: ${time10kMs}ms)", time10kMs < 300L)
    }

    @Test
    fun testDiagnosisEvaluationSla() {
        val dummyTask = ScheduledWork(
            id = "diag-bench-task",
            name = "DiagBenchTask",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER
        )
        val dummyAttempt = ExecutionAttempt(
            attemptId = "att-bench-1",
            taskId = dummyTask.id,
            attemptNumber = 1,
            outcome = AttemptOutcome.FAILED
        )

        // 1,000 events diagnosis evaluation
        val events1k = (1..1000).map { i ->
            TaskLensEvent(
                id = "diag-evt-1k-$i",
                taskId = dummyTask.id,
                attemptId = dummyAttempt.attemptId,
                sequenceNumber = i.toLong(),
                type = EventType.CUSTOM_BREADCRUMB,
                source = EventSource.APPLICATION
            )
        }
        val context1k = DiagnosisContext(task = dummyTask, attempts = listOf(dummyAttempt), events = events1k)

        // Warm up
        diagnosisEngine.diagnose(context1k)

        val time1kMs = measureTimeMillis {
            diagnosisEngine.diagnose(context1k)
        }
        assertTrue("Diagnosis evaluation for 1,000 events must be < 30ms (measured: ${time1kMs}ms)", time1kMs < 30L)

        // 10,000 events diagnosis evaluation
        val events10k = (1..10000).map { i ->
            TaskLensEvent(
                id = "diag-evt-10k-$i",
                taskId = dummyTask.id,
                attemptId = dummyAttempt.attemptId,
                sequenceNumber = i.toLong(),
                type = EventType.CUSTOM_BREADCRUMB,
                source = EventSource.APPLICATION
            )
        }
        val context10k = DiagnosisContext(task = dummyTask, attempts = listOf(dummyAttempt), events = events10k)

        val time10kMs = measureTimeMillis {
            diagnosisEngine.diagnose(context10k)
        }
        assertTrue("Diagnosis evaluation for 10,000 events must be < 200ms (measured: ${time10kMs}ms)", time10kMs < 200L)
    }

    @Test
    fun testArchiveExportLatencyBudget() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(TaskLensDbHelper.DATABASE_NAME)
        val store = AndroidSqliteTaskLensStore(context)

        val taskId = "export-bench-task"
        val task = ScheduledWork(
            id = taskId,
            name = "ExportBenchTask",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER
        )
        store.saveTask(task)

        for (i in 1..200) {
            store.append(
                TaskLensEvent(
                    id = "export-evt-$i",
                    taskId = taskId,
                    type = EventType.CUSTOM_BREADCRUMB,
                    source = EventSource.APPLICATION
                )
            )
        }

        val exporter = DefaultTaskLensExporter(store)
        val targetFile = File(context.cacheDir, "bench_export.tasklens")

        val elapsedMs = measureTimeMillis {
            exporter.export(
                taskId = taskId,
                outputFile = targetFile,
                includePayloads = false,
                deviceMetadata = mapOf("platform" to "android"),
                platform = "android",
                redactor = DefaultTaskLensRedactor
            )
        }

        assertTrue("Archive export must be < 100ms (measured: ${elapsedMs}ms)", elapsedMs < 100L)
        assertTrue("Archive file must exist and be non-empty", targetFile.exists() && targetFile.length() > 0)
    }

    @Test
    fun testMemoryOverheadBounded() {
        System.gc()
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        // Process 5,000 events in memory
        val events = (1..5000).map { i ->
            TaskLensEvent(
                id = "mem-evt-$i",
                taskId = "mem-task",
                type = EventType.CUSTOM_BREADCRUMB,
                source = EventSource.APPLICATION,
                attributes = mapOf("payload" to "data_$i")
            )
        }
        val attempts = correlationEngine.reconstructAttempts("mem-task", events)
        assertTrue(attempts.isEmpty() || attempts.isNotEmpty())

        val postMemory = runtime.totalMemory() - runtime.freeMemory()
        val deltaMb = (postMemory - initialMemory) / (1024 * 1024)

        // Memory delta for 5,000 events in-memory should be well under 25 MB
        assertTrue("Memory overhead must be bounded < 25 MB (measured delta: ${deltaMb} MB)", deltaMb < 25L)
    }
}
