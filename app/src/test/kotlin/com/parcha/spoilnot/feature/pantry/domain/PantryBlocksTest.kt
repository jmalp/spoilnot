package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list as the screen draws it (DESIGN 19.12): Use first and Everything else in the default view only. */
class PantryBlocksTest {
    private val products = listOf(
        product("milk"),
        product("rice", location = Location.Pantry, category = Category.PantryStaples, min = 5.0),
        product("oil", name = "Olive oil", min = 1.0, location = Location.Pantry),
        product("soup", category = Category.Cooked),
    )
    private val batches = listOf(
        batch("m1", "milk", inDays = 1),
        batch("m2", "milk", inDays = 8, location = Location.Freezer),
        batch("r1", "rice", quantity = 2.0, inDays = 200, location = Location.Pantry),
        batch("s1", "soup", inDays = -1),
        batch("y1", "milk", inDays = 3),
        batch("y2", "milk", inDays = 4),
    )

    private fun blocks(
        batches: List<StockBatch> = this.batches,
        query: PantryQuery = PantryQuery(),
        showExpired: Boolean = false,
    ) = PantryBlocks.of(PantryItems.build(products, batches, query, TODAY), query, showExpired)

    private fun PantryBlock.keys() = lines.map { it.key }

    @Test
    fun `the default view splits into Use first and Everything else, keeping the list's order`() {
        val list = blocks()
        assertEquals(listOf(PantryHeading.UseFirst, PantryHeading.EverythingElse), list.map { it.heading })
        // Expired, tomorrow and in 3 days need attention; in 4 days does not (NeedsAttention.DAYS).
        assertEquals(listOf("s1", "m1", "y1"), list[0].keys())
        assertEquals(listOf("y2", "m2", "r1", "out-oil"), list[1].keys())
        assertEquals(listOf(PantryBlocks.USE_FIRST, PantryBlocks.EVERYTHING_ELSE), list.map { it.key })
    }

    @Test
    fun `with nothing needing attention there are no headings`() {
        val list = blocks(batches.filter { it.id in setOf("m2", "r1", "y2") })
        assertEquals(1, list.size)
        assertEquals(null, list.single().heading)
        assertEquals(listOf("y2", "m2", "r1", "out-oil"), list.single().keys())
    }

    @Test
    fun `when everything needs attention there is only Use first`() {
        // No staples, so no Out rows.
        val groups = PantryItems.build(
            listOf(product("milk"), product("soup")),
            batches.filter { it.id in setOf("m1", "s1") },
            PantryQuery(),
            TODAY,
        )
        val list = PantryBlocks.of(groups, PantryQuery(), showExpired = false)
        assertEquals(listOf(PantryHeading.UseFirst), list.map { it.heading })
        assertEquals(listOf("s1", "m1"), list.single().keys())
    }

    @Test
    fun `the expired backlog folds into the first row of Use first and opens in place`() {
        val old = (2..4).map { batch("x$it", "milk", inDays = -it.toLong()) }
        val folded = blocks(batches + old)
        assertEquals(PantryHeading.UseFirst, folded[0].heading)
        assertEquals(PantryLine.Folded(4), folded[0].lines.first())
        assertEquals(listOf("expired-fold", "m1", "y1"), folded[0].keys())

        val shown = blocks(batches + old, showExpired = true)
        assertEquals(listOf("expired-fold", "x4", "x3", "x2", "s1", "m1", "y1"), shown[0].keys())
        assertEquals(listOf("y2", "m2", "r1", "out-oil"), shown[1].keys())
    }

    @Test
    fun `a fold with nothing else to use first is Use first's only row`() {
        val old = (2..4).map { batch("x$it", "milk", inDays = -it.toLong()) }
        val list = blocks(old + batches.filter { it.id == "r1" })
        assertEquals(listOf("expired-fold"), list[0].keys())
        assertEquals(PantryHeading.EverythingElse, list[1].heading)
    }

    @Test
    fun `any other view keeps its order without the two groups`() {
        listOf(
            PantryQuery(sort = PantrySort.Name),
            PantryQuery(sort = PantrySort.RecentlyAdded),
            PantryQuery(search = "milk"),
            PantryQuery(filter = PantryFilter.Fridge),
            PantryQuery(filter = PantryFilter.NeedsAttention),
        ).forEach { query ->
            assertFalse(query.isDefaultView)
            val list = blocks(query = query)
            assertTrue("$query", list.none { it.heading == PantryHeading.UseFirst })
            assertEquals("$query", listOf(null), list.map { it.heading })
        }
        assertTrue(PantryQuery().isDefaultView)
        assertTrue(PantryQuery(search = "  ").isDefaultView)
    }

    @Test
    fun `grouping keeps its headings, with the fold as a block of its own at the top`() {
        val old = (2..4).map { batch("x$it", "milk", inDays = -it.toLong()) }
        val query = PantryQuery(grouping = PantryGrouping.Location)
        val list = blocks(batches + old, query)
        assertEquals(PantryBlocks.EXPIRED, list[0].key)
        assertEquals(null, list[0].heading)
        assertEquals(listOf("expired-fold"), list[0].keys())
        assertEquals(
            listOf(
                PantryHeading.At(Location.Fridge),
                PantryHeading.At(Location.Freezer),
                PantryHeading.At(Location.Pantry),
            ),
            list.drop(1).map { it.heading },
        )
        assertEquals(listOf("expired-fold", "x4", "x3", "x2", "s1"), blocks(batches + old, query, true)[0].keys())

        val byCategory = blocks(query = PantryQuery(grouping = PantryGrouping.Category))
        assertTrue(byCategory.any { it.heading == PantryHeading.Kind(Category.Cooked) && it.keys() == listOf("s1") })
    }

    @Test
    fun `an empty list has no blocks`() {
        assertEquals(emptyList<PantryBlock>(), PantryBlocks.of(emptyList(), PantryQuery(), showExpired = false))
    }
}
