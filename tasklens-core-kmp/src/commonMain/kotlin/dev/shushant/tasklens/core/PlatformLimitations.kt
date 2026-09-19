package dev.shushant.tasklens.core

/**
 * Canonical codes for platform-level limitations that may affect task
 * execution or TaskLens telemetry collection.
 *
 * These codes align with the TaskLens Enterprise spec §15 and are surfaced
 * by the Diagnosis Engine in [DiagnosisCandidate] evidence records.
 */
enum class PlatformLimitationCode(
    /** Short, machine-readable identifier used in event attributes and reports. */
    val code: String,
    /** Human-readable description of the limitation. */
    val description: String
) {
    /** Android Doze mode is active; network and CPU are restricted. */
    DOZE_MODE_ACTIVE(
        code = "DOZE_MODE_ACTIVE",
        description = "Android Doze mode is active. Background tasks may be deferred or killed."
    ),

    /** App is battery-optimised; WorkManager and alarms may be restricted. */
    BATTERY_OPTIMIZATION_ENABLED(
        code = "BATTERY_OPTIMIZATION_ENABLED",
        description = "Battery optimization is enabled for this app. Task scheduling may be restricted."
    ),

    /** Low system memory; processes can be killed at any point. */
    LOW_MEMORY(
        code = "LOW_MEMORY",
        description = "Device is in a low-memory state. Background processes are at elevated risk of being killed."
    ),

    /** Network is metered; tasks that require unmetered connections will be deferred. */
    METERED_NETWORK(
        code = "METERED_NETWORK",
        description = "Active network is metered. Tasks with unmetered network constraints will wait."
    ),

    /** No network connectivity is available. */
    NO_NETWORK(
        code = "NO_NETWORK",
        description = "No network connection is available. Network-dependent tasks cannot proceed."
    ),

    /** Storage is critically low; writes may fail silently. */
    LOW_STORAGE(
        code = "LOW_STORAGE",
        description = "Device storage is critically low. Event persistence may fail."
    ),

    /** The device is in power-save mode; CPU and network throughput are reduced. */
    POWER_SAVE_MODE(
        code = "POWER_SAVE_MODE",
        description = "Power-save mode is active. Task execution frequency may be reduced."
    ),

    /** The app is operating in the background and subject to Background Execution Limits. */
    BACKGROUND_EXECUTION_LIMITED(
        code = "BACKGROUND_EXECUTION_LIMITED",
        description = "App is subject to Android Background Execution Limits. Foreground service may be required."
    ),
}

/**
 * A single platform limitation observation captured during a diagnosis context evaluation.
 * This is a richer runtime snapshot used internally by the diagnosis engine; the serialisable
 * [PlatformLimitation] in Diagnosis.kt is the canonical wire/storage type.
 */
data class ObservedPlatformLimitation(
    /** The canonical code for this limitation. */
    val code: PlatformLimitationCode,
    /** Optional additional detail gathered at the time of observation. */
    val detail: String = ""
)
