import Foundation

public enum DiagnosisConfidence: String, Codable {
    case confirmed = "CONFIRMED"
    case strong = "STRONG"
    case possible = "POSSIBLE"
    case unknown = "UNKNOWN"
}

public enum DiagnosisClassification: String, Codable {
    case waitingOnConstraint = "WAITING_ON_CONSTRAINT"
    case platformStopped = "PLATFORM_STOPPED"
    case taskExpired = "TASK_EXPIRED"
    case retryRequested = "RETRY_REQUESTED"
    case networkInterruption = "NETWORK_INTERRUPTION"
    case powerRestriction = "POWER_RESTRICTION"
    case processTerminated = "PROCESS_TERMINATED"
    case applicationCancelled = "APPLICATION_CANCELLED"
    case applicationFailure = "APPLICATION_FAILURE"
    case schedulerDelay = "SCHEDULER_DELAY"
    case configurationProblem = "CONFIGURATION_PROBLEM"
    case unknown = "UNKNOWN"
}

public struct PossibleFactor: Codable {
    public let title: String
    public let description: String
    public let timestamp: Date?

    public init(title: String, description: String, timestamp: Date? = nil) {
        self.title = title
        self.description = description
        self.timestamp = timestamp
    }
}

public struct Diagnosis: Codable, Identifiable {
    public let id: String
    public let taskId: String
    public let attemptId: String?
    public let ruleId: String
    public let ruleVersion: String
    public let title: String
    public let summary: String
    public let classification: DiagnosisClassification
    public let confidence: DiagnosisConfidence
    public let evidence: [Evidence]
    public let possibleFactors: [PossibleFactor]
    public let limitations: [PlatformLimitation]

    public init(
        id: String = UUID().uuidString,
        taskId: String,
        attemptId: String? = nil,
        ruleId: String = "",
        ruleVersion: String = "1",
        title: String,
        summary: String,
        classification: DiagnosisClassification,
        confidence: DiagnosisConfidence,
        evidence: [Evidence] = [],
        possibleFactors: [PossibleFactor] = [],
        limitations: [PlatformLimitation] = []
    ) {
        self.id = id
        self.taskId = taskId
        self.attemptId = attemptId
        self.ruleId = ruleId
        self.ruleVersion = ruleVersion
        self.title = title
        self.summary = summary
        self.classification = classification
        self.confidence = confidence
        self.evidence = evidence
        self.possibleFactors = possibleFactors
        self.limitations = limitations
    }
}
