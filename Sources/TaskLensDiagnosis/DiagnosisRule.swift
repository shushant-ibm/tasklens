import Foundation
import TaskLensCore

public struct DiagnosisContext {
    public let task: ScheduledWork
    public let attempts: [ExecutionAttempt]
    public let events: [TaskLensEvent]

    public init(
        task: ScheduledWork,
        attempts: [ExecutionAttempt] = [],
        events: [TaskLensEvent] = []
    ) {
        self.task = task
        self.attempts = attempts
        self.events = events
    }
}

public struct DiagnosisCandidate {
    public let ruleId: String
    public let title: String
    public let summary: String
    public let classification: DiagnosisClassification
    public let confidence: DiagnosisConfidence
    public let evidence: [Evidence]
    public let possibleFactors: [PossibleFactor]
    public let limitations: [PlatformLimitation]
    public let priority: Int

    public init(
        ruleId: String,
        title: String,
        summary: String,
        classification: DiagnosisClassification,
        confidence: DiagnosisConfidence,
        evidence: [Evidence] = [],
        possibleFactors: [PossibleFactor] = [],
        limitations: [PlatformLimitation] = [],
        priority: Int = 0
    ) {
        self.ruleId = ruleId
        self.title = title
        self.summary = summary
        self.classification = classification
        self.confidence = confidence
        self.evidence = evidence
        self.possibleFactors = possibleFactors
        self.limitations = limitations
        self.priority = priority
    }
}

public protocol DiagnosisRule {
    var id: String { get }
    var name: String { get }
    func evaluate(context: DiagnosisContext) -> DiagnosisCandidate?
}
