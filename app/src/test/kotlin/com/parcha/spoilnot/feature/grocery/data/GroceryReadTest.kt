package com.parcha.spoilnot.feature.grocery.data

import com.parcha.spoilnot.core.firebase.fakeSnapshot
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.toItem
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.toList
import org.junit.Assert.assertEquals
import org.junit.Test

class GroceryReadTest {
    @Test
    fun `item names are cut to 80 characters and list names to 60`() {
        assertEquals("E".repeat(80), fakeSnapshot("i1", mapOf("name" to "E".repeat(10_000))).toItem().name)
        assertEquals("L".repeat(60), fakeSnapshot("g1", mapOf("name" to "L".repeat(10_000))).toList().name)
        assertEquals("Eggs", fakeSnapshot("i2", mapOf("name" to "Eggs")).toItem().name)
    }
}
