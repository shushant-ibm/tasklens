package dev.shushant.tasklens.core

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class DiagnosisConfidence {
    CONFIRMED,
    LIKELY,
    POSSIBLE,
    UNKNOWN
}

@Serializable
enum class DiagnosisClassification {
    WAITING_ON_CONSTRAINT,
    PLATFORM_STOPPED,
    TASK_EXPIRED,
    RETRY_REQUESTED,
    NETWORK_INTERRUPTION,
    POWER_RESTRICTION,
    PROCESS_TERMINATED,
    APPLICATION_CANCELLED,
    APPLICATION_FAILURE,
    SCHEDULER_DELAY,
    CONFIGURATION_PROBLEM,
    UNKNOWN
}

@Serializable
data class PossibleFactor(
    val title: String,
    val description: String,
    val timestamp: Instant? = null
)

@Serializable
data class PlatformLimitation(
    val code: String,
    val message: String
)

@Serializable
data class Diagnosis(
    val id: String,
    val taskId: String,
    val attemptId: String? = null,
    val ruleId: String = "",
    val ruleVersion: String = "1",
    val title: String,
    val summary: String,
    val classification: DiagnosisClassification,
    val confidence: DiagnosisConfidence,
    val evidence: List<Evidence> = emptyList(),
    val possibleFactors: List<PossibleFactor> = emptyList(),
    val limitations: List<PlatformLimitation> = emptyList()
)
