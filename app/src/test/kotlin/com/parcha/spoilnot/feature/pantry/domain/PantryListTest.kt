package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's per-product rows (Running low). The Pantry tab's list is [PantryItemsTest]. */
class PantryListTest {
    private val milk = product("milk")
    private val yogurt = product("yogurt", min = 5.0)
    private val oil = product("oil", name = "Olive oil", min = 1.0, location = Location.Pantry)
    private val products = listOf(milk, yogurt, oil)
    private val batches = listOf(
        batch("m1", "milk", quantity = 2.0),
        batch("y1", "yogurt", quantity = 1.0),
        batch("y2", "yogurt", quantity = 2.0, location = Location.Freezer),
    )

    @Test
    fun `one row per product with stock plus staples at zero marked out`() {
        val rows = PantryList.rows(products, batches)
        assertEquals(listOf("Milk", "Yogurt", "Olive oil"), rows.map { it.name })
        val oilRow = rows.single { it.name == "Olive oil" }
        assertTrue(oilRow.isOut)
        assertTrue(oilRow.isLow)
        assertEquals(0.0, oilRow.total, 0.0)
    }

    @Test
    fun `a row totals every location, and low is below keep at least`() {
        val rows = PantryList.rows(products, batches)
        val yogurtRow = rows.single { it.name == "Yogurt" }
        assertEquals(3.0, yogurtRow.total, 0.0)
        assertTrue(yogurtRow.isLow)
        assertFalse(yogurtRow.isOut)
        assertFalse(rows.single { it.name == "Milk" }.isLow)
    }

    @Test
    fun `empty batches are hidden and a product with only empty batches disappears`() {
        val withEmpty = batches + batch("m0", "milk", quantity = 0.0) + batch("x0", "ghost", quantity = -1.0)
        val rows = PantryList.rows(products, withEmpty)
        assertFalse(rows.any { it.productId == "ghost" })
        assertEquals(2.0, rows.single { it.name == "Milk" }.total, 0.0)
    }

    @Test
    fun `a batch whose product is not loaded yet still shows with its own name`() {
        val rows = PantryList.rows(emptyList(), listOf(batch("b1", "p9", name = "Eggs")))
        assertEquals("Eggs", rows.single().name)
        assertFalse(rows.single().isOut)
        assertFalse(rows.single().isLow)
    }
}
