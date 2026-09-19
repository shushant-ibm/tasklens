package dev.shushant.tasklens.core

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class TaskType {
    WORKER,
    COROUTINE_WORKER,
    PERIODIC_WORK,
    EXPEDITED_WORK,
    BG_APP_REFRESH,
    BG_PROCESSING,
    BACKGROUND_URLSESSION,
    JOB_SERVICE,
    FOREGROUND_SERVICE,
    ALARM,
    CUSTOM
}

@Serializable
enum class SchedulerType {
    WORK_MANAGER,
    BG_TASK_SCHEDULER,
    JOB_SCHEDULER,
    FOREGROUND_SERVICE,
    ALARM_MANAGER,
    URL_SESSION,
    MANUAL
}

@Serializable
enum class AttemptOutcome {
    SUCCESS,
    RETRY,
    STOPPED,
    EXPIRED,
    CANCELLED,
    FAILED,
    RUNNING,
    UNKNOWN
}

@Serializable
enum class NetworkRequirement {
    NOT_REQUIRED,
    CONNECTED,
    UNMETERED,
    NOT_ROAMING,
    TEMPORARILY_UNMETERED
}

@Serializable
enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    VPN,
    OTHER,
    NONE
}

@Serializable
enum class AppState {
    FOREGROUND,
    BACKGROUND,
    TERMINATED,
    UNKNOWN
}

@Serializable
enum class ProcessState {
    RUNNING,
    BACKGROUND_RESTRICTED,
    KILLED,
    UNKNOWN
}

@Serializable
data class PlatformReason(
    val code: Int,
    val name: String,
    val description: String? = null
)

@Serializable
data class NetworkState(
    val isConnected: Boolean,
    val isValidated: Boolean = true,
    val isMetered: Boolean = false,
    val transport: NetworkTransport = NetworkTransport.NONE
)

@Serializable
data class ConstraintSnapshot(
    val network: NetworkRequirement? = null,
    val chargingRequired: Boolean? = null,
    val batteryNotLowRequired: Boolean? = null,
    val storageNotLowRequired: Boolean? = null,
    val externalPowerRequired: Boolean? = null,
    val requiresIdle: Boolean? = null,
    val custom: Map<String, String> = emptyMap()
)

@Serializable
data class EnvironmentSnapshot(
    val timestamp: Instant,
    val networkState: NetworkState? = null,
    val charging: Boolean? = null,
    val batteryLevel: Double? = null,
    val lowPowerMode: Boolean? = null,
    val batterySaver: Boolean? = null,
    val appState: AppState? = null,
    val backgroundRefreshEnabled: Boolean? = null,
    val processState: ProcessState? = null,
    val platformAttributes: Map<String, String> = emptyMap()
)

@Serializable
data class ScheduledWork(
    val id: String,
    val platformId: String? = null,
    val name: String? = null,
    val type: TaskType = TaskType.CUSTOM,
    val scheduler: SchedulerType = SchedulerType.MANUAL,
    val submittedAt: Instant? = null,
    val earliestBeginAt: Instant? = null,
    val periodic: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ExecutionAttempt(
    val attemptId: String,
    val taskId: String,
    val attemptNumber: Int,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val outcome: AttemptOutcome? = null,
    val platformReason: PlatformReason? = null,
    val evidenceIds: List<String> = emptyList()
)
