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
    fun `checking off and unchecking are not logged (R15)`() = runTest {
        service.setChecked("h", actor, "g1", item("i1", "Milk"), true)
        assertEquals(listOf(GroceryChange.SetChecked("g1", "i1", true)), writer.last)
        service.setChecked("h", actor, "g1", item("i1", "Milk", checked = true), false)
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
    fun `a typed name without a product gets a guessed aisle`() = runTest {
        service.quickAdd("h", actor, "g1", "3 bananas", emptyList(), empty)
        val created = (writer.last[0] as GroceryChange.CreateItem).item
        assertEquals(Category.Produce, created.category)
        assertNull(created.productId)
        service.quickAdd("h", actor, "g1", "batteries", emptyList(), empty)
        assertNull((writer.last[0] as GroceryChange.CreateItem).item.category)
    }

    @Test
    fun `renaming an item relinks it to the product of its new name`() = runTest {
        val beans = product("beans", "Black beans", category = Category.PantryStaples)
        val item = item("i1", "Beans").copy(productId = "old")
        service.edit("h", actor, "g1", item, 2.0, null, " note ", null, "black  BEANS ", listOf(beans))
        assertEquals(
            listOf(
                GroceryChange.UpdateItem(
                    "g1",
                    "i1",
                    GroceryItemFields(null, null, null, null),
                    GroceryItemFields(2.0, null, "note", null),
                ),
                GroceryChange.RenameItem("g1", "i1", "Black beans", "black beans", "beans"),
            ),
            writer.last,
        )
        // A name no product has keeps the typed spelling and unlinks; the fields that didn't change aren't written.
        service.edit("h", actor, "g1", item, null, null, null, null, "Pinto beans", listOf(beans))
        assertEquals(listOf(GroceryChange.RenameItem("g1", "i1", "Pinto beans", "pinto beans", null)), writer.last)
        // Nothing changed: nothing is written.
        writer.last = emptyList()
        service.edit("h", actor, "g1", item, null, null, null, null, "Beans", listOf(beans))
        assertTrue(writer.last.isEmpty())
    }

    @Test
    fun `quick add reads the recipe parser's unit words, units after the number, and no amount for zero`() {
        // After v1, LOGIC-08.
        assertEquals(QuickAdd("apples", 3.0, QuantityUnit.Pounds), QuickAddParser.parse("3 lbs apples"))
        assertEquals(QuickAdd("apples", 3.0, QuantityUnit.Pounds), QuickAddParser.parse("3 lbs. apples"))
        assertEquals(QuickAdd("milk", 2.0, QuantityUnit.Liters), QuickAddParser.parse("2l milk"))
        assertEquals(QuickAdd("rice", 500.0, QuantityUnit.Grams), QuickAddParser.parse("500g rice"))
        assertEquals(QuickAdd("milk", 2.0, QuantityUnit.Liters), QuickAddParser.parse("2 Litres milk"))
        assertEquals(QuickAdd("flour", 1.0, QuantityUnit.Kilograms), QuickAddParser.parse("1 kilo flour"))
        assertEquals(QuickAdd("eggs", null, null), QuickAddParser.parse("0 eggs"))
        // A word that starts like a unit is a name; a name may not follow the number directly.
        assertEquals(QuickAdd("lemons", 2.0, null), QuickAddParser.parse("2 lemons"))
        assertEquals(QuickAdd("gherkins", 2.0, null), QuickAddParser.parse("2 gherkins"))
        assertEquals(QuickAdd("7up", null, null), QuickAddParser.parse("7up"))
        assertEquals(QuickAdd("kg", 2.0, null), QuickAddParser.parse("2 kg"))
        // Recipe-only units aren't pantry units.
        assertEquals(QuickAdd("cups flour", 2.0, null), QuickAddParser.parse("2 cups flour"))
    }

    @Test
    fun `increase quantity only adds in the same unit or when one side has none`() = runTest {
        val rice = item("i1", "Rice").copy(quantity = 500.0, unit = QuantityUnit.Grams)
        val kilos = QuickAddParser.parse("2 kg rice")
        assertTrue(!kilos.canIncrease(rice))
        assertTrue(QuickAddParser.parse("200 g rice").canIncrease(rice))
        assertTrue(QuickAddParser.parse("2 rice").canIncrease(rice))
        assertTrue(kilos.canIncrease(rice.copy(unit = null)))
        val refused = runCatching { service.increase("h", actor, "g1", rice, kilos) }
        assertTrue(refused.isFailure)
        assertEquals(0, writer.calls)
        service.increase("h", actor, "g1", rice, QuickAddParser.parse("200 g rice"))
        assertEquals(GroceryChange.AddToItem("g1", "i1", 200.0), writer.last.single())
    }

    @Test
    fun `increase adds to an existing quantity or starts from one`() = runTest {
        service.increase("h", actor, "g1", item("i1", "Eggs").copy(quantity = 6.0), QuickAdd("eggs", 6.0, null))
        assertEquals(GroceryChange.AddToItem("g1", "i1", 6.0), writer.last.single())
        service.increase("h", actor, "g1", item("i1", "Eggs"), QuickAdd("eggs", null, null))
        assertEquals(2.0, (writer.last.single() as GroceryChange.UpdateItem).after.quantity!!, 0.0)
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
    fun `add to pantry adds to the item's linked product after it was renamed`() = runTest {
        // After v1, LOGIC-03: "Milk" on the list, the product renamed "Whole milk" since.
        val milk = product("p1", "Whole milk")
        val data = PantryData(listOf(milk), emptyList(), false)
        val low = item("low_p1", "Milk").copy(productId = "p1", source = ItemSource.LowStock)
        service.addToPantry(
            "h",
            actor,
            "g1",
            listOf(PantryEntry(low, 2.0, QuantityUnit.Pieces, TODAY.plusDays(7), Location.Fridge)),
            emptyList(),
            data,
        )
        val changes = writer.last
        assertTrue(changes.none { it is PantryChange.CreateProduct })
        val batch = changes.filterIsInstance<PantryChange.CreateBatch>().single().batch
        assertEquals("p1", batch.productId)
        assertEquals("Whole milk", batch.productName)

        // A link to a product that is gone falls back to the name.
        val gone = item("i2", "Whole milk").copy(productId = "deleted")
        service.addToPantry(
            "h",
            actor,
            "g1",
            listOf(PantryEntry(gone, 1.0, QuantityUnit.Pieces, null, Location.Fridge)),
            emptyList(),
            data,
        )
        assertEquals("p1", writer.last.filterIsInstance<PantryChange.CreateBatch>().single().batch.productId)
    }

    @Test
    fun `a product with stock fixes the line's unit, and an amount in another unit isn't used`() {
        // After v1, LOGIC-04: "2 kg rice" on the list, Rice kept in grams with 500 g in stock.
        val rice = product("rice", unit = QuantityUnit.Grams).copy(lastAddedQuantity = 1000.0)
        val stock = batch("r1", "rice", quantity = 500.0)
        val data = PantryData(listOf(rice), listOf(stock), false)
        val kilos = item("i1", "Rice").copy(productId = "rice", quantity = 2.0, unit = QuantityUnit.Kilograms)

        val locked = AddToPantryRules.defaults(kilos, data, TODAY)
        assertEquals(QuantityUnit.Grams, locked.unit)
        assertTrue(locked.unitLocked)
        assertEquals("the product's last amount instead of 2", 1000.0, locked.quantity!!, 0.0)
        assertNull(
            "no last amount: the person types it",
            AddToPantryRules.defaults(kilos, data.copy(products = listOf(rice.copy(lastAddedQuantity = null))), TODAY)
                .quantity,
        )
        // In the product's unit, or without one, the item's amount is used.
        val grams = kilos.copy(quantity = 250.0, unit = QuantityUnit.Grams)
        assertEquals(250.0, AddToPantryRules.defaults(grams, data, TODAY).quantity!!, 0.0)
        assertEquals(
            3.0,
            AddToPantryRules.defaults(kilos.copy(quantity = 3.0, unit = null), data, TODAY).quantity!!,
            0.0,
        )
        // Without stock, the unit is free and starts from the item's.
        val free = AddToPantryRules.defaults(kilos, data.copy(batches = emptyList()), TODAY)
        assertEquals(QuantityUnit.Kilograms, free.unit)
        assertTrue(!free.unitLocked)
        assertEquals(2.0, free.quantity!!, 0.0)
    }

    @Test
    fun `undo of add to pantry removes what it added and puts the items back`() = runTest {
        // After v1, LOGIC-10.
        val milk = product("milk", recent = listOf(7))
        val existing = batch("m1", "milk", quantity = 1.0, inDays = 7)
        val data = PantryData(listOf(milk), listOf(existing), false)
        val milkItem = item("i1", "Milk", checked = true).copy(productId = "milk")
        val bread1 = item("i2", "Bread", checked = true)
        val bread2 = item("i3", "bread", checked = true)
        val skipped = item("i4", "Batteries", checked = true)

        val undo = service.addToPantry(
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
        )!!
        val written = writer.last
        val bread = written.filterIsInstance<PantryChange.CreateProduct>().single().product
        val breadBatch = written.filterIsInstance<PantryChange.CreateBatch>().single().batch
        val updated = written.filterIsInstance<PantryChange.UpdateProduct>().single { it.product.id == "milk" }
        val events = written.filterIsInstance<PantryChange.LogEvent>().map { it.eventId }

        val changes = undo.changes
        assertEquals(
            "the new batch goes; nothing is taken off a batch Undo removes anyway",
            listOf(breadBatch.id),
            changes.filterIsInstance<PantryChange.DeleteBatch>().map { it.batchId },
        )
        assertEquals(listOf(PantryChange.AddToBatch("m1", -2.0)), changes.filterIsInstance<PantryChange.AddToBatch>())
        assertEquals(
            listOf(PantryChange.DeleteProduct(bread.id)),
            changes.filterIsInstance<PantryChange.DeleteProduct>(),
        )
        assertEquals(
            "Milk gets back what the addition changed",
            listOf(PantryChange.UpdateProduct(milk, updated.product)),
            changes.filterIsInstance<PantryChange.UpdateProduct>(),
        )
        assertEquals(events, changes.filterIsInstance<PantryChange.DeleteEvent>().map { it.eventId })
        assertEquals(
            listOf(milkItem, bread1, bread2, skipped).map { GroceryChange.RestoreItem("g1", it) },
            changes.filterIsInstance<GroceryChange.RestoreItem>(),
        )
        assertEquals("nothing else", 1 + 1 + 1 + 1 + events.size + 4, changes.size)

        // Only skipped items: their Undo puts them back. Nothing at all: no write and no Undo.
        val removed = service.addToPantry("h", actor, "g1", emptyList(), listOf(skipped), data)!!
        assertEquals(listOf(GroceryChange.RestoreItem("g1", skipped)), removed.changes)
        val calls = writer.calls
        assertNull(service.addToPantry("h", actor, "g1", emptyList(), emptyList(), data))
        assertEquals(calls, writer.calls)
    }

    @Test
    fun `deleting a list deletes the list first, then its items for real`() = runTest {
        service.deleteList("h", actor, GroceryList("g2", "Party", isDefault = false))
        assertEquals(
            listOf(
                GroceryChange.DeleteList("g2"),
                GroceryChange.DeleteItem("g2", "i1", tombstone = false),
                GroceryChange.DeleteItem("g2", "i2", tombstone = false),
            ),
            writer.last,
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

        override fun observeCachedLists(householdId: String): Flow<List<GroceryList>> = emptyFlow()

        override fun observeItems(householdId: String, listId: String): Flow<List<GroceryItem>> = emptyFlow()

        override suspend fun isCached(householdId: String, listId: String, itemId: String) = false

        override suspend fun itemIds(householdId: String, listId: String) =
            if (listId == "g2") listOf("i1", "i2") else emptyList()
    }
}
