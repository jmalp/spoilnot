package com.parcha.spoilnot.core.write

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.firebase.FakeServer
import com.parcha.spoilnot.feature.grocery.data.GroceryChangeMapper
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.grocery.domain.GroceryService
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.pantry.data.PantryChangeMapper
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Add to pantry and its Undo through the real mappers (after v1, LOGIC-10): the Undo writes tombstones and merges,
 * never a real delete of a member's document, takes amounts off with increments, and brings the items back by
 * removing their tombstones only (the rules' "removed once" and creator checks, DESIGN 8.5 and 8.6).
 */
class AddToPantryUndoTest {
    private val server = FakeServer()
    private val alice = Actor("alice", "Alice")
    private val mappers = listOf(GroceryChangeMapper(), PantryChangeMapper())

    private val writer = object : HouseholdWriter {
        private var next = 0

        override fun newId() = "new${next++}"

        override suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>) {
            changes.forEach { change ->
                check(mappers.any { it.write(server.batch, server.db, householdId, actor, change) }) { "$change" }
            }
        }

        override val errors: Flow<AppError> = emptyFlow()
    }
    private val clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
    private val service = GroceryService(writer, NoLists, PantryService(writer, clock))

    private fun item(id: String, name: String, productId: String? = null) = GroceryItem(
        id = id, name = name, nameKey = name.lowercase(), quantity = 1.0, unit = QuantityUnit.Pieces, note = null,
        category = Category.Dairy, checked = true, checkedBy = "bob", source = ItemSource.Manual,
        productId = productId, recipeId = null, createdBy = "bob", createdAt = null,
    )

    @Test
    fun `undo writes tombstones, increments and restores only`() = runTest {
        val milk = product("milk", recent = listOf(7))
        val stock = batch("m1", "milk", quantity = 1.0, inDays = 7)
        val milkItem = item("i1", "Milk", productId = "milk")
        val bread = item("i2", "Bread")
        // What the server has before: the product, its batch and the two checked items Bob added.
        writer.apply("h1", alice, listOf(PantryChange.CreateProduct(milk), PantryChange.CreateBatch(stock)))
        writer.apply("h1", Actor("bob", "Bob"), listOf(milkItem, bread).map { GroceryChange.CreateItem("g1", it) })
        server.writes.clear()

        val undo = service.addToPantry(
            "h1",
            alice,
            "g1",
            listOf(
                PantryEntry(milkItem, 2.0, QuantityUnit.Pieces, TODAY.plusDays(7), Location.Fridge),
                PantryEntry(bread, 1.0, QuantityUnit.Pieces, TODAY.plusDays(3), Location.Pantry),
            ),
            emptyList(),
            PantryData(listOf(milk), listOf(stock), false),
        )!!
        val added = server.writes.toList()
        val newProduct = added.single { it.first.startsWith("households/h1/products/new") && it.second == "set" }.first
        val newBatch = added.single { it.first.startsWith("households/h1/stock/new") }.first
        server.writes.clear()

        writer.apply("h1", alice, undo.changes)
        val undone = server.writes.associate { it.first to (it.second to it.third) }

        // The new product and batch are tombstones now, merged into the documents.
        for (path in listOf(newProduct, newBatch)) {
            assertEquals("merge", undone.getValue(path).first)
            assertTrue("$path deleted", "deletedAt" in server.docs.getValue(path))
            assertEquals("its creator stays", "alice", server.docs.getValue(path)["createdBy"])
        }
        // The amount added to Milk's batch comes off as an increment, nothing else.
        val (kind, fields) = undone.getValue("households/h1/stock/m1")
        assertEquals("update", kind)
        assertEquals(setOf("quantity", "updatedAt", "updatedBy"), fields.keys)
        // Milk gets back only what the addition changed (its shelf-life record), not its name or settings.
        val (productKind, productFields) = undone.getValue("households/h1/products/milk")
        assertEquals("merge", productKind)
        assertFalse("name" in productFields)
        assertTrue("recentShelfLifeDays" in productFields)
        // The items come back as they were: in the cart, checked by Bob, created by Bob.
        for (id in listOf("i1", "i2")) {
            val path = "households/h1/groceryLists/g1/items/$id"
            assertEquals(setOf("deletedAt", "updatedAt", "updatedBy"), undone.getValue(path).second.keys)
            val doc = server.docs.getValue(path)
            assertFalse("deletedAt" in doc)
            assertEquals(true, doc["checked"])
            assertEquals("bob", doc["checkedBy"])
            assertEquals("bob", doc["createdBy"])
        }
        // The two activity events are removed (a member may remove their own).
        val deleted = server.writes.filter { it.second == "delete" }.map { it.first }
        assertEquals(2, deleted.size)
        assertTrue(deleted.all { it.startsWith("households/h1/activity/") })
    }

    private object NoLists : GroceryRepository {
        override fun observeLists(householdId: String): Flow<List<GroceryList>> = emptyFlow()

        override fun observeCachedLists(householdId: String): Flow<List<GroceryList>> = emptyFlow()

        override fun observeItems(householdId: String, listId: String): Flow<List<GroceryItem>> = emptyFlow()

        override suspend fun isCached(householdId: String, listId: String, itemId: String) = false

        override suspend fun itemIds(householdId: String, listId: String) = emptyList<String>()
    }
}
