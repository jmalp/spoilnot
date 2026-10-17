package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryListTest {
    private val milk = product("milk")
    private val yogurt = product("yogurt")
    private val rice = product("rice", location = Location.Pantry, category = Category.PantryStaples)
    private val oil =
        product("oil", name = "Olive oil", min = 1.0, location = Location.Pantry, category = Category.PantryStaples)
    private val products = listOf(milk, yogurt, rice, oil)
    private val batches = listOf(
        batch("m1", "milk", quantity = 2.0, inDays = 1),
        batch("y1", "yogurt", quantity = 1.0, inDays = 3, addedSecondsAgo = 10),
        batch("y2", "yogurt", quantity = 2.0, inDays = 9, addedSecondsAgo = 5),
        batch("y3", "yogurt", quantity = 1.0, inDays = 2, location = Location.Freezer, addedSecondsAgo = 50),
        batch("r1", "rice", quantity = 1.0, inDays = null, location = Location.Pantry, addedSecondsAgo = 100),
    )

    private fun build(query: PantryQuery = PantryQuery()) = PantryList.build(products, batches, query, TODAY)

    private fun names(query: PantryQuery = PantryQuery()) = build(query).flatMap { g -> g.rows.map { it.name } }

    @Test
    fun `one row per product with stock plus staples at zero marked out`() {
        val rows = PantryList.rows(products, batches, TODAY)
        assertEquals(setOf("Milk", "Yogurt", "Rice", "Olive oil"), rows.map { it.name }.toSet())
        val oilRow = rows.single { it.name == "Olive oil" }
        assertTrue(oilRow.isOut)
        assertTrue(oilRow.isLow)
        assertEquals(0.0, oilRow.total, 0.0)
        assertEquals(ExpiryStatus.NoDate, oilRow.status)
    }

    @Test
    fun `a row totals its batches and shows the soonest status`() {
        val yogurtRow = PantryList.rows(products, batches, TODAY).single { it.name == "Yogurt" }
        assertEquals(4.0, yogurtRow.total, 0.0)
        assertEquals(ExpiryStatus.Soon, yogurtRow.status)
        assertEquals(listOf("y3", "y1", "y2"), yogurtRow.batches.map { it.id })
    }

    @Test
    fun `empty batches are hidden and a product with only empty batches disappears`() {
        val withEmpty = batches + batch("m0", "milk", quantity = 0.0) + batch("x0", "ghost", quantity = -1.0)
        val rows = PantryList.rows(products, withEmpty, TODAY)
        assertFalse(rows.any { it.productId == "ghost" })
        assertEquals(listOf("m1"), rows.single { it.name == "Milk" }.batches.map { it.id })
    }

    @Test
    fun `default sort is soonest expiry with out-of-stock staples last`() {
        assertEquals(listOf("Milk", "Yogurt", "Rice", "Olive oil"), names())
    }

    @Test
    fun `sort by name and by recently added`() {
        assertEquals(listOf("Milk", "Olive oil", "Rice", "Yogurt"), names(PantryQuery(sort = PantrySort.Name)))
        assertEquals(listOf("Milk", "Yogurt", "Rice", "Olive oil"), names(PantryQuery(sort = PantrySort.RecentlyAdded)))
    }

    @Test
    fun `location filters keep only that location's batches and staples stored there`() {
        val fridge = build(PantryQuery(filter = PantryFilter.Fridge)).single().rows
        assertEquals(listOf("Milk", "Yogurt"), fridge.map { it.name })
        assertEquals(3.0, fridge.single { it.name == "Yogurt" }.total, 0.0)
        assertEquals(listOf("Yogurt"), names(PantryQuery(filter = PantryFilter.Freezer)))
        assertEquals(listOf("Rice", "Olive oil"), names(PantryQuery(filter = PantryFilter.Pantry)))
        assertEquals(emptyList<String>(), names(PantryQuery(filter = PantryFilter.Other)))
    }

    @Test
    fun `expiring soon is today to 3 days, expired is separate`() {
        val withExpired = batches + batch("m2", "milk", inDays = -2)
        val soon = PantryList.build(products, withExpired, PantryQuery(filter = PantryFilter.ExpiringSoon), TODAY)
        assertEquals(listOf("Milk", "Yogurt"), soon.flatMap { g -> g.rows.map { it.name } })
        assertEquals(listOf("m1"), soon.single().rows.first().batches.map { it.id })
        val expired = PantryList.build(products, withExpired, PantryQuery(filter = PantryFilter.Expired), TODAY)
        assertEquals(listOf("m2"), expired.single().rows.single().batches.map { it.id })
        assertEquals(ExpiryStatus.Expired, expired.single().rows.single().status)
    }

    @Test
    fun `low stock lists staples below keep at least`() {
        val yogurtStaple = yogurt.copy(minQuantity = 5.0)
        val low = PantryList.build(
            listOf(milk, yogurtStaple, rice, oil),
            batches,
            PantryQuery(filter = PantryFilter.LowStock),
            TODAY,
        )
        assertEquals(setOf("Yogurt", "Olive oil"), low.flatMap { g -> g.rows.map { it.name } }.toSet())
    }

    @Test
    fun `search matches names ignoring case and accents`() {
        val creme = product("creme", name = "Crème fraîche")
        val rows = PantryList.build(
            products + creme,
            batches + batch("c1", "creme"),
            PantryQuery(search = "CREME"),
            TODAY,
        )
        assertEquals(listOf("Crème fraîche"), rows.flatMap { g -> g.rows.map { it.name } })
        assertEquals(listOf("Yogurt"), names(PantryQuery(search = " yog")))
    }

    @Test
    fun `group by location splits a product across its locations`() {
        val groups = build(PantryQuery(grouping = PantryGrouping.Location))
        assertEquals(listOf(Location.Fridge, Location.Freezer, Location.Pantry), groups.map { it.location })
        assertEquals(listOf("Milk", "Yogurt"), groups[0].rows.map { it.name })
        assertEquals(1.0, groups[1].rows.single().total, 0.0)
        assertEquals(4.0, groups[1].rows.single().overallTotal, 0.0)
    }

    @Test
    fun `group by category`() {
        val groups = build(PantryQuery(grouping = PantryGrouping.Category))
        assertEquals(listOf(Category.Dairy, Category.PantryStaples), groups.map { it.category })
        assertEquals(listOf("Rice", "Olive oil"), groups[1].rows.map { it.name })
    }

    @Test
    fun `a batch whose product is not loaded yet still shows with its own name`() {
        val rows = PantryList.rows(emptyList(), listOf(batch("b1", "p9", name = "Eggs")), TODAY)
        assertEquals("Eggs", rows.single().name)
        assertFalse(rows.single().isOut)
    }

    @Test
    fun `pending writes mark the row`() {
        val rows = PantryList.rows(products, listOf(batch("m1", "milk").copy(pendingSync = true)), TODAY)
        assertTrue(rows.single { it.name == "Milk" }.pendingSync)
    }

    @Test
    fun `500 batches build quickly`() {
        val many = (1..500).map { batch("b$it", "p${it % 120}", inDays = (it % 20).toLong() - 3) }
        val manyProducts = (0 until 120).map { product("p$it") }
        val start = System.nanoTime()
        repeat(20) { PantryList.build(manyProducts, many, PantryQuery(), TODAY) }
        val perBuildMs = (System.nanoTime() - start) / 20 / 1_000_000.0
        assertTrue("took $perBuildMs ms", perBuildMs < 50)
    }
}
