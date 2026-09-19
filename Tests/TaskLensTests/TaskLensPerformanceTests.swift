import XCTest
import Foundation
@testable import TaskLensCore
@testable import TaskLensDiagnosis
@testable import TaskLensStorage
@testable import TaskLens

final class TaskLensPerformanceTests: XCTestCase {

    func testInstallLatencyBudget() {
        let start = CFAbsoluteTimeGetCurrent()
        TaskLens.install()
        let elapsedMs = (CFAbsoluteTimeGetCurrent() - start) * 1000.0
        XCTAssertLessThan(elapsedMs, 50.0, "Install latency must be < 50ms (measured: \(elapsedMs)ms)")
    }

    func testEventIngestionLatencyBudget() async {
        let count = 1000
        var latenciesMicros: [Double] = []
        latenciesMicros.reserveCapacity(count)

        // Warmup
        for i in 0..<50 {
            let evt = TaskLensEvent(
                id: "warmup-\(i)",
                taskId: "bench-task",
                sequenceNumber: Int64(i),
                type: .customBreadcrumb,
                source: .application
            )
            await TaskLens.emit(event: evt)
        }

        // Measure individual emit
        for i in 0..<count {
            let evt = TaskLensEvent(
                id: "bench-evt-\(i)",
                taskId: "bench-task",
                sequenceNumber: Int64(i + 100),
                type: .customBreadcrumb,
                source: .application,
                attributes: ["iter": "\(i)"]
            )
            let start = CFAbsoluteTimeGetCurrent()
            await TaskLens.emit(event: evt)
            let elapsedMicros = (CFAbsoluteTimeGetCurrent() - start) * 1_000_000.0
            latenciesMicros.append(elapsedMicros)
        }

        latenciesMicros.sort()
        let p50 = latenciesMicros[Int(Double(count) * 0.50)]
        let p95 = latenciesMicros[Int(Double(count) * 0.95)]
        let p99 = latenciesMicros[Int(Double(count) * 0.99)]

        // Enforce budgets: p50 < 1000µs (1ms), p95 < 3000µs (3ms), p99 < 5000µs (5ms)
        XCTAssertLessThan(p50, 1000.0, "Ingestion p50 must be < 1ms (measured: \(p50)µs)")
        XCTAssertLessThan(p95, 3000.0, "Ingestion p95 must be < 3ms (measured: \(p95)µs)")
        XCTAssertLessThan(p99, 5000.0, "Ingestion p99 must be < 5ms (measured: \(p99)µs)")
    }

    func testSqliteAppendThroughputBudget() async {
        let store = SQLiteTaskLensStore()
        let count = 500

        // Warmup
        for i in 0..<20 {
            let evt = TaskLensEvent(
                id: "warmup-\(i)",
                taskId: "bench-sqlite-task",
                sequenceNumber: Int64(i),
                type: .customBreadcrumb,
                source: .application
            )
            await store.append(event: evt)
        }

        let start = CFAbsoluteTimeGetCurrent()
        for i in 0..<count {
            let evt = TaskLensEvent(
                id: "sqlite-bench-\(i)",
                taskId: "bench-sqlite-task",
                sequenceNumber: Int64(i + 50),
                type: .customBreadcrumb,
                source: .application
            )
            await store.append(event: evt)
        }
        let elapsedMs = (CFAbsoluteTimeGetCurrent() - start) * 1000.0
        let eventsPerSec = (Double(count) / max(elapsedMs, 1.0)) * 1000.0

        XCTAssertTrue(
            elapsedMs < 1000.0 || eventsPerSec >= 500.0,
            "SQLite append throughput must exceed 500 events/sec (measured: \(Int(eventsPerSec)) events/sec, \(elapsedMs)ms)"
        )
    }

    func testDiagnosisEvaluation1kBudget() {
        let dummyTask = ScheduledWork(
            id: "diag-swift-task",
            name: "DiagSwiftTask",
            type: .worker,
            scheduler: .bgTaskScheduler
        )
        let dummyAttempt = ExecutionAttempt(
            attemptId: "att-swift-1",
            taskId: dummyTask.id,
            attemptNumber: 1,
            outcome: .failed
        )

        var events1k: [TaskLensEvent] = []
        events1k.reserveCapacity(1000)
        for i in 1...1000 {
            events1k.append(
                TaskLensEvent(
                    id: "diag-evt-\(i)",
                    taskId: dummyTask.id,
                    attemptId: dummyAttempt.attemptId,
                    sequenceNumber: Int64(i),
                    type: .customBreadcrumb,
                    source: .application
                )
            )
        }

        let context = DiagnosisContext(task: dummyTask, attempts: [dummyAttempt], events: events1k)
        let engine = DefaultDiagnosisEngine()

        // Warmup
        let _ = engine.diagnose(context: context)

        let start = CFAbsoluteTimeGetCurrent()
        let diagnoses = engine.diagnose(context: context)
        let elapsedMs = (CFAbsoluteTimeGetCurrent() - start) * 1000.0

        XCTAssertFalse(diagnoses.isEmpty)
        XCTAssertLessThan(elapsedMs, 30.0, "Diagnosis evaluation for 1k events must be < 30ms (measured: \(elapsedMs)ms)")
    }

    func testArchiveExportLatencyBudget() async throws {
        let store = SQLiteTaskLensStore()
        let taskId = "export-swift-task"
        let task = ScheduledWork(
            id: taskId,
            name: "ExportSwiftTask",
            type: .worker,
            scheduler: .bgTaskScheduler
        )
        await store.saveTask(task)

        for i in 1...200 {
            let evt = TaskLensEvent(
                id: "export-evt-\(i)",
                taskId: taskId,
                sequenceNumber: Int64(i),
                type: .customBreadcrumb,
                source: .application
            )
            await store.append(event: evt)
        }

        let start = CFAbsoluteTimeGetCurrent()
        let archiveUrl = try await TaskLensArchiveExporter.createArchive(
            taskId: taskId,
            store: store,
            redactor: DefaultTaskLensRedactor.shared
        )
        let elapsedMs = (CFAbsoluteTimeGetCurrent() - start) * 1000.0

        XCTAssertLessThan(elapsedMs, 100.0, "Archive export must be < 100ms (measured: \(elapsedMs)ms)")
        XCTAssertTrue(FileManager.default.fileExists(atPath: archiveUrl.path))
    }
}
