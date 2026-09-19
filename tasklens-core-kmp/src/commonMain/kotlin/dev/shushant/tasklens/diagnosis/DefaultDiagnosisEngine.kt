package dev.shushant.tasklens.diagnosis

import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.DiagnosisClassification
import dev.shushant.tasklens.core.DiagnosisConfidence
import dev.shushant.tasklens.diagnosis.rules.ApplicationCancelledRule
import dev.shushant.tasklens.diagnosis.rules.ApplicationFailureRule
import dev.shushant.tasklens.diagnosis.rules.CapabilityMismatchRule
import dev.shushant.tasklens.diagnosis.rules.NetworkConstraintRule
import dev.shushant.tasklens.diagnosis.rules.NetworkInterruptionRule
import dev.shushant.tasklens.diagnosis.rules.PlatformStopReasonRule
import dev.shushant.tasklens.diagnosis.rules.RetryRequestedRule
import dev.shushant.tasklens.diagnosis.rules.SchedulerDelayRule
import dev.shushant.tasklens.diagnosis.rules.TaskExpiredRule
import kotlin.random.Random

interface DiagnosisEngine {
    fun diagnose(context: DiagnosisContext): List<Diagnosis>
}

class DefaultDiagnosisEngine(
    private val rules: List<DiagnosisRule> = listOf(
        CapabilityMismatchRule(),
        PlatformStopReasonRule(),
        TaskExpiredRule(),
        NetworkConstraintRule(),
        NetworkInterruptionRule(),
        ApplicationCancelledRule(),
        RetryRequestedRule(),
        ApplicationFailureRule(),
        SchedulerDelayRule()
    )
) : DiagnosisEngine {

    override fun diagnose(context: DiagnosisContext): List<Diagnosis> {
        val candidates = mutableListOf<DiagnosisCandidate>()

        // Sort rules by priority (lower int = higher priority) before evaluating
        for (rule in rules.sortedBy { it.priority }) {
            try {
                val candidate = rule.evaluate(context)
                if (candidate != null) {
                    candidates.add(candidate)
                }
            } catch (t: Throwable) {
                // Failure isolation: a rule failure must never crash diagnosis
            }
        }

        if (candidates.isEmpty()) {
            return listOf(
                Diagnosis(
                    id = diagnosisId(),
                    taskId = context.task.id,
                    attemptId = context.attempts.lastOrNull()?.attemptId,
                    ruleId = "unknown",
                    ruleVersion = "1",
                    title = "Execution Normal / Inconclusive",
                    summary = "No abnormal execution signals or interruptions were detected for this task.",
                    classification = DiagnosisClassification.UNKNOWN,
                    confidence = DiagnosisConfidence.UNKNOWN,
                    evidence = emptyList(),
                    possibleFactors = emptyList(),
                    limitations = emptyList()
                )
            )
        }

        // Higher candidate priority = more important (sort descending)
        return candidates.sortedByDescending { it.priority }.map { candidate ->
            Diagnosis(
                id = diagnosisId(),
                taskId = context.task.id,
                attemptId = context.attempts.lastOrNull()?.attemptId,
                ruleId = candidate.ruleId,
                ruleVersion = "1",
                title = candidate.title,
                summary = candidate.summary,
                classification = candidate.classification,
                confidence = candidate.confidence,
                evidence = candidate.evidence,
                possibleFactors = candidate.possibleFactors,
                limitations = candidate.limitations
            )
        }
    }
}

/** Pure-Kotlin UUIDv4 without java.util.UUID for cross-platform KMP. */
private fun diagnosisId(): String {
    val bytes = ByteArray(16) { Random.nextInt(256).toByte() }
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val sb = StringBuilder(36)
    bytes.forEachIndexed { i, b ->
        if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
        val v = b.toInt() and 0xff
        val hex = v.toString(16)
        if (hex.length == 1) sb.append('0')
        sb.append(hex)
    }
    return sb.toString()
}
