package dev.shushant.tasklens.core

import kotlin.time.Instant

/**
 * Abstraction over the system clock, enabling deterministic testing.
 *
 * The production implementation delegates to [kotlin.time.Clock.System].
 */
interface TaskLensClock {
    /** Returns the current instant. */
    fun now(): Instant
}

/** Production implementation backed by [kotlin.time.Clock.System]. */
object SystemClock : TaskLensClock {
    override fun now(): Instant = kotlin.time.Clock.System.now()
}
