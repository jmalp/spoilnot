package com.parcha.spoilnot.feature.home.domain

import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDashboardTest {
    private val pantry = PantryData(
        products = listOf(
            product("milk"),
            product("yogurt"),
            product("rice"),
            product("eggs", min = 6.0),
            product("oil", "Olive oil", min = 1.0),
            product("cheese"),
        ),
        batches = listOf(
            batch("b1", "milk", inDays = 1),
            batch("b2", "yogurt", inDays = -2),
            batch("b3", "rice", inDays = 90),
            batch("b4", "eggs", quantity = 2.0, inDays = 20),
            batch("b5", "cheese", inDays = 3),
            batch("b6", "cheese", inDays = 10),
        ),
        fromCache = false,
    )

    private fun groceryItem(id: String, checked: Boolean) = GroceryItem(
        id, id, id, null, null, null, null, checked, null, ItemSource.Manual, null, null, null, null,
    )

    @Test
    fun `needs attention lists what can still be used first and counts the expired apart (UX-01)`() {
        val s = HomeDashboard.summary(pantry, emptyList(), emptyList(), TODAY)
        assertEquals(listOf("Milk", "Cheese"), s.attention.usable.map { it.name })
        assertEquals(listOf("Yogurt"), s.attention.expired.map { it.name })
        assertEquals(3, s.attention.size)
    }

    @Test
    fun `a backlog of expired food does not push out what can still be used`() {
        val backlog = pantry.copy(
            products = pantry.products + (1..8).map { product("old$it") },
            batches = pantry.batches + (1..8).map { batch("o$it", "old$it", inDays = -it.toLong()) },
        )
        val s = HomeDashboard.summary(backlog, emptyList(), emptyList(), TODAY)
        assertEquals(listOf("Milk", "Cheese"), s.attention.usable.map { it.name })
        assertEquals(9, s.attention.expired.size)
    }

    @Test
    fun `a new household is told how to start, not that all is done (UX-07)`() {
        val empty = HomeDashboard.summary(PantryData(emptyList(), emptyList(), false), emptyList(), emptyList(), TODAY)
        assertTrue(empty.pantryEmpty)
        assertTrue(empty.noStaples)
        val s = HomeDashboard.summary(pantry, emptyList(), emptyList(), TODAY)
        assertFalse(s.pantryEmpty)
        assertFalse(s.noStaples)
    }

    @Test
    fun `low stock lists staples below Keep at least, out of stock first`() {
        val s = HomeDashboard.summary(pantry, emptyList(), emptyList(), TODAY)
        assertEquals(listOf("Olive oil", "Eggs"), s.lowStock.map { it.name })
        assertEquals(listOf(true, false), s.lowStock.map { it.isOut })
    }

    @Test
    fun `grocery count is the items still to buy`() {
        val items = listOf(groceryItem("a", false), groceryItem("b", true), groceryItem("c", false))
        assertEquals(2, HomeDashboard.summary(pantry, items, emptyList(), TODAY).groceryCount)
    }
}
