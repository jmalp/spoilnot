package com.parcha.spoilnot.core.write

import kotlin.math.abs

/**
 * A change that adds [delta] to the counts in one document, such as a month of Insights (DESIGN 5.7). The rules let a
 * count move by at most one per write (DESIGN 8.5), so [WriteBatches] sends it as steps of one, each counted document
 * at most once per batch.
 */
interface CounterChange : HouseholdChange {
    /** The counted document (the month), shared by every change that writes to it. */
    val counter: String
    val delta: Int

    fun withDelta(delta: Int): CounterChange
}

/** How the writers group a plan of changes into batched writes (DESIGN 8.5). */
object WriteBatches {
    /** DESIGN 8.5: chunk batched writes at 400 operations. */
    const val LIMIT = 400

    /**
     * [changes] in order, at most [limit] per batch. A [CounterChange] becomes steps of +1 or -1: the first step of
     * each counted document goes in the batch with the changes it came with, and the rest follow in later batches, at
     * most one step per document in each (two writes to one document in a batch count as one write to the rules).
     */
    fun plan(changes: List<HouseholdChange>, limit: Int = LIMIT): List<List<HouseholdChange>> {
        require(limit > 0)
        val batches = mutableListOf<List<HouseholdChange>>()
        var current = mutableListOf<HouseholdChange>()
        val counted = mutableSetOf<String>()
        var later = mutableListOf<CounterChange>()

        fun next() {
            if (current.isNotEmpty()) batches += current
            current = mutableListOf()
            counted.clear()
        }

        for (change in changes) {
            if (current.size >= limit) next()
            if (change !is CounterChange) {
                current += change
                continue
            }
            val steps = steps(change)
            if (steps.isEmpty()) continue
            if (change.counter in counted) {
                later += steps
            } else {
                current += steps.first()
                counted += change.counter
                later += steps.drop(1)
            }
        }
        // The remaining steps fill the open batch where they can, then batches of their own.
        while (later.isNotEmpty()) {
            val rest = mutableListOf<CounterChange>()
            for (step in later) {
                if (current.size < limit && step.counter !in counted) {
                    current += step
                    counted += step.counter
                } else {
                    rest += step
                }
            }
            if (rest.size == later.size) next()
            later = rest
        }
        next()
        return batches
    }

    private fun steps(change: CounterChange): List<CounterChange> {
        val step = if (change.delta > 0) 1 else -1
        return List(abs(change.delta)) { change.withDelta(step) }
    }
}
