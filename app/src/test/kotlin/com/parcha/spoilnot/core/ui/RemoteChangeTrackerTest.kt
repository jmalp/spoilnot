package com.parcha.spoilnot.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteChangeTrackerTest {
    private data class Row(val id: String, val value: Int, val pending: Boolean = false)

    private val tracker = RemoteChangeTracker<Row>({ it.id }, { it.pending }, { it.value })

    @Test
    fun `nothing on the first load`() {
        assertEquals(emptySet<String>(), tracker.update(listOf(Row("a", 1), Row("b", 2))))
    }

    @Test
    fun `this phone's writes pass through pending and are not highlighted`() {
        tracker.update(listOf(Row("a", 1)))
        assertEquals(emptySet<String>(), tracker.update(listOf(Row("a", 2, pending = true))))
        assertEquals(emptySet<String>(), tracker.update(listOf(Row("a", 2))))
        assertEquals(emptySet<String>(), tracker.update(listOf(Row("a", 2), Row("b", 1, pending = true))))
        assertEquals(emptySet<String>(), tracker.update(listOf(Row("a", 2), Row("b", 1))))
    }

    @Test
    fun `changes and additions from another phone are highlighted`() {
        tracker.update(listOf(Row("a", 1), Row("b", 1)))
        assertEquals(setOf("a", "c"), tracker.update(listOf(Row("a", 5), Row("b", 1), Row("c", 1))))
        // Unchanged afterwards: nothing new.
        assertEquals(emptySet<String>(), tracker.update(listOf(Row("a", 5), Row("b", 1), Row("c", 1))))
    }

    @Test
    fun `a new scope's first load is not highlighted, as another grocery list's rows aren't new`() {
        tracker.update(listOf(Row("a", 1)), scope = "list-1")
        // The old list's rows under the new list for a moment, then the new list's own rows.
        assertEquals(emptySet<String>(), tracker.update(listOf(Row("a", 1)), scope = "list-1"))
        assertEquals(emptySet<String>(), tracker.update(listOf(Row("b", 1), Row("c", 1)), scope = "list-2"))
        assertEquals(
            setOf("c", "d"),
            tracker.update(listOf(Row("b", 1), Row("c", 2), Row("d", 1)), scope = "list-2"),
        )
        // And back: the first list again is a first load too.
        assertEquals(emptySet<String>(), tracker.update(listOf(Row("a", 5)), scope = "list-1"))
    }
}
