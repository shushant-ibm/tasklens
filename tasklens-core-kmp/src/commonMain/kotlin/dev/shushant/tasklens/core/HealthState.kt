package dev.shushant.tasklens.core

/**
 * Represents the operational health of the TaskLens SDK at a point in time.
 *
 * Consumers can call `TaskLens.health()` to determine whether all sub-systems
 * are fully operational or whether some are degraded/disabled.
 *
 * Spec §36 — Health API.
 */
enum class HealthState {
    /**
     * All sub-systems (storage, Kourier bridge, export engine) are operational.
     * The SDK is fully collecting and processing events.
     */
    READY,

    /**
     * The persistent storage layer has encountered an error or is unavailable.
     * Events may be dropped or only held in memory.
     */
    DEGRADED_STORAGE,

    /**
     * The Kourier network telemetry bridge failed to start or lost its
     * connection.  Network events will not be captured.
     */
    DEGRADED_KOURIER,

    /**
     * The export engine encountered an error on the last export attempt.
     * Trace exports may be incomplete.
     */
    DEGRADED_EXPORT,

    /**
     * The SDK has not been installed (i.e. `TaskLens.install()` was never
     * called), or the no-op variant is in use.  No telemetry is being
     * collected.
     */
    DISABLED,
}
