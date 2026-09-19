import XCTest
@testable import TaskLensCore
@testable import TaskLensBGTasks
@testable import TaskLensURLSession
@testable import TaskLensStorage
@testable import TaskLens

final class TaskLensRealHostIntegrationTests: XCTestCase {

    override func setUp() async throws {
        TaskLensBGBridge.eventSink = nil
        TaskLensBGBridge.taskSink = nil
        TaskLensBGBridge.attemptSink = nil
        TaskLensBGBridge.diagnosisSink = nil
    }

    func testRequestConstruction() {
        let date = Date().addingTimeInterval(900)
        let req = TaskLensBGRequest(
            identifier: "dev.shushant.tasklens.dbcleanup",
            earliestBeginDate: date,
            requiresNetworkConnectivity: true,
            requiresExternalPower: true,
            isProcessing: true
        )

        XCTAssertEqual(req.identifier, "dev.shushant.tasklens.dbcleanup")
        XCTAssertEqual(req.earliestBeginDate, date)
        XCTAssertTrue(req.requiresNetworkConnectivity)
        XCTAssertTrue(req.requiresExternalPower)
        XCTAssertTrue(req.isProcessing)
    }

    func testPermittedIdentifierValidationFlagsMissingEntries() {
        let registered: Set<String> = [
            "dev.shushant.tasklens.refresh",
            "dev.shushant.tasklens.sync",
            "dev.shushant.tasklens.unregistered"
        ]
        let infoPlist: Set<String> = [
            "dev.shushant.tasklens.refresh",
            "dev.shushant.tasklens.sync"
        ]

        let errors = TaskLensBG.validateCapabilities(
            registeredIdentifiers: registered,
            infoPlistIdentifiers: infoPlist
        )

        XCTAssertEqual(errors.count, 1)
        XCTAssertTrue(errors.first!.contains("dev.shushant.tasklens.unregistered"))
        XCTAssertTrue(errors.first!.contains("missing from BGTaskSchedulerPermittedIdentifiers"))
    }

    private final class SafeBox<T>: @unchecked Sendable {
        var value: T?
        init(_ value: T? = nil) { self.value = value }
    }

    func testExpirationHandlerPreservationExecutesBothTelemetryAndUserBlock() {
        let userHandlerInvoked = SafeBox(false)
        let recordedEvent = SafeBox<TaskLensEvent>(nil)

        let expectation = self.expectation(description: "Event recorded")

        TaskLensBGBridge.eventSink = { event in
            recordedEvent.value = event
            expectation.fulfill()
        }

        let wrapped = TaskLensBG.wrapExpirationHandler(
            taskIdentifier: "dev.shushant.tasklens.refresh",
            attemptId: "att-test-1",
            userHandler: {
                userHandlerInvoked.value = true
            }
        )

        wrapped()

        wait(for: [expectation], timeout: 2.0)

        XCTAssertEqual(userHandlerInvoked.value, true, "Host application expiration handler must be executed")
        XCTAssertNotNil(recordedEvent.value)
        XCTAssertEqual(recordedEvent.value?.type, .taskExpired)
        XCTAssertEqual(recordedEvent.value?.taskId, "dev.shushant.tasklens.refresh")
    }

    func testCompletionWrapperPreservationEmitsTelemetryAndCallsHostBlock() {
        let hostBlockResult = SafeBox<Bool>(nil)
        let recordedEvent = SafeBox<TaskLensEvent>(nil)

        let expectation = self.expectation(description: "Completion recorded")

        TaskLensBGBridge.eventSink = { event in
            recordedEvent.value = event
            expectation.fulfill()
        }

        TaskLensBG.wrapCompletion(
            taskIdentifier: "dev.shushant.tasklens.sync",
            attemptId: "att-test-2",
            success: true,
            onComplete: { success in
                hostBlockResult.value = success
            }
        )

        wait(for: [expectation], timeout: 2.0)

        XCTAssertEqual(hostBlockResult.value, true)
        XCTAssertNotNil(recordedEvent.value)
        XCTAssertEqual(recordedEvent.value?.type, .taskSucceeded)
        XCTAssertEqual(recordedEvent.value?.taskId, "dev.shushant.tasklens.sync")
    }

    func testLowPowerModeDetection() {
        // Reads ProcessInfo without crashing
        let lpm = TaskLensBG.isLowPowerModeEnabled
        XCTAssertTrue(lpm == true || lpm == false)
    }

    func testBackgroundURLSessionWiring() {
        var receivedEvents: [TaskLensEvent] = []
        let proxy = TaskLensURLSessionProxy(
            sessionIdentifier: "test-bg-session",
            actualDelegate: nil,
            eventSink: { event in
                receivedEvents.append(event)
            }
        )

        // Simulate failed task
        let session = URLSession.shared
        let task = session.dataTask(with: URL(string: "https://example.com/test")!)
        let mockError = NSError(domain: NSURLErrorDomain, code: NSURLErrorTimedOut, userInfo: [
            NSLocalizedDescriptionKey: "The request timed out."
        ])

        proxy.urlSession(session, task: task, didCompleteWithError: mockError)

        XCTAssertEqual(receivedEvents.count, 1)
        let event = receivedEvents.first!
        XCTAssertEqual(event.type, .urlSessionTransferFailed)
        XCTAssertEqual(event.attributes["session"], "test-bg-session")
        XCTAssertEqual(event.attributes["error"], "The request timed out.")
    }
}
