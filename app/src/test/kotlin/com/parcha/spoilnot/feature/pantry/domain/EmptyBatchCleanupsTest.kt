package com.parcha.spoilnot.feature.pantry.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** A clean-up the server refused isn't retried in a loop (after v1, review REL-11). */
class EmptyBatchCleanupsTest {
    private val cleanups = EmptyBatchCleanups()

    @Test
    fun `a batch waiting for the server's answer isn't written again`() {
        assertEquals(listOf("a", "b"), cleanups.toTry(listOf("a", "b")))
        assertEquals(listOf("c"), cleanups.toTry(listOf("a", "b", "c")))
    }

    @Test
    fun `a refused clean-up isn't tried again, an accepted one may be`() {
        cleanups.toTry(listOf("neg", "zero"))
        cleanups.answered(listOf("neg"), refused = true)
        cleanups.answered(listOf("zero"), refused = false)
        // "zero" came back (Undo of Used up) and ran out again: it is cleaned up again; "neg" never in this process.
        assertEquals(listOf("zero"), cleanups.toTry(listOf("neg", "zero")))
    }
}
