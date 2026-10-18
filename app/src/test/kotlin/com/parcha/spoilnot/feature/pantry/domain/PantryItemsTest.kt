package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One row per purchase (beta feedback). */
class PantryItemsTest {
    private val products = listOf(
        product("milk"),
        product("rice", location = Location.Pantry, category = Category.PantryStaples, min = 5.0),
        product("oil", name = "Olive oil", min = 1.0, location = Location.Pantry),
        product("soup", category = Category.Cooked),
    )
    private val batches = listOf(
        batch("m1", "milk", inDays = 1),
        batch("m2", "milk", inDays = 8, location = Location.Freezer, addedSecondsAgo = 10),
        batch("r1", "rice", quantity = 2.0, inDays = 200, location = Location.Pantry, addedSecondsAgo = 30),
        batch("s1", "soup", inDays = -1),
        batch("gone", "milk", quantity = 0.0, inDays = 2),
    )

    private fun keys(query: PantryQuery = PantryQuery()) =
        PantryItems.build(products, batches, query, TODAY).flatMap { it.items }.map { it.key }

    @Test
    fun `each purchase is its own row, soonest first, out staples last, empty batches hidden`() {
        assertEquals(listOf("s1", "m1", "m2", "r1", "out-oil"), keys())
    }

    @Test
    fun `filters work per purchase`() {
        assertEquals(listOf("m2"), keys(PantryQuery(filter = PantryFilter.Freezer)))
        assertEquals(listOf("m1"), keys(PantryQuery(filter = PantryFilter.ExpiringSoon)))
        assertEquals(listOf("s1"), keys(PantryQuery(filter = PantryFilter.Expired)))
        assertEquals(listOf("r1", "out-oil"), keys(PantryQuery(filter = PantryFilter.LowStock)))
        assertEquals(listOf("r1", "out-oil"), keys(PantryQuery(filter = PantryFilter.Pantry)))
    }

    @Test
    fun `search, sort and grouping`() {
        assertEquals(listOf("m1", "m2"), keys(PantryQuery(search = "MILK")))
        assertEquals(listOf("m1", "m2", "out-oil", "r1", "s1"), keys(PantryQuery(sort = PantrySort.Name)))
        assertEquals(listOf("m1", "s1", "m2", "r1", "out-oil"), keys(PantryQuery(sort = PantrySort.RecentlyAdded)))
        val groups = PantryItems.build(products, batches, PantryQuery(grouping = PantryGrouping.Category), TODAY)
        assertTrue(groups.any { it.category == Category.Cooked && it.items.single().key == "s1" })
    }
}
