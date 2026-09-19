import XCTest
import Foundation
@testable import TaskLensCore
@testable import TaskLensDiagnosis
@testable import TaskLensBGTasks
@testable import TaskLensURLSession
@testable import TaskLens

final class SwiftApiCompatibilityTests: XCTestCase {

    func testSwiftApiBaselineFileMatchesContract() throws {
        // Find baseline file
        let possiblePaths = [
            "api/tasklens-swift.api",
            "../api/tasklens-swift.api",
            "../../api/tasklens-swift.api"
        ]
        var baselinePath: String?
        for path in possiblePaths {
            if FileManager.default.fileExists(atPath: path) {
                baselinePath = path
                break
            }
        }
        
        guard let path = baselinePath else {
            XCTFail("Could not locate api/tasklens-swift.api baseline file")
            return
        }

        let content = try String(contentsOfFile: path, encoding: .utf8)
        XCTAssertTrue(content.contains("TaskLens"), "Must contain TaskLens facade")
        XCTAssertTrue(content.contains("TaskLensBG"), "Must contain TaskLensBG")
        XCTAssertTrue(content.contains("TaskLensURLSession"), "Must contain TaskLensURLSession")
        XCTAssertTrue(content.contains("func install(config: TaskLensConfig)"), "Must document install")
        XCTAssertTrue(content.contains("func isInstalled() -> Bool"), "Must document isInstalled")
        XCTAssertTrue(content.contains("func emit(event: TaskLensEvent) async"), "Must document emit")
        XCTAssertTrue(content.contains("func breadcrumb(title: String, message: String, taskId: String?, attributes: [String: String]) async"), "Must document breadcrumb")
        XCTAssertTrue(content.contains("func export(taskId: String) async throws -> URL"), "Must document export")
        XCTAssertTrue(content.contains("wrapExpirationHandler"), "Must document wrapExpirationHandler")
        XCTAssertTrue(content.contains("wrapCompletion"), "Must document wrapCompletion")
        XCTAssertTrue(content.contains("isLowPowerModeEnabled"), "Must document isLowPowerModeEnabled")
    }

    func testCompileTimeApiContractVerification() async throws {
        // Compile-time assertion that public API methods have exact signatures expected
        let _ = TaskLens.isInstalled()
        let _ = TaskLens.getConfig()
        
        let dummyEvent = TaskLensEvent(
            id: UUID().uuidString,
            taskId: "test-task",
            attemptId: nil,
            timestamp: Date(),
            sequenceNumber: 1,
            type: .taskSubmitted,
            source: .application,
            severity: .info,
            attributes: [:]
        )
        await TaskLens.emit(event: dummyEvent)
        await TaskLens.breadcrumb(title: "b", message: "m", taskId: "t", attributes: [:])
        TaskLens.show()
        TaskLens.hide()
        await TaskLens.clear()
        
        // TaskLensBG static API verification
        let _ = TaskLensBG.isLowPowerModeEnabled
        let dummyHandler: (@Sendable () -> Void)? = { }
        let wrappedHandler = TaskLensBG.wrapExpirationHandler(
            taskIdentifier: "com.example.task",
            attemptId: "att-1",
            userHandler: dummyHandler
        )
        XCTAssertNotNil(wrappedHandler)

        let dummyCompletion: (@Sendable (Bool) -> Void)? = { _ in }
        TaskLensBG.wrapCompletion(
            taskIdentifier: "com.example.task",
            attemptId: "att-1",
            success: true,
            onComplete: dummyCompletion
        )

        let missing = TaskLensBG.validateCapabilities(
            registeredIdentifiers: ["task1"],
            infoPlistIdentifiers: ["task1"]
        )
        XCTAssertEqual(missing.count, 0)
    }
}
