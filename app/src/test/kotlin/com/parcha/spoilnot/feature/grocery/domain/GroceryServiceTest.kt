package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
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
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryServiceTest {
    private val writer = RecordingWriter()
    private val clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
    private val service = GroceryService(writer, NoRepository, PantryService(writer, clock))
    private val actor = Actor("u1", "Sam")
    private val empty = PantryData(emptyList(), emptyList(), false)

    private fun item(
        id: String,
        name: String,
        checked: Boolean = false,
        category: Category? = null,
        minutesAgo: Long = 0,
    ) = GroceryItem(
        id, name, name.lowercase(), null, null, null, category, checked, null, ItemSource.Manual, null, null, "u1",
        Instant.parse("2026-10-12T12:00:00Z").minusSeconds(minutesAgo * 60),
    )

    @Test
    fun `checking off is logged, unchecking is not`() = runTest {
        service.setChecked("h", actor, "g1", item("i1", "Milk"), true, "Groceries")
        assertEquals(
            listOf(
                GroceryChange.SetChecked("g1", "i1", true),
                GroceryChange.LogEvent("id0", GroceryEventType.Checked, "Milk", "Groceries"),
            ),
            writer.last,
        )
        service.setChecked("h", actor, "g1", item("i1", "Milk", checked = true), false, "Groceries")
        assertEquals(listOf(GroceryChange.SetChecked("g1", "i1", false)), writer.last)
    }

    @Test
    fun `quick add parses quantity and unit`() {
        assertEquals(QuickAdd("eggs", 12.0, null), QuickAddParser.parse("12 eggs"))
        assertEquals(QuickAdd("rice", 1.5, QuantityUnit.Kilograms), QuickAddParser.parse("1,5 kg rice"))
        assertEquals(QuickAdd("milk", 2.0, QuantityUnit.FluidOunces), QuickAddParser.parse("2 fl oz milk"))
        // A leading number is read as a quantity; a number alone is a name.
        assertEquals(QuickAdd("Up", 7.0, null), QuickAddParser.parse("  7 Up "))
        assertEquals(QuickAdd("12", null, null), QuickAddParser.parse("12"))
        assertEquals(QuickAdd("Greek yogurt", null, null), QuickAddParser.parse("Greek   yogurt"))
    }

    @Test
    fun `adding a name already on the list asks first`() = runTest {
        val eggs = item("i1", "Eggs")
        val result = service.quickAdd("h", actor, "g1", "EGGS", listOf(eggs), empty)
        assertTrue(result is QuickAddResult.AlreadyOnList)
        assertTrue(writer.last.isEmpty())

        // A checked one does not count; and "Add anyway" adds a second line.
        assertEquals(
            QuickAddResult.Added,
            service.quickAdd("h", actor, "g1", "eggs", listOf(eggs.copy(checked = true)), empty),
        )
        assertEquals(
            QuickAddResult.Added,
            service.quickAdd("h", actor, "g1", "eggs", listOf(eggs), empty, addAnyway = true),
        )
    }

    @Test
    fun `quick add links the pantry product for its category`() = runTest {
        val milk = product("milk", category = Category.Dairy)
        service.quickAdd(
            "h",
            actor,
            "g1",
            "2 milk",
            emptyList(),
            PantryData(listOf(milk), emptyList(), false),
            listName = "Groceries",
        )
        val created = (writer.last[0] as GroceryChange.CreateItem).item
        // Logged for Recent activity (DESIGN 5.6).
        val event = writer.last[1] as GroceryChange.LogEvent
        assertEquals(GroceryEventType.Added to "Milk", event.type to event.name)
        assertEquals("Groceries", event.listName)
        assertEquals("Milk", created.name)
        assertEquals("milk", created.productId)
        assertEquals(Category.Dairy, created.category)
        assertEquals(2.0, created.quantity!!, 0.0)
    }

    @Test
    fun `increase adds to an existing quantity or starts from one`() = runTest {
        service.increase("h", actor, "g1", item("i1", "Eggs").copy(quantity = 6.0), QuickAdd("eggs", 6.0, null))
        assertEquals(GroceryChange.AddToItem("g1", "i1", 6.0), writer.last.single())
        service.increase("h", actor, "g1", item("i1", "Eggs"), QuickAdd("eggs", null, null))
        assertEquals(2.0, (writer.last.single() as GroceryChange.UpdateItem).quantity!!, 0.0)
    }

    @Test
    fun `delete and clear checked can be undone`() = runTest {
        val eggs = item("i1", "Eggs")
        val undo = service.delete("h", actor, "g1", eggs)
        assertEquals(listOf(GroceryChange.RestoreItem("g1", eggs)), undo.changes)

        val cart = listOf(item("a", "A", checked = true), item("b", "B"), item("c", "C", checked = true))
        val cleared = service.clearChecked("h", actor, "g1", cart)!!
        assertEquals(listOf("a", "c"), writer.last.map { (it as GroceryChange.DeleteItem).itemId })
        assertEquals(2, cleared.changes.size)
        assertNull(service.clearChecked("h", actor, "g1", listOf(item("b", "B"))))
    }

    @Test
    fun `a renamed recipe item teaches its product the recipe name when it reaches the pantry`() = runTest {
        val onions = product("onion", "Onions")
        val data = PantryData(listOf(onions), emptyList(), false)
        val renamed = item("i1", "Onions").copy(aliasOf = "scallion")
        val newName = item("i2", "Green onions").copy(aliasOf = "spring onion")
        service.addToPantry(
            "h",
            actor,
            "g1",
            listOf(
                PantryEntry(renamed, 1.0, QuantityUnit.Pieces, TODAY.plusDays(7), Location.Fridge),
                PantryEntry(newName, 1.0, QuantityUnit.Pieces, TODAY.plusDays(7), Location.Fridge),
            ),
            emptyList(),
            data,
        )
        val changes = writer.last
        assertTrue(PantryChange.AddAlias("onion", "scallion") in changes)
        val created = changes.filterIsInstance<PantryChange.CreateProduct>().single().product
        assertEquals("Green onions", created.name)
        assertEquals(listOf("spring onion"), created.aliases)
        // Already known: nothing to add.
        val taught = onions.copy(aliases = listOf("scallion"))
        service.addToPantry(
            "h",
            actor,
            "g1",
            listOf(PantryEntry(renamed, 1.0, QuantityUnit.Pieces, TODAY.plusDays(7), Location.Fridge)),
            emptyList(),
            PantryData(listOf(taught), emptyList(), false),
        )
        assertTrue(writer.last.none { it is PantryChange.AddAlias })
    }

    @Test
    fun `add to pantry is one write with new and existing products, merged batches and items removed`() = runTest {
        val milk = product("milk", recent = listOf(7))
        val existing = batch("m1", "milk", quantity = 1.0, inDays = 7)
        val data = PantryData(listOf(milk), listOf(existing), false)
        val milkItem = item("i1", "Milk").copy(productId = "milk")
        val bread1 = item("i2", "Bread", category = Category.Bakery)
        val bread2 = item("i3", "bread")
        val skipped = item("i4", "Batteries")

        service.addToPantry(
            "h",
            actor,
            "g1",
            listOf(
                PantryEntry(milkItem, 2.0, QuantityUnit.Pieces, TODAY.plusDays(7), Location.Fridge),
                PantryEntry(bread1, 1.0, QuantityUnit.Pieces, TODAY.plusDays(3), Location.Pantry),
                PantryEntry(bread2, 1.0, QuantityUnit.Pieces, TODAY.plusDays(3), Location.Pantry),
            ),
            listOf(skipped),
            data,
        )

        assertEquals("one batched write", 1, writer.calls)
        val changes = writer.last
        assertEquals(PantryChange.AddToBatch("m1", 2.0), changes.filterIsInstance<PantryChange.AddToBatch>().first())
        val created = changes.filterIsInstance<PantryChange.CreateProduct>()
        assertEquals("the two breads share one new product", listOf("Bread"), created.map { it.product.name })
        assertEquals(Category.Bakery, created.single().product.category)
        val breadBatches = changes.filterIsInstance<PantryChange.CreateBatch>()
        assertEquals(1, breadBatches.size)
        assertEquals(
            2.0,
            breadBatches.single().batch.quantity +
                changes.filterIsInstance<PantryChange.AddToBatch>().filter {
                    it.batchId ==
                        breadBatches.single().batch.id
                }.sumOf { it.delta },
            0.0,
        )
        assertEquals(
            setOf("i1", "i2", "i3", "i4"),
            changes.filterIsInstance<GroceryChange.DeleteItem>().map {
                it.itemId
            }.toSet(),
        )
    }

    @Test
    fun `the default list cannot be deleted`() = runTest {
        try {
            service.deleteList("h", actor, GroceryList("g1", "Groceries", isDefault = true))
            throw AssertionError("expected a refusal")
        } catch (e: IllegalArgumentException) {
            assertTrue(writer.last.isEmpty())
        }
    }

    @Test
    fun `layout groups unchecked items by aisle in time order and puts checked ones in the cart`() {
        val view = GroceryLayout.of(
            listOf(
                item("1", "Spinach", category = Category.Produce, minutesAgo = 1),
                item("2", "Milk", category = Category.Dairy, minutesAgo = 5),
                item("3", "Eggs", category = Category.Dairy, minutesAgo = 9),
                item("4", "Batteries"),
                item("5", "Bread", checked = true),
            ),
        )
        assertEquals(listOf(Category.Produce, Category.Dairy, Category.Other), view.aisles.map { it.first })
        assertEquals(listOf("Eggs", "Milk"), view.aisles[1].second.map { it.name })
        assertEquals(listOf("Bread"), view.inCart.map { it.name })
    }

    @Test
    fun `suggestions come from pantry products and names on the list`() {
        val data = PantryData(listOf(product("milk"), product("oat", name = "Oat milk")), emptyList(), false)
        // Names that start with the text come first.
        assertEquals(
            listOf("Milk", "Millet", "Oat milk"),
            GroceryLayout.suggestions("mil", listOf(item("1", "Millet")), data),
        )
        assertTrue(GroceryLayout.suggestions("", emptyList(), data).isEmpty())
    }

    private class RecordingWriter : HouseholdWriter {
        var last: List<HouseholdChange> = emptyList()
        var calls = 0
        private var next = 0

        override fun newId() = "id${next++}"

        override suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>) {
            calls++
            last = changes
        }

        override val errors: Flow<AppError> = emptyFlow()
    }

    private object NoRepository : GroceryRepository {
        override fun observeLists(householdId: String): Flow<List<GroceryList>> = emptyFlow()

        override fun observeItems(householdId: String, listId: String): Flow<List<GroceryItem>> = emptyFlow()

        override suspend fun isCached(householdId: String, listId: String, itemId: String) = false

        override suspend fun itemIds(householdId: String, listId: String) = emptyList<String>()
    }
}
