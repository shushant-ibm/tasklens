import Foundation
import TaskLensCore

public struct BGTaskExpiredRule: DiagnosisRule {
    public let id = "RULE_TASK_EXPIRED"
    public let name = "BGTask Expired"

    public init() {}

    public func evaluate(context: DiagnosisContext) -> DiagnosisCandidate? {
        guard let expireEvent = context.events.last(where: { $0.type == .taskExpired }) else {
            return nil
        }

        let evidence = Evidence(
            type: .expirationHandler,
            source: .platform,
            timestamp: expireEvent.timestamp,
            title: "Task Expiration Handler Invoked",
            description: "BGTask expiration handler fired before completion",
            sourceEventIds: [expireEvent.id]
        )

        return DiagnosisCandidate(
            ruleId: id,
            title: "Task Expired Before Completion",
            summary: "The iOS background execution quota expired before the task completed.",
            classification: .taskExpired,
            confidence: .confirmed,
            evidence: [evidence],
            limitations: [
                PlatformLimitation(
                    code: "BGTASK_TIME_LIMIT",
                    message: "iOS enforces strict ~30s background execution time limits."
                )
            ],
            priority: 95
        )
    }
}

public struct BGTaskFailureRule: DiagnosisRule {
    public let id = "RULE_APPLICATION_FAILURE"
    public let name = "Application Reported Failure"

    public init() {}

    public func evaluate(context: DiagnosisContext) -> DiagnosisCandidate? {
        guard let failEvent = context.events.last(where: { $0.type == .taskFailed }) else {
            return nil
        }

        let evidence = Evidence(
            type: .completionResult,
            source: .application,
            timestamp: failEvent.timestamp,
            title: "Task Reported Failure",
            description: failEvent.attributes["error"] ?? "setTaskCompleted(success: false) reported by app",
            sourceEventIds: [failEvent.id]
        )

        return DiagnosisCandidate(
            ruleId: id,
            title: "Task Completed With Failure",
            summary: "The application reported task completion with success: false.",
            classification: .applicationFailure,
            confidence: .confirmed,
            evidence: [evidence],
            priority: 60
        )
    }
}

public struct CapabilityMismatchRule: DiagnosisRule {
    public let id = "RULE_CAPABILITY_MISMATCH"
    public let name = "Permitted Identifiers Mismatch"

    public init() {}

    public func evaluate(context: DiagnosisContext) -> DiagnosisCandidate? {
        if let err = context.task.metadata["config_error"] {
            let evidence = Evidence(
                type: .configValidation,
                source: .application,
                title: "Configuration Problem",
                description: err
            )
            return DiagnosisCandidate(
                ruleId: id,
                title: "Configuration Problem",
                summary: err,
                classification: .configurationProblem,
                confidence: .confirmed,
                evidence: [evidence],
                priority: 110
            )
        }
        return nil
    }
}

public struct SchedulerDelayRule: DiagnosisRule {
    public let id = "RULE_SCHEDULER_DELAY"
    public let name = "Scheduler Delay"

    public init() {}

    public func evaluate(context: DiagnosisContext) -> DiagnosisCandidate? {
        guard context.events.contains(where: { $0.type == .taskSubmitted }) else {
            return nil
        }
        let launched = context.events.contains(where: { $0.type == .taskLaunched || $0.type == .taskStarted })
        if !launched {
            return DiagnosisCandidate(
                ruleId: id,
                title: "Task Submitted But Not Launched",
                summary: "The task request was submitted to BGTaskScheduler but the system has not launched it yet.",
                classification: .schedulerDelay,
                confidence: .possible,
                limitations: [
                    PlatformLimitation(
                        code: "IOS_SCHEDULER_BLACK_BOX",
                        message: "iOS does not expose the exact reason or heuristics behind its background task launch decisions."
                    )
                ],
                priority: 40
            )
        }
        return nil
    }
}
