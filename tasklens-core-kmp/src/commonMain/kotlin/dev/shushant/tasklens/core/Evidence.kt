package dev.shushant.tasklens.core

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class EvidenceType {
    PLATFORM_REASON,
    STATE_TRANSITION,
    CONSTRAINT_STATE,
    NETWORK_STATE,
    POWER_STATE,
    LIFECYCLE_STATE,
    EXPIRATION_HANDLER,
    COMPLETION_RESULT,
    RETRY_SIGNAL,
    EXCEPTION,
    TIME_CORRELATION,
    CONFIG_VALIDATION
}

@Serializable
enum class EvidenceSource {
    PLATFORM,
    ENVIRONMENT,
    APPLICATION,
    DERIVED,
    CORRELATION
}

@Serializable
data class Evidence(
    val id: String,
    val type: EvidenceType,
    val source: EvidenceSource,
    val timestamp: Instant? = null,
    val title: String,
    val description: String,
    val sourceEventIds: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)
