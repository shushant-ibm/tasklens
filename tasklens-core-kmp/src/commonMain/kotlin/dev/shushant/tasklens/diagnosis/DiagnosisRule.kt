package dev.shushant.tasklens.diagnosis

import dev.shushant.tasklens.core.DiagnosisClassification
import dev.shushant.tasklens.core.DiagnosisConfidence
import dev.shushant.tasklens.core.EnvironmentSnapshot
import dev.shushant.tasklens.core.Evidence
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.PlatformLimitation
import dev.shushant.tasklens.core.PossibleFactor
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.TaskLensEvent

data class DiagnosisContext(
    val task: ScheduledWork,
    val attempts: List<ExecutionAttempt>,
    val events: List<TaskLensEvent>,
    val environment: List<EnvironmentSnapshot> = emptyList()
)

data class DiagnosisCandidate(
    val ruleId: String,
    val title: String,
    val summary: String,
    val classification: DiagnosisClassification,
    val confidence: DiagnosisConfidence,
    val evidence: List<Evidence>,
    val possibleFactors: List<PossibleFactor> = emptyList(),
    val limitations: List<PlatformLimitation> = emptyList(),
    val priority: Int = 0
)

interface DiagnosisRule {
    val id: String
    val name: String
    /** Lower number = higher priority; rules with lower priority run first. Default 100. */
    val priority: Int get() = 100
    fun evaluate(context: DiagnosisContext): DiagnosisCandidate?
}
