package dev.shushant.tasklens.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SequenceCounterStressTest {

    @Test
    fun testHighConcurrencySequenceGeneration() = runTest(timeout = kotlin.time.Duration.parse("60s")) {
        TaskLensEvent.resetSequence(0L)

        val numCoroutines = 100
        val incrementsPerCoroutine = 10_000
        val totalExpected = numCoroutines * incrementsPerCoroutine

        // Array to store generated sequence numbers across 100 coroutines
        val results = LongArray(totalExpected)

        coroutineScope {
            for (c in 0 until numCoroutines) {
                launch(Dispatchers.Default) {
                    val baseIdx = c * incrementsPerCoroutine
                    for (i in 0 until incrementsPerCoroutine) {
                        results[baseIdx + i] = TaskLensEvent.nextSequence()
                    }
                }
            }
        }

        // Sort the generated IDs to verify uniqueness, absence of gaps, and strict monotonicity
        results.sort()

        assertEquals(1L, results.first(), "First sequence ID should be 1")
        assertEquals(totalExpected.toLong(), results.last(), "Last sequence ID should be $totalExpected")

        var previous = 0L
        for (i in 0 until totalExpected) {
            val current = results[i]
            val expected = previous + 1
            if (current != expected) {
                throw AssertionError("Sequence violation at index $i: expected $expected but got $current")
            }
            previous = current
        }

        assertEquals(totalExpected.toLong(), previous, "All 1,000,000 sequence IDs generated monotonically without gaps or duplicates")
    }
}
