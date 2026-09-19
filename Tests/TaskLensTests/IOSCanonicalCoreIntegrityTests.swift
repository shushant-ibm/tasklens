import XCTest
@testable import TaskLensCore
@testable import TaskLensDiagnosis

/**
 * IOSCanonicalCoreIntegrityTests
 *
 * Enforces architectural compliance that KMP is the single source of truth for:
 * - Domain model contracts
 * - Serialization schema
 * - Event types, sources, and severities
 * - Diagnosis rule IDs and classification values
 * - Redactor sensitive key patterns
 *
 * If duplicate or divergent business logic is reintroduced to the Swift codebase,
 * this test suite will fail.
 */
final class IOSCanonicalCoreIntegrityTests: XCTestCase {

    /// Canonical rule IDs defined in KMP (tasklens-core-kmp/.../rules/Rules.kt)
    private let canonicalKmpRuleIds: Set<String> = [
        "RULE_TASK_EXPIRED",
        "RULE_APPLICATION_FAILURE",
        "RULE_CAPABILITY_MISMATCH",
        "RULE_SCHEDULER_DELAY",
        "RULE_PLATFORM_STOPPED",
        "RULE_NETWORK_CONSTRAINT_BLOCKED",
        "RULE_NETWORK_INTERRUPTION",
        "RULE_APPLICATION_CANCELLED",
        "RULE_RETRY_REQUESTED"
    ]

    func testNoRogueRulesInSwiftDiagnosisEngine() {
        // Ensure default rules strictly use canonical IDs
        let rules: [DiagnosisRule] = [
            BGTaskExpiredRule(),
            BGTaskFailureRule(),
            CapabilityMismatchRule(),
            SchedulerDelayRule()
        ]

        for rule in rules {
            XCTAssertTrue(
                canonicalKmpRuleIds.contains(rule.id),
                "Swift rule ID '\(rule.id)' is not registered in canonical KMP rules list! Swift must not maintain divergent diagnosis rules."
            )
        }
    }

    func testEventEnumParityWithKmp() {
        // Assert event types have exact uppercase serialization values matching KMP
        XCTAssertEqual(EventType.taskRegistered.rawValue, "TASK_REGISTERED")
        XCTAssertEqual(EventType.taskSubmitted.rawValue, "TASK_SUBMITTED")
        XCTAssertEqual(EventType.taskLaunched.rawValue, "TASK_LAUNCHED")
        XCTAssertEqual(EventType.taskStarted.rawValue, "TASK_STARTED")
        XCTAssertEqual(EventType.taskStopped.rawValue, "TASK_STOPPED")
        XCTAssertEqual(EventType.taskExpired.rawValue, "TASK_EXPIRED")
        XCTAssertEqual(EventType.taskSucceeded.rawValue, "TASK_SUCCEEDED")
        XCTAssertEqual(EventType.taskFailed.rawValue, "TASK_FAILED")
        XCTAssertEqual(EventType.taskCancelled.rawValue, "TASK_CANCELLED")
        XCTAssertEqual(EventType.networkChanged.rawValue, "NETWORK_CHANGED")
        XCTAssertEqual(EventType.batteryChanged.rawValue, "BATTERY_CHANGED")

        // Assert event sources match KMP
        XCTAssertEqual(EventSource.bgTaskScheduler.rawValue, "BG_TASK_SCHEDULER")
        XCTAssertEqual(EventSource.workManager.rawValue, "WORK_MANAGER")
        XCTAssertEqual(EventSource.urlSession.rawValue, "URL_SESSION")
        XCTAssertEqual(EventSource.application.rawValue, "APPLICATION")

        // Assert attempt outcomes match KMP
        XCTAssertEqual(AttemptOutcome.success.rawValue, "SUCCESS")
        XCTAssertEqual(AttemptOutcome.failed.rawValue, "FAILED")
        XCTAssertEqual(AttemptOutcome.stopped.rawValue, "STOPPED")
        XCTAssertEqual(AttemptOutcome.expired.rawValue, "EXPIRED")
        XCTAssertEqual(AttemptOutcome.cancelled.rawValue, "CANCELLED")
    }

    func testDiagnosisClassificationParityWithKmp() {
        XCTAssertEqual(DiagnosisClassification.taskExpired.rawValue, "TASK_EXPIRED")
        XCTAssertEqual(DiagnosisClassification.applicationFailure.rawValue, "APPLICATION_FAILURE")
        XCTAssertEqual(DiagnosisClassification.schedulerDelay.rawValue, "SCHEDULER_DELAY")
        XCTAssertEqual(DiagnosisClassification.configurationProblem.rawValue, "CONFIGURATION_PROBLEM")
        XCTAssertEqual(DiagnosisClassification.waitingOnConstraint.rawValue, "WAITING_ON_CONSTRAINT")
        XCTAssertEqual(DiagnosisClassification.platformStopped.rawValue, "PLATFORM_STOPPED")
        XCTAssertEqual(DiagnosisClassification.networkInterruption.rawValue, "NETWORK_INTERRUPTION")
    }

    func testCanonicalModelJsonSchemaEncoding() throws {
        let task = ScheduledWork(
            id: "canonical-task-1",
            platformId: nil,
            name: "TestSync",
            type: .bgProcessing,
            scheduler: .bgTaskScheduler,
            submittedAt: nil,
            earliestBeginAt: nil,
            periodic: true,
            metadata: ["key": "val"]
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(task)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(json["id"] as? String, "canonical-task-1")
        XCTAssertEqual(json["name"] as? String, "TestSync")
        XCTAssertEqual(json["type"] as? String, "BG_PROCESSING")
        XCTAssertEqual(json["scheduler"] as? String, "BG_TASK_SCHEDULER")
        XCTAssertEqual(json["periodic"] as? Bool, true)
    }

    func testRedactorParityWithCanonicalKmpRedactor() {
        let redactor = DefaultTaskLensRedactor.shared

        // Keys that MUST be redacted per canonical specification
        let sensitiveKeys = [
            "password", "auth_token", "Authorization", "api_key", "secret",
            "access_token", "private_key", "credentials", "session_id", "bearer"
        ]

        for key in sensitiveKeys {
            let result = redactor.redact(key: key, value: "super-sensitive-12345")
            XCTAssertEqual(result, "[REDACTED]", "Key '\(key)' must be redacted by DefaultTaskLensRedactor")
        }

        // Non-sensitive keys must NOT be redacted
        XCTAssertEqual(redactor.redact(key: "task_name", value: "SyncWorker"), "SyncWorker")
        XCTAssertEqual(redactor.redact(key: "attempt_count", value: "3"), "3")
    }
}
