import Foundation
import TaskLensCore
#if os(iOS) || os(tvOS)
import BackgroundTasks
#endif

public enum TaskLensBGBridge {
    public static var eventSink: (@Sendable (TaskLensEvent) async -> Void)?
    public static var taskSink: (@Sendable (ScheduledWork) async -> Void)?
    public static var attemptSink: (@Sendable (ExecutionAttempt) async -> Void)?
    public static var diagnosisSink: (@Sendable (Diagnosis) async -> Void)?
}

public enum TaskLensBG {

    public static func register(
        identifier: String,
        launchHandler: @escaping (Any) -> Void
    ) {
        let registerEvent = TaskLensEvent(
            taskId: identifier,
            type: .taskRegistered,
            source: .bgTaskScheduler,
            attributes: ["identifier": identifier]
        )
        Task {
            await TaskLensBGBridge.eventSink?(registerEvent)
        }

        #if os(iOS) || os(tvOS)
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            let attemptId = UUID().uuidString
            let isLowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
            let attrs: [String: String] = [
                "identifier": identifier,
                "attempt_id": attemptId,
                "low_power_mode": isLowPower ? "true" : "false"
            ]

            let launchEvent = TaskLensEvent(
                taskId: identifier,
                attemptId: attemptId,
                type: .taskLaunched,
                source: .bgTaskScheduler,
                attributes: attrs
            )

            let attempt = ExecutionAttempt(
                attemptId: attemptId,
                taskId: identifier,
                attemptNumber: 1,
                startedAt: Date(),
                outcome: nil
            )

            let scheduledWork = ScheduledWork(
                id: identifier,
                name: identifier,
                type: .bgProcessing,
                scheduler: .bgTaskScheduler,
                submittedAt: Date()
            )

            Task {
                await TaskLensBGBridge.taskSink?(scheduledWork)
                await TaskLensBGBridge.attemptSink?(attempt)
                await TaskLensBGBridge.eventSink?(launchEvent)
            }

            if let bgTask = task as? BGTask {
                bgTask.expirationHandler = {
                    let expireEvent = TaskLensEvent(
                        taskId: identifier,
                        attemptId: attemptId,
                        type: .taskExpired,
                        source: .bgTaskScheduler,
                        attributes: ["identifier": identifier]
                    )
                    let expiredAttempt = ExecutionAttempt(
                        attemptId: attemptId,
                        taskId: identifier,
                        attemptNumber: 1,
                        startedAt: attempt.startedAt,
                        endedAt: Date(),
                        outcome: .failed
                    )
                    let diagnosis = Diagnosis(
                        id: UUID().uuidString,
                        taskId: identifier,
                        attemptId: attemptId,
                        ruleId: "RULE_TASK_EXPIRED",
                        title: "Task Expired Before Completion",
                        summary: "The operating system background execution quota expired before the task completed.",
                        classification: .taskExpired,
                        confidence: .confirmed,
                        evidence: [
                            Evidence(
                                type: .expirationHandler,
                                source: .platform,
                                timestamp: Date(),
                                title: "Task Expiration Handler Invoked",
                                description: "OS background runtime quota expired"
                            )
                        ],
                        limitations: [
                            PlatformLimitation(
                                code: "BG_TASK_MAX_EXECUTION_TIME",
                                message: "Operating systems impose strict background execution time caps."
                            )
                        ]
                    )

                    Task {
                        await TaskLensBGBridge.eventSink?(expireEvent)
                        await TaskLensBGBridge.attemptSink?(expiredAttempt)
                        await TaskLensBGBridge.diagnosisSink?(diagnosis)
                    }
                }
            }

            launchHandler(task)
        }
        #endif
    }

    public static func submit(
        identifier: String,
        earliestBeginDate: Date? = nil,
        requiresNetwork: Bool = false,
        requiresExternalPower: Bool = false
    ) throws {
        var attrs: [String: String] = [
            "identifier": identifier,
            "requires_network": requiresNetwork ? "true" : "false",
            "requires_power": requiresExternalPower ? "true" : "false"
        ]
        if let begin = earliestBeginDate {
            attrs["earliest_begin_epoch_ms"] = String(Int64(begin.timeIntervalSince1970 * 1000))
        }

        let submitEvent = TaskLensEvent(
            taskId: identifier,
            type: .taskSubmitted,
            source: .bgTaskScheduler,
            attributes: attrs
        )

        Task {
            await TaskLensBGBridge.eventSink?(submitEvent)
        }

        #if os(iOS) || os(tvOS)
        let request = BGProcessingTaskRequest(identifier: identifier)
        request.earliestBeginDate = earliestBeginDate
        request.requiresNetworkConnectivity = requiresNetwork
        request.requiresExternalPower = requiresExternalPower
        try BGTaskScheduler.shared.submit(request)
        #endif
    }

    public static func complete(
        task: Any,
        identifier: String,
        attemptId: String? = nil,
        success: Bool
    ) {
        let eventType: EventType = success ? .taskSucceeded : .taskFailed
        let event = TaskLensEvent(
            taskId: identifier,
            attemptId: attemptId,
            type: eventType,
            source: .bgTaskScheduler,
            attributes: ["success": success ? "true" : "false"]
        )

        Task {
            await TaskLensBGBridge.eventSink?(event)
        }

        #if os(iOS) || os(tvOS)
        if let bgTask = task as? BGTask {
            bgTask.setTaskCompleted(success: success)
        }
        #endif
    }

    public static func validateCapabilities(
        registeredIdentifiers: Set<String>,
        infoPlistIdentifiers: Set<String>
    ) -> [String] {
        var errors: [String] = []
        for id in registeredIdentifiers {
            if !infoPlistIdentifiers.contains(id) {
                errors.append("Task identifier '\(id)' was registered but is missing from BGTaskSchedulerPermittedIdentifiers in Info.plist")
            }
        }
        return errors
    }

    // MARK: - Simulation APIs for Automated Testing

    /// Deterministically simulates a BGTask launch, storing the task and initial attempt in SQLite.
    public static func simulateTaskLaunch(
        identifier: String,
        attemptId: String = UUID().uuidString
    ) async {
        let task = ScheduledWork(
            id: identifier,
            name: identifier,
            type: .bgProcessing,
            scheduler: .bgTaskScheduler,
            submittedAt: Date()
        )
        let attempt = ExecutionAttempt(
            attemptId: attemptId,
            taskId: identifier,
            attemptNumber: 1,
            startedAt: Date(),
            outcome: nil
        )
        let launchEvent = TaskLensEvent(
            taskId: identifier,
            attemptId: attemptId,
            type: .taskLaunched,
            source: .bgTaskScheduler,
            attributes: ["identifier": identifier]
        )

        await TaskLensBGBridge.taskSink?(task)
        await TaskLensBGBridge.attemptSink?(attempt)
        await TaskLensBGBridge.eventSink?(launchEvent)
    }

    /// Deterministically simulates task expiration, recording the expiration event and diagnosis.
    public static func simulateTaskExpiration(
        identifier: String,
        attemptId: String
    ) async {
        let expireEvent = TaskLensEvent(
            taskId: identifier,
            attemptId: attemptId,
            type: .taskExpired,
            source: .bgTaskScheduler,
            attributes: ["identifier": identifier]
        )
        let expiredAttempt = ExecutionAttempt(
            attemptId: attemptId,
            taskId: identifier,
            attemptNumber: 1,
            startedAt: Date().addingTimeInterval(-30),
            endedAt: Date(),
            outcome: .failed
        )
        let diagnosis = Diagnosis(
            id: UUID().uuidString,
            taskId: identifier,
            attemptId: attemptId,
            ruleId: "RULE_TASK_EXPIRED",
            title: "Task Expired Before Completion",
            summary: "The operating system background execution quota expired before the task completed.",
            classification: .taskExpired,
            confidence: .confirmed,
            evidence: [
                Evidence(
                    type: .expirationHandler,
                    source: .platform,
                    timestamp: Date(),
                    title: "Task Expiration Handler Invoked",
                    description: "OS background runtime quota expired"
                )
            ],
            limitations: [
                PlatformLimitation(
                    code: "BG_TASK_MAX_EXECUTION_TIME",
                    message: "Operating systems impose strict background execution time caps."
                )
            ]
        )

        await TaskLensBGBridge.eventSink?(expireEvent)
        await TaskLensBGBridge.attemptSink?(expiredAttempt)
        await TaskLensBGBridge.diagnosisSink?(diagnosis)
    }

    // MARK: - Host Integration Utilities

    /// Checks if device is currently in Low Power Mode
    public static var isLowPowerModeEnabled: Bool {
        ProcessInfo.processInfo.isLowPowerModeEnabled
    }

    /// Preserves both TaskLens telemetry and the host app's expiration handler
    public static func wrapExpirationHandler(
        taskIdentifier: String,
        attemptId: String,
        userHandler: (@Sendable () -> Void)?
    ) -> (@Sendable () -> Void) {
        return {
            Task {
                let expireEvent = TaskLensEvent(
                    taskId: taskIdentifier,
                    attemptId: attemptId,
                    type: .taskExpired,
                    source: .bgTaskScheduler
                )
                await TaskLensBGBridge.eventSink?(expireEvent)
            }
            userHandler?()
        }
    }

    /// Preserves completion telemetry and executes host completion block
    public static func wrapCompletion(
        taskIdentifier: String,
        attemptId: String,
        success: Bool,
        onComplete: (@Sendable (Bool) -> Void)? = nil
    ) {
        Task {
            let event = TaskLensEvent(
                taskId: taskIdentifier,
                attemptId: attemptId,
                type: success ? .taskSucceeded : .taskFailed,
                source: .bgTaskScheduler,
                attributes: ["success": String(success)]
            )
            await TaskLensBGBridge.eventSink?(event)
        }
        onComplete?(success)
    }
}

/// Portable specification for BGTask requests
public struct TaskLensBGRequest {
    public let identifier: String
    public let earliestBeginDate: Date?
    public let requiresNetworkConnectivity: Bool
    public let requiresExternalPower: Bool
    public let isProcessing: Bool

    public init(
        identifier: String,
        earliestBeginDate: Date? = nil,
        requiresNetworkConnectivity: Bool = false,
        requiresExternalPower: Bool = false,
        isProcessing: Bool = true
    ) {
        self.identifier = identifier
        self.earliestBeginDate = earliestBeginDate
        self.requiresNetworkConnectivity = requiresNetworkConnectivity
        self.requiresExternalPower = requiresExternalPower
        self.isProcessing = isProcessing
    }
}
