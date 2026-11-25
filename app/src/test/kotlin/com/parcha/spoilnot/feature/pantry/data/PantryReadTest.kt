package com.parcha.spoilnot.feature.pantry.data

import com.parcha.spoilnot.core.firebase.fakeSnapshot
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.toBatch
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.toEvent
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.toProduct
import org.junit.Assert.assertEquals
import org.junit.Test

/** Names from another client are cut to DESIGN 8.1's lengths, so a very long one cannot slow Home and Pantry. */
class PantryReadTest {
    private val long = "M".repeat(500_000)

    @Test
    fun `product and batch names are cut to 80 characters`() {
        assertEquals("M".repeat(80), fakeSnapshot("p1", mapOf("name" to long)).toProduct().name)
        assertEquals("M".repeat(80), fakeSnapshot("s1", mapOf("productName" to long)).toBatch().productName)
        assertEquals("Milk", fakeSnapshot("p2", mapOf("name" to "Milk")).toProduct().name)
    }

    @Test
    fun `an event's author name is cut to 40 characters`() {
        val event = fakeSnapshot("a1", mapOf("type" to "discarded", "actorName" to long)).toEvent()
        assertEquals("M".repeat(40), event?.actorName)
    }
}
