package dev.shushant.tasklens.diagnosis

import dev.shushant.tasklens.core.Evidence
import dev.shushant.tasklens.core.EvidenceSource
import dev.shushant.tasklens.core.EvidenceType
import kotlin.random.Random
import kotlin.time.Instant

private fun evidenceId(): String {
    val bytes = Random.nextBytes(16)
    val sb = StringBuilder(32)
    bytes.forEach { b ->
        val v = b.toInt() and 0xff
        val hex = v.toString(16)
        if (hex.length == 1) sb.append('0')
        sb.append(hex)
    }
    return sb.toString()
}

object EvidenceBuilder {

    fun fromPlatformReason(
        code: Int,
        name: String,
        description: String?,
        timestamp: Instant? = null,
        sourceEventIds: List<String> = emptyList()
    ): Evidence = Evidence(
        id = evidenceId(),
        type = EvidenceType.PLATFORM_REASON,
        source = EvidenceSource.PLATFORM,
        timestamp = timestamp,
        title = "Platform Stop Reason: $name",
        description = description ?: "System reported code $code ($name)",
        sourceEventIds = sourceEventIds,
        metadata = mapOf("code" to code.toString(), "name" to name)
    )

    fun fromStateTransition(
        fromState: String,
        toState: String,
        timestamp: Instant,
        sourceEventId: String
    ): Evidence = Evidence(
        id = evidenceId(),
        type = EvidenceType.STATE_TRANSITION,
        source = EvidenceSource.PLATFORM,
        timestamp = timestamp,
        title = "State Transition: $fromState → $toState",
        description = "Task transitioned to state $toState",
        sourceEventIds = listOf(sourceEventId),
        metadata = mapOf("from" to fromState, "to" to toState)
    )

    fun fromConstraint(
        constraintName: String,
        required: String,
        actual: String,
        timestamp: Instant? = null
    ): Evidence = Evidence(
        id = evidenceId(),
        type = EvidenceType.CONSTRAINT_STATE,
        source = EvidenceSource.ENVIRONMENT,
        timestamp = timestamp,
        title = "Unsatisfied Constraint: $constraintName",
        description = "Required: $required, Actual state: $actual",
        sourceEventIds = emptyList(),
        metadata = mapOf("constraint" to constraintName, "required" to required, "actual" to actual)
    )

    fun fromNetwork(
        description: String,
        timestamp: Instant,
        sourceEventId: String
    ): Evidence = Evidence(
        id = evidenceId(),
        type = EvidenceType.NETWORK_STATE,
        source = EvidenceSource.ENVIRONMENT,
        timestamp = timestamp,
        title = "Network Disconnected During Execution",
        description = description,
        sourceEventIds = listOf(sourceEventId)
    )

    fun fromExpiration(
        timestamp: Instant,
        sourceEventId: String
    ): Evidence = Evidence(
        id = evidenceId(),
        type = EvidenceType.EXPIRATION_HANDLER,
        source = EvidenceSource.PLATFORM,
        timestamp = timestamp,
        title = "Task Expiration Handler Invoked",
        description = "OS background runtime quota expired before task completion",
        sourceEventIds = listOf(sourceEventId)
    )

    fun fromConfig(
        title: String,
        description: String,
        metadata: Map<String, String> = emptyMap()
    ): Evidence = Evidence(
        id = evidenceId(),
        type = EvidenceType.CONFIG_VALIDATION,
        source = EvidenceSource.APPLICATION,
        title = title,
        description = description,
        metadata = metadata
    )
}
