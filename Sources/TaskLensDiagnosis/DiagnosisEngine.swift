import Foundation
import TaskLensCore

public protocol DiagnosisEngine {
    func diagnose(context: DiagnosisContext) -> [Diagnosis]
}

public struct DefaultDiagnosisEngine: DiagnosisEngine {
    private let rules: [DiagnosisRule]

    public init(rules: [DiagnosisRule]? = nil) {
        self.rules = rules ?? [
            CapabilityMismatchRule(),
            BGTaskExpiredRule(),
            BGTaskFailureRule(),
            SchedulerDelayRule()
        ]
    }

    public func diagnose(context: DiagnosisContext) -> [Diagnosis] {
        var candidates: [DiagnosisCandidate] = []

        for rule in rules {
            if let candidate = rule.evaluate(context: context) {
                candidates.append(candidate)
            }
        }

        if candidates.isEmpty {
            return [
                Diagnosis(
                    taskId: context.task.id,
                    attemptId: context.attempts.last?.attemptId,
                    title: "Execution Normal / Inconclusive",
                    summary: "No abnormal execution signals or interruptions were detected for this background task.",
                    classification: .unknown,
                    confidence: .unknown
                )
            ]
        }

        return candidates.sorted { $0.priority > $1.priority }.map { candidate in
            Diagnosis(
                taskId: context.task.id,
                attemptId: context.attempts.last?.attemptId,
                ruleId: candidate.ruleId,
                ruleVersion: "1.0",
                title: candidate.title,
                summary: candidate.summary,
                classification: candidate.classification,
                confidence: candidate.confidence,
                evidence: candidate.evidence,
                possibleFactors: candidate.possibleFactors,
                limitations: candidate.limitations
            )
        }
    }
}
