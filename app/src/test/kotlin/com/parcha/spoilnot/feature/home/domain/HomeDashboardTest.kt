package com.parcha.spoilnot.feature.home.domain

import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
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
    fun `needs attention is expired and expiring within 3 days, soonest first`() {
        val s = HomeDashboard.summary(pantry, emptyList(), emptyList(), TODAY)
        assertEquals(listOf("Yogurt", "Milk", "Cheese"), s.attention.map { it.name })
        assertEquals(1, s.expiredCount)
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
