import XCTest
@testable import TaskLensCore
@testable import TaskLensDiagnosis
@testable import TaskLensStorage
@testable import TaskLens

final class TaskLensTests: XCTestCase {

    func testEventCreationAndSequence() {
        let event1 = TaskLensEvent(
            taskId: "ios-task-1",
            type: .taskSubmitted,
            source: .bgTaskScheduler
        )
        let event2 = TaskLensEvent(
            taskId: "ios-task-1",
            type: .taskLaunched,
            source: .bgTaskScheduler
        )

        XCTAssertEqual(event1.taskId, "ios-task-1")
        XCTAssertEqual(event1.type, .taskSubmitted)
        XCTAssertTrue(event2.sequenceNumber > event1.sequenceNumber)
    }

    func testRedactor() {
        let redactor = DefaultTaskLensRedactor.shared
        XCTAssertEqual(redactor.redact(key: "authorization", value: "Bearer secret-token"), "[REDACTED]")
        XCTAssertEqual(redactor.redact(key: "apiKey", value: "12345"), "[REDACTED]")
        XCTAssertEqual(redactor.redact(key: "worker_name", value: "RefreshTask"), "RefreshTask")
    }

    func testBGTaskExpiredDiagnosis() {
        let task = ScheduledWork(id: "com.example.refresh", name: "Refresh", type: .bgAppRefresh, scheduler: .bgTaskScheduler)
        let expireEvent = TaskLensEvent(
            taskId: "com.example.refresh",
            type: .taskExpired,
            source: .bgTaskScheduler
        )

        let context = DiagnosisContext(
            task: task,
            attempts: [],
            events: [expireEvent]
        )

        let engine = DefaultDiagnosisEngine()
        let diagnoses = engine.diagnose(context: context)
        XCTAssertFalse(diagnoses.isEmpty)
        let diagnosis = diagnoses.first!
        XCTAssertEqual(diagnosis.classification, .taskExpired)
        XCTAssertEqual(diagnosis.confidence, .confirmed)
    }

    func testCapabilityMismatchDiagnosis() {
        let task = ScheduledWork(
            id: "com.example.unregistered",
            metadata: ["config_error": "Missing from BGTaskSchedulerPermittedIdentifiers"]
        )
        let context = DiagnosisContext(task: task)
        let engine = DefaultDiagnosisEngine()
        let diagnoses = engine.diagnose(context: context)
        guard let first = diagnoses.first else {
            XCTFail("Expected diagnosis candidate")
            return
        }
        XCTAssertEqual(first.classification, .configurationProblem)
        XCTAssertEqual(first.confidence, .confirmed)
    }

    func testStoreAppendAndQuery() async {
        let store = InMemoryTaskLensStore()
        let task = ScheduledWork(id: "task-swift", name: "SwiftTask", scheduler: .bgTaskScheduler)
        await store.saveTask(task)

        let fetched = await store.task(id: "task-swift")
        XCTAssertNotNil(fetched)
        XCTAssertEqual(fetched?.name, "SwiftTask")

        let event = TaskLensEvent(taskId: "task-swift", type: .taskStarted, source: .bgTaskScheduler)
        await store.append(event: event)

        let events = await store.events(taskId: "task-swift")
        XCTAssertEqual(events.count, 1)
        XCTAssertEqual(events.first?.type, .taskStarted)
    }
}
