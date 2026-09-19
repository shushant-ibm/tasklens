package dev.shushant.tasklens.diagnosis.rules

import dev.shushant.tasklens.core.DiagnosisClassification
import dev.shushant.tasklens.core.DiagnosisConfidence
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.PlatformLimitation
import dev.shushant.tasklens.core.PlatformReason
import dev.shushant.tasklens.core.PossibleFactor
import dev.shushant.tasklens.diagnosis.DiagnosisCandidate
import dev.shushant.tasklens.diagnosis.DiagnosisContext
import dev.shushant.tasklens.diagnosis.DiagnosisRule
import dev.shushant.tasklens.diagnosis.EvidenceBuilder
import kotlin.time.Clock

class NetworkConstraintRule : DiagnosisRule {
    override val id = "RULE_NETWORK_CONSTRAINT_BLOCKED"
    override val name = "Blocked by Network Constraint"

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val waitingEvent = context.events.lastOrNull { it.type == EventType.TASK_WAITING || it.type == EventType.TASK_ENQUEUED }
            ?: return null

        val requiredNetwork = waitingEvent.attributes["required_network"]
            ?: context.task.metadata["required_network"]
            ?: return null

        val currentNetwork = waitingEvent.attributes["current_network"] ?: "DISCONNECTED"
        val isNetworkSatisfied = waitingEvent.attributes["network_satisfied"]?.toBoolean() ?: false

        if (!isNetworkSatisfied && requiredNetwork != "NOT_REQUIRED") {
            val evidence = EvidenceBuilder.fromConstraint(
                constraintName = "Network Requirement",
                required = requiredNetwork,
                actual = currentNetwork,
                timestamp = waitingEvent.timestamp
            )

            return DiagnosisCandidate(
                ruleId = id,
                title = "Waiting on Network Constraint",
                summary = "The task was enqueued and remains blocked because its network constraint ($requiredNetwork) is unsatisfied (current state: $currentNetwork).",
                classification = DiagnosisClassification.WAITING_ON_CONSTRAINT,
                confidence = DiagnosisConfidence.CONFIRMED,
                evidence = listOf(evidence),
                priority = 90
            )
        }
        return null
    }
}

class RetryRequestedRule : DiagnosisRule {
    override val id = "RULE_RETRY_REQUESTED"
    override val name = "Retry Requested by Worker"

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val retryEvent = context.events.lastOrNull { it.type == EventType.TASK_RETRY_REQUESTED }
        val attemptCount = context.attempts.size

        if (retryEvent != null || attemptCount > 1) {
            val evidenceList = mutableListOf<dev.shushant.tasklens.core.Evidence>()
            if (retryEvent != null) {
                evidenceList.add(
                    EvidenceBuilder.fromStateTransition(
                        fromState = "RUNNING",
                        toState = "RETRY",
                        timestamp = retryEvent.timestamp,
                        sourceEventId = retryEvent.id
                    )
                )
            }

            return DiagnosisCandidate(
                ruleId = id,
                title = "Worker Requested Retry",
                summary = "Execution attempt was scheduled after the worker explicitly requested retry (current attempt count: $attemptCount).",
                classification = DiagnosisClassification.RETRY_REQUESTED,
                confidence = if (retryEvent != null) DiagnosisConfidence.CONFIRMED else DiagnosisConfidence.LIKELY,
                evidence = evidenceList,
                priority = 70
            )
        }
        return null
    }
}

class PlatformStopReasonRule : DiagnosisRule {
    override val id = "RULE_PLATFORM_STOPPED"
    override val name = "Platform Stopped Execution"

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val stopEvent = context.events.lastOrNull { it.type == EventType.TASK_STOPPED }
        val stopAttempt = context.attempts.lastOrNull { it.platformReason != null }

        val reason = stopAttempt?.platformReason
            ?: stopEvent?.attributes?.get("stop_reason")?.toIntOrNull()?.let { code ->
                PlatformReason(
                    code = code,
                    name = stopEvent.attributes["stop_reason_name"] ?: "STOP_REASON_$code",
                    description = stopEvent.attributes["stop_reason_desc"]
                )
            }

        if (reason != null) {
            val evidence = EvidenceBuilder.fromPlatformReason(
                code = reason.code,
                name = reason.name,
                description = reason.description,
                timestamp = stopEvent?.timestamp,
                sourceEventIds = listOfNotNull(stopEvent?.id)
            )

            return DiagnosisCandidate(
                ruleId = id,
                title = "Execution Stopped by Platform",
                summary = "The operating system stopped execution: ${reason.name}${reason.description?.let { " ($it)" } ?: ""}.",
                classification = DiagnosisClassification.PLATFORM_STOPPED,
                confidence = DiagnosisConfidence.CONFIRMED,
                evidence = listOf(evidence),
                priority = 100
            )
        }
        return null
    }
}

class NetworkInterruptionRule : DiagnosisRule {
    override val id = "RULE_NETWORK_INTERRUPTION"
    override val name = "Network Interruption During Execution"

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val runningAttempt = context.attempts.lastOrNull() ?: return null
        val startedAt = runningAttempt.startedAt ?: return null
        val endedAt = runningAttempt.endedAt

        val networkDrops = context.events.filter { event ->
            event.type == EventType.NETWORK_CHANGED &&
                event.attributes["connected"] == "false" &&
                event.timestamp >= startedAt &&
                (endedAt == null || event.timestamp <= endedAt)
        }

        if (networkDrops.isNotEmpty()) {
            val drop = networkDrops.last()
            val evidence = EvidenceBuilder.fromNetwork(
                description = "Device lost network connectivity while task attempt #${runningAttempt.attemptNumber} was executing",
                timestamp = drop.timestamp,
                sourceEventId = drop.id
            )

            return DiagnosisCandidate(
                ruleId = id,
                title = "Network Disconnected During Execution",
                summary = "Network connection dropped during active task execution.",
                classification = DiagnosisClassification.NETWORK_INTERRUPTION,
                confidence = DiagnosisConfidence.LIKELY,
                evidence = listOf(evidence),
                possibleFactors = listOf(
                    PossibleFactor(
                        title = "Connectivity Loss",
                        description = "Network became unavailable at ${drop.timestamp}",
                        timestamp = drop.timestamp
                    )
                ),
                priority = 80
            )
        }
        return null
    }
}

class TaskExpiredRule : DiagnosisRule {
    override val id = "RULE_TASK_EXPIRED"
    override val name = "Task Expired"

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val expireEvent = context.events.lastOrNull { it.type == EventType.TASK_EXPIRED } ?: return null

        val evidence = EvidenceBuilder.fromExpiration(
            timestamp = expireEvent.timestamp,
            sourceEventId = expireEvent.id
        )

        return DiagnosisCandidate(
            ruleId = id,
            title = "Task Expired Before Completion",
            summary = "The operating system background execution quota expired before the task reported successful completion.",
            classification = DiagnosisClassification.TASK_EXPIRED,
            confidence = DiagnosisConfidence.CONFIRMED,
            evidence = listOf(evidence),
            limitations = listOf(
                PlatformLimitation(
                    code = "BG_TASK_MAX_EXECUTION_TIME",
                    message = "Operating systems impose strict background execution time caps (e.g. ~30 seconds on iOS)."
                )
            ),
            priority = 95
        )
    }
}

class ApplicationFailureRule : DiagnosisRule {
    override val id = "RULE_APPLICATION_FAILURE"
    override val name = "Application Code Reported Failure"

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val failEvent = context.events.lastOrNull { it.type == EventType.TASK_FAILED } ?: return null

        val errorMsg = failEvent.attributes["error_message"] ?: failEvent.attributes["exception"] ?: "Worker returned Result.failure()"
        val evidence = EvidenceBuilder.fromStateTransition(
            fromState = "RUNNING",
            toState = "FAILED",
            timestamp = failEvent.timestamp,
            sourceEventId = failEvent.id
        )

        return DiagnosisCandidate(
            ruleId = id,
            title = "Task Execution Failed",
            summary = "The application worker reported failure: $errorMsg",
            classification = DiagnosisClassification.APPLICATION_FAILURE,
            confidence = DiagnosisConfidence.CONFIRMED,
            evidence = listOf(evidence),
            priority = 60
        )
    }
}

class ApplicationCancelledRule : DiagnosisRule {
    override val id = "RULE_APPLICATION_CANCELLED"
    override val name = "Task Cancelled"

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val cancelEvent = context.events.lastOrNull { it.type == EventType.TASK_CANCELLED } ?: return null

        val evidence = EvidenceBuilder.fromStateTransition(
            fromState = "ENQUEUED/RUNNING",
            toState = "CANCELLED",
            timestamp = cancelEvent.timestamp,
            sourceEventId = cancelEvent.id
        )

        return DiagnosisCandidate(
            ruleId = id,
            title = "Task Cancelled",
            summary = "The task was explicitly cancelled by application request or work chain cancellation.",
            classification = DiagnosisClassification.APPLICATION_CANCELLED,
            confidence = DiagnosisConfidence.CONFIRMED,
            evidence = listOf(evidence),
            priority = 75
        )
    }
}

class SchedulerDelayRule : DiagnosisRule {
    override val id = "RULE_SCHEDULER_DELAY"
    override val name = "Scheduler Dispatch Delay"

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val submitted = context.events.firstOrNull { it.type == EventType.TASK_SUBMITTED || it.type == EventType.TASK_ENQUEUED } ?: return null
        val hasLaunched = context.events.any { it.type == EventType.TASK_LAUNCHED || it.type == EventType.TASK_STARTED }

        if (!hasLaunched) {
            val now = Clock.System.now().toEpochMilliseconds()
            val submittedMs = submitted.timestamp.toEpochMilliseconds()
            val delaySeconds = (now - submittedMs) / 1000

            if (delaySeconds > 30L) {
                return DiagnosisCandidate(
                    ruleId = id,
                    title = "Scheduler Dispatch Delay",
                    summary = "The task was submitted $delaySeconds seconds ago but has not yet been launched by the system scheduler.",
                    classification = DiagnosisClassification.SCHEDULER_DELAY,
                    confidence = DiagnosisConfidence.POSSIBLE,
                    evidence = emptyList(),
                    limitations = listOf(
                        PlatformLimitation(
                            code = "SCHEDULER_BLACK_BOX",
                            message = "The system scheduler (e.g. iOS BGTaskScheduler / Android JobScheduler) does not expose internal scheduling heuristics."
                        )
                    ),
                    priority = 40
                )
            }
        }
        return null
    }
}

class CapabilityMismatchRule : DiagnosisRule {
    override val id = "RULE_CAPABILITY_MISMATCH"
    override val name = "Scheduler Configuration Mismatch"

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val mismatch = context.task.metadata["config_error"] ?: context.events.firstNotNullOfOrNull { it.attributes["config_error"] }
        if (!mismatch.isNullOrBlank()) {
            val evidence = EvidenceBuilder.fromConfig(
                title = "Configuration Mismatch",
                description = mismatch
            )
            return DiagnosisCandidate(
                ruleId = id,
                title = "Configuration Problem",
                summary = mismatch,
                classification = DiagnosisClassification.CONFIGURATION_PROBLEM,
                confidence = DiagnosisConfidence.CONFIRMED,
                evidence = listOf(evidence),
                priority = 110
            )
        }
        return null
    }
}
