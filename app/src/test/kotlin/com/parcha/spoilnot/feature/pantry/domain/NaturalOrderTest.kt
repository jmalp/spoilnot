package com.parcha.spoilnot.feature.pantry.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {
    @Test
    fun `numbers sort by value, letters ignore case and accents`() {
        val names = listOf("Item 100", "item 20", "Item 3", "Bag 10", "bag 2", "Éclair", "eggs", "Apples")
        assertEquals(
            listOf("Apples", "bag 2", "Bag 10", "Éclair", "eggs", "Item 3", "item 20", "Item 100"),
            names.sortedWith(NaturalOrder),
        )
    }

    @Test
    fun `long numbers and leading zeros do not overflow or reorder`() {
        assertEquals(
            listOf("Lot 007", "Lot 12", "Lot 99999999999999999999"),
            listOf("Lot 99999999999999999999", "Lot 12", "Lot 007").sortedWith(NaturalOrder),
        )
    }
}
