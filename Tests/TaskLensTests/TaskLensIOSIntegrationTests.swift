import XCTest
@testable import TaskLensCore
@testable import TaskLensStorage
@testable import TaskLensDiagnosis
@testable import TaskLensBGTasks
@testable import TaskLens

final class TaskLensIOSIntegrationTests: XCTestCase {

    var tempDbUrl: URL!

    override func setUp() {
        super.setUp()
        let tempDir = FileManager.default.temporaryDirectory
        tempDbUrl = tempDir.appendingPathComponent("test_\(UUID().uuidString).sqlite")
    }

    override func tearDown() {
        if let url = tempDbUrl {
            try? FileManager.default.removeItem(at: url)
        }
        super.tearDown()
    }

    func testExpirationSurvivesRestart() async throws {
        let identifier = "com.example.background.sync"
        let attemptId = UUID().uuidString

        // Phase 1: Initialize first store instance and record task lifecycle & expiration
        do {
            let store1 = SQLiteTaskLensStore(path: tempDbUrl.path)
            let task = ScheduledWork(
                id: identifier,
                name: "SyncWork",
                type: .bgProcessing,
                scheduler: .bgTaskScheduler,
                submittedAt: Date().addingTimeInterval(-60)
            )
            let attempt = ExecutionAttempt(
                attemptId: attemptId,
                taskId: identifier,
                attemptNumber: 1,
                startedAt: Date().addingTimeInterval(-30),
                endedAt: Date(),
                outcome: .failed
            )
            let submitEvent = TaskLensEvent(
                taskId: identifier,
                type: .taskSubmitted,
                source: .bgTaskScheduler
            )
            let launchEvent = TaskLensEvent(
                taskId: identifier,
                attemptId: attemptId,
                type: .taskLaunched,
                source: .bgTaskScheduler
            )
            let expireEvent = TaskLensEvent(
                taskId: identifier,
                attemptId: attemptId,
                type: .taskExpired,
                source: .bgTaskScheduler
            )
            let diagnosis = Diagnosis(
                id: "diag-1",
                taskId: identifier,
                attemptId: attemptId,
                ruleId: "RULE_TASK_EXPIRED",
                title: "Task Expired Before Completion",
                summary: "Quota expired",
                classification: .taskExpired,
                confidence: .confirmed
            )

            await store1.saveTask(task)
            await store1.saveAttempt(attempt)
            await store1.append(event: submitEvent)
            await store1.append(event: launchEvent)
            await store1.append(event: expireEvent)
            await store1.saveDiagnosis(diagnosis)
            await store1.close()
        }

        // Phase 2: Simulate App Process Restart — open a brand new SQLiteTaskLensStore on the same file
        let store2 = SQLiteTaskLensStore(path: tempDbUrl.path)

        // Verify task persisted across restart
        let loadedTask = await store2.task(id: identifier)
        XCTAssertNotNil(loadedTask, "Task must survive process restart")
        XCTAssertEqual(loadedTask?.name, "SyncWork")

        // Verify attempts persisted across restart
        let attempts = await store2.attempts(taskId: identifier)
        XCTAssertEqual(attempts.count, 1, "Attempt must survive process restart")
        XCTAssertEqual(attempts.first?.attemptId, attemptId)
        XCTAssertEqual(attempts.first?.outcome, .failed)

        // Verify events persisted in exact sequence
        let events = await store2.events(taskId: identifier)
        XCTAssertEqual(events.count, 3, "All 3 lifecycle events must survive process restart")
        XCTAssertEqual(events[0].type, .taskSubmitted)
        XCTAssertEqual(events[1].type, .taskLaunched)
        XCTAssertEqual(events[2].type, .taskExpired)
        XCTAssertTrue(events[0].sequenceNumber < events[1].sequenceNumber)
        XCTAssertTrue(events[1].sequenceNumber < events[2].sequenceNumber)

        // Verify diagnoses persisted across restart
        let diagnoses = await store2.diagnoses(taskId: identifier)
        XCTAssertEqual(diagnoses.count, 1, "Diagnosis must survive process restart")
        XCTAssertEqual(diagnoses.first?.classification, .taskExpired)
        XCTAssertEqual(diagnoses.first?.confidence, .confirmed)

        await store2.close()
    }

    func testBGTaskEndToEndLifecycleViaSimulation() async throws {
        let store = SQLiteTaskLensStore(path: tempDbUrl.path)
        TaskLens.install(customStore: store)

        let identifier = "com.example.refresh.task"
        let attemptId = UUID().uuidString

        // 1. Submit
        try TaskLensBG.submit(identifier: identifier, requiresNetwork: true, requiresExternalPower: false)

        // 2. Launch
        await TaskLensBG.simulateTaskLaunch(identifier: identifier, attemptId: attemptId)

        // 3. Expire
        await TaskLensBG.simulateTaskExpiration(identifier: identifier, attemptId: attemptId)

        let loadedTask = await store.task(id: identifier)
        XCTAssertNotNil(loadedTask)

        let attempts = await store.attempts(taskId: identifier)
        XCTAssertEqual(attempts.count, 1)
        XCTAssertEqual(attempts.first?.outcome, .failed)

        let events = await store.events(taskId: identifier)
        XCTAssertTrue(events.contains { $0.type == .taskSubmitted })
        XCTAssertTrue(events.contains { $0.type == .taskLaunched })
        XCTAssertTrue(events.contains { $0.type == .taskExpired })

        let diagnoses = await store.diagnoses(taskId: identifier)
        XCTAssertFalse(diagnoses.isEmpty)
        XCTAssertEqual(diagnoses.first?.classification, .taskExpired)

        await store.close()
    }

    func testExportGeneratesValidTaskLensZipArchive() async throws {
        let store = SQLiteTaskLensStore(path: tempDbUrl.path)
        TaskLens.install(customStore: store)

        let taskId = "com.example.export.task"
        let task = ScheduledWork(id: taskId, name: "ExportableTask", scheduler: .bgTaskScheduler)
        let event = TaskLensEvent(taskId: taskId, type: .taskSucceeded, source: .bgTaskScheduler)
        let diagnosis = Diagnosis(
            id: "diag-export",
            taskId: taskId,
            ruleId: "RULE_SUCCESS",
            title: "Task Succeeded",
            summary: "Completed successfully",
            classification: .unknown,
            confidence: .confirmed
        )

        await store.saveTask(task)
        await store.append(event: event)
        await store.saveDiagnosis(diagnosis)

        let archiveUrl = try await TaskLens.export(taskId: taskId)
        XCTAssertTrue(FileManager.default.fileExists(atPath: archiveUrl.path), "Export archive must exist on disk")
        XCTAssertTrue(archiveUrl.path.hasSuffix(".tasklens"), "Archive must have .tasklens extension")

        let data = try Data(contentsOf: archiveUrl)
        XCTAssertTrue(data.count > 100, "Archive must contain valid non-empty zip data")

        // Inspect standard ZIP signature: 0x04034b50 (PK\x03\x04)
        let magic = data.prefix(4)
        XCTAssertEqual(magic, Data([0x50, 0x4b, 0x03, 0x04]), "Export must be a valid ZIP archive starting with PK header")

        // Cleanup
        try? FileManager.default.removeItem(at: archiveUrl)
        await store.close()
    }

    func testSQLiteRetentionPolicy() async throws {
        let store = SQLiteTaskLensStore(path: tempDbUrl.path)

        for i in 1...5 {
            let id = "task-\(i)"
            let task = ScheduledWork(id: id, name: "Task \(i)", scheduler: .bgTaskScheduler, submittedAt: Date().addingTimeInterval(Double(i * 10)))
            let event = TaskLensEvent(taskId: id, type: .taskSubmitted, source: .bgTaskScheduler)
            await store.saveTask(task)
            await store.append(event: event)
        }

        var allTasks = await store.tasks(query: TaskQuery(limit: 10))
        XCTAssertEqual(allTasks.count, 5)

        // Apply retention: keep max 2 tasks
        await store.applyRetention(.maxTasks(2))

        allTasks = await store.tasks(query: TaskQuery(limit: 10))
        XCTAssertEqual(allTasks.count, 2, "Retention policy must prune older tasks and keep only the latest 2")
        XCTAssertTrue(allTasks.contains { $0.id == "task-5" })
        XCTAssertTrue(allTasks.contains { $0.id == "task-4" })

        await store.close()
    }
}
