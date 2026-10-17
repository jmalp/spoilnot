package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DESIGN 9.6 requires tests for: crossing down, recovery then dropping again, the user deleting the item, two
 * concurrent evaluations, a product without a minimum, a minimum raised while already low, and batches with
 * quantity 0. The engine is applied to a small simulated database so whole sequences can be checked.
 */
class RestockEngineTest {
    private val list = "g1"

    /** A tiny model of the household: products, batches and the default list's items. */
    private class World(var products: List<Product>, var batches: List<StockBatch>) {
        val items = mutableMapOf<String, Map<String, Any?>>()
        var writes = 0

        val data get() = PantryData(products, batches, fromCache = false)

        fun apply(plan: List<HouseholdChange>) {
            plan.forEach { change ->
                writes++
                when (change) {
                    is PantryChange.SetRestockArmed ->
                        products =
                            products.map { if (it.id == change.productId) it.copy(restockArmed = change.armed) else it }

                    is GroceryChange.UpsertLowStock -> {
                        val id = GroceryRules.lowStockItemId(change.productId)
                        val before = items[id].orEmpty()
                        items[id] = before + mapOf("quantity" to change.quantity, "name" to change.name)
                    }

                    else -> error("unexpected $change")
                }
            }
        }

        fun stock(id: String, quantity: Double) {
            batches = batches.filter { it.id != id } + batch(id, "milk", quantity = quantity)
        }
    }

    private fun milk(min: Double? = 2.0, armed: Boolean = true, buy: Double? = null) =
        product("milk", min = min).copy(restockArmed = armed, buyQuantity = buy)

    private fun World.run() = apply(RestockEngine.plan(data, list, items.keys))

    @Test
    fun `crossing below the minimum adds the item once and disarms`() {
        val world = World(listOf(milk()), listOf(batch("b1", "milk", quantity = 3.0)))
        world.run()
        assertTrue(world.items.isEmpty())

        world.stock("b1", 1.0)
        world.run()
        assertEquals(mapOf("quantity" to 1.0, "name" to "Milk"), world.items["low_milk"])
        assertFalse(world.products.single().restockArmed)

        val writes = world.writes
        world.run()
        world.stock("b1", 0.5)
        world.run()
        assertEquals("no nagging while disarmed", writes, world.writes)
    }

    @Test
    fun `buy quantity wins, otherwise minimum minus stock with at least 1`() {
        assertEquals(5.0, RestockEngine.buyQuantity(milk(min = 4.0, buy = 5.0), 1.0), 0.0)
        assertEquals(3.0, RestockEngine.buyQuantity(milk(min = 4.0), 1.0), 0.0)
        assertEquals(1.0, RestockEngine.buyQuantity(milk(min = 1.5), 1.0), 0.0)
    }

    @Test
    fun `recovery re-arms and the next drop adds again`() {
        val world = World(listOf(milk()), listOf(batch("b1", "milk", quantity = 1.0)))
        world.run()
        world.items.clear() // bought and moved to the pantry
        world.stock("b1", 4.0)
        world.run()
        assertTrue(world.products.single().restockArmed)
        assertTrue(world.items.isEmpty())

        world.stock("b1", 1.0)
        world.run()
        assertTrue(world.items.containsKey("low_milk"))
    }

    @Test
    fun `a deleted item is not recreated until stock recovers`() {
        val world = World(listOf(milk()), listOf(batch("b1", "milk", quantity = 1.0)))
        world.run()
        world.items.remove("low_milk") // the user deleted it
        repeat(3) { world.run() }
        assertTrue(world.items.isEmpty())
        world.stock("b1", 0.5)
        world.run()
        assertTrue("still below, still disarmed", world.items.isEmpty())
    }

    @Test
    fun `two phones evaluating the same snapshot write identical data`() {
        val products = listOf(milk())
        val batches = listOf(batch("b1", "milk", quantity = 1.0))
        val phoneA = RestockEngine.plan(PantryData(products, batches, false), list, emptySet())
        val phoneB = RestockEngine.plan(PantryData(products, batches, false), list, emptySet())
        assertEquals(phoneA, phoneB)

        val world = World(products, batches)
        world.apply(phoneA)
        world.apply(phoneB)
        assertEquals(1, world.items.size)
        assertFalse(world.products.single().restockArmed)
    }

    @Test
    fun `a product without a minimum or with automatic add off is ignored`() {
        val none = World(listOf(milk(min = null)), emptyList())
        none.run()
        val off = World(listOf(milk().copy(autoAddToGrocery = false)), emptyList())
        off.run()
        assertEquals(0, none.writes + off.writes)
    }

    @Test
    fun `raising the minimum while already low adds the item`() {
        // PantryService re-arms the product when Keep at least goes up (DESIGN 9.6).
        val world = World(listOf(milk(min = 1.0, armed = false)), listOf(batch("b1", "milk", quantity = 1.0)))
        world.run()
        assertTrue(world.items.isEmpty())
        world.products = listOf(milk(min = 3.0, armed = true))
        world.run()
        assertEquals(2.0, world.items["low_milk"]!!["quantity"])
    }

    @Test
    fun `batches at zero or below do not count`() {
        val world = World(
            listOf(milk(min = 1.0)),
            listOf(batch("b1", "milk", quantity = 0.0), batch("b2", "milk", quantity = -2.0)),
        )
        world.run()
        assertTrue(world.items.containsKey("low_milk"))
        assertEquals(1.0, world.items["low_milk"]!!["quantity"])
    }

    @Test
    fun `created fields are written only for items missing from the cache`() {
        val data = PantryData(listOf(milk()), listOf(batch("b1", "milk", quantity = 1.0)), false)
        val fresh = RestockEngine.plan(data, list, emptySet()).filterIsInstance<GroceryChange.UpsertLowStock>().single()
        val known = RestockEngine.plan(
            data,
            list,
            setOf("low_milk"),
        ).filterIsInstance<GroceryChange.UpsertLowStock>().single()
        assertTrue(fresh.writeCreated)
        assertFalse(known.writeCreated)
    }

    @Test
    fun `without a default list nothing is disarmed`() {
        val data = PantryData(listOf(milk()), listOf(batch("b1", "milk", quantity = 1.0)), false)
        assertTrue(RestockEngine.plan(data, defaultListId = null, cachedLowStockIds = emptySet()).isEmpty())
    }

    @Test
    fun `back in stock shows for an unchecked low-stock item once stock recovers`() {
        val item = GroceryItem(
            id = "low_milk",
            name = "Milk",
            nameKey = "milk",
            quantity = 1.0,
            unit = null,
            note = null,
            category = null,
            checked = false,
            checkedBy = null,
            source = ItemSource.LowStock,
            productId = "milk",
            recipeId = null,
            createdBy = null,
            createdAt = null,
        )
        val low = PantryData(listOf(milk()), listOf(batch("b1", "milk", quantity = 1.0)), false)
        val back = PantryData(listOf(milk()), listOf(batch("b1", "milk", quantity = 2.0)), false)
        assertFalse(RestockEngine.isBackInStock(item, low))
        assertTrue(RestockEngine.isBackInStock(item, back))
        assertFalse(RestockEngine.isBackInStock(item.copy(checked = true), back))
        assertFalse(RestockEngine.isBackInStock(item.copy(source = ItemSource.Manual), back))
    }
}
