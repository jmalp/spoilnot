package com.parcha.spoilnot.core.write

import org.junit.Assert.assertEquals
import org.junit.Test

class WriteBatchesTest {
    private data class Plain(val id: Int) : HouseholdChange

    private data class Count(override val counter: String, override val delta: Int) : CounterChange {
        override fun withDelta(delta: Int) = copy(delta = delta)
    }

    @Test
    fun `changes without counts are chunked in order at the limit`() {
        val changes = (1..9).map { Plain(it) }
        assertEquals(listOf(changes.take(4), changes.subList(4, 8), changes.drop(8)), WriteBatches.plan(changes, 4))
        assertEquals(emptyList<List<HouseholdChange>>(), WriteBatches.plan(emptyList()))
    }

    @Test
    fun `a count of three goes as one step with its changes and two batches of one step after`() {
        val plan = WriteBatches.plan(listOf(Plain(1), Plain(2), Count("2026-11", 3)))
        assertEquals(
            listOf(
                listOf(Plain(1), Plain(2), Count("2026-11", 1)),
                listOf(Count("2026-11", 1)),
                listOf(Count("2026-11", 1)),
            ),
            plan,
        )
    }

    @Test
    fun `an undo takes the count back one step at a time`() {
        assertEquals(
            listOf(listOf(Plain(1), Count("m", -1)), listOf(Count("m", -1))),
            WriteBatches.plan(listOf(Plain(1), Count("m", -2))),
        )
    }

    @Test
    fun `two counts of one document never share a batch, counts of different documents may`() {
        val plan = WriteBatches.plan(
            listOf(Plain(1), Count("m", 1), Plain(2), Count("m", 1), Count("n", 1), Count("m", 1)),
        )
        assertEquals(
            listOf(
                listOf(Plain(1), Count("m", 1), Plain(2), Count("n", 1)),
                listOf(Count("m", 1)),
                listOf(Count("m", 1)),
            ),
            plan,
        )
        plan.forEach { batch ->
            val counters = batch.filterIsInstance<Count>().map { it.counter }
            assertEquals(counters.distinct(), counters)
        }
    }

    @Test
    fun `steps after the first batch also respect the limit, and a zero count writes nothing`() {
        val plan = WriteBatches.plan(listOf(Count("a", 2), Count("b", 2), Count("c", 2), Count("z", 0)), limit = 2)
        assertEquals(
            listOf(
                listOf(Count("a", 1), Count("b", 1)),
                listOf(Count("c", 1), Count("a", 1)),
                listOf(Count("b", 1), Count("c", 1)),
            ),
            plan,
        )
        assertEquals(6, plan.sumOf { it.size })
    }
}
