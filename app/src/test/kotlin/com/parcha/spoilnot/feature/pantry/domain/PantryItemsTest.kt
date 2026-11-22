package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One row per purchase (beta feedback). Also has the cases that still apply from v1's per-product list, deleted after
 * the 2026-11 review (CODE-01).
 */
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
        // Needs attention includes the expired (after v1, R8).
        assertEquals(listOf("s1", "m1"), keys(PantryQuery(filter = PantryFilter.NeedsAttention)))
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

    @Test
    fun `three or more expired fold into a header while browsing, not when searching or filtering to Expired`() {
        val old = (1..3).map { batch("x$it", "milk", inDays = -it.toLong()) }
        val groups = PantryItems.build(products, batches + old, PantryQuery(), TODAY)
        val fold = ExpiredFold.of(groups, PantryQuery())!!
        assertEquals(listOf("x3", "x2", "x1", "s1"), fold.expired.map { it.key })
        assertEquals(listOf("m1", "m2", "r1", "out-oil"), fold.rest.flatMap { it.items }.map { it.key })
        assertEquals(null, ExpiredFold.of(groups, PantryQuery(search = "milk")))
        assertEquals(null, ExpiredFold.of(groups, PantryQuery(filter = PantryFilter.Expired)))
        // Two are not a backlog: they stay in the list.
        assertEquals(
            null,
            ExpiredFold.of(PantryItems.build(products, batches + old.take(1), PantryQuery(), TODAY), PantryQuery()),
        )
    }

    @Test
    fun `group by location puts each purchase in its own place, an Out row at the product's usual place`() {
        val groups = PantryItems.build(products, batches, PantryQuery(grouping = PantryGrouping.Location), TODAY)
        assertEquals(listOf(Location.Fridge, Location.Freezer, Location.Pantry), groups.map { it.location })
        assertEquals(listOf("s1", "m1"), groups[0].items.map { it.key })
        assertEquals(listOf("m2"), groups[1].items.map { it.key })
        assertEquals(listOf("r1", "out-oil"), groups[2].items.map { it.key })
    }

    @Test
    fun `search ignores case and accents`() {
        val creme = product("creme", name = "Crème fraîche")
        val keys = PantryItems.build(
            products + creme,
            batches + batch("c1", "creme"),
            PantryQuery(search = " CREME"),
            TODAY,
        )
            .flatMap { it.items }.map { it.key }
        assertEquals(listOf("c1"), keys)
    }

    @Test
    fun `a purchase whose product is not loaded yet shows with its own name and is not low`() {
        val item = PantryItems.all(emptyList(), listOf(batch("b1", "p9", name = "Eggs")), TODAY).single()
        assertEquals("Eggs", item.name)
        assertEquals(Category.Dairy, item.category)
        assertFalse(item.isLow)
    }

    @Test
    fun `a purchase waiting for its server time is the newest, and keeps its pending mark`() {
        val pending = batch("new", "milk", inDays = 30).copy(createdAt = null, pendingSync = true)
        val items = PantryItems.build(products, batches + pending, PantryQuery(sort = PantrySort.RecentlyAdded), TODAY)
            .flatMap { it.items }
        assertEquals("new", items.first().key)
        assertTrue((items.first() as PantryItem.Purchase).batch.pendingSync)
    }

    @Test
    fun `500 purchases build quickly`() {
        val many = (1..500).map { batch("b$it", "p${it % 120}", inDays = (it % 20).toLong() - 3) }
        val manyProducts = (0 until 120).map { product("p$it", min = if (it % 10 == 0) 3.0 else null) }
        val start = System.nanoTime()
        repeat(20) {
            val groups = PantryItems.build(manyProducts, many, PantryQuery(), TODAY)
            PantryBlocks.of(groups, PantryQuery(), showExpired = false)
        }
        val perBuildMs = (System.nanoTime() - start) / 20 / 1_000_000.0
        assertTrue("took $perBuildMs ms", perBuildMs < 50)
    }
}
