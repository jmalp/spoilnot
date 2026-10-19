package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.insights.domain.StatsChange
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryServiceTest {
    private val repo = RecordingWriter()
    private val clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
    private val service = PantryService(repo, clock)
    private val actor = Actor("u1", "Sam")

    private fun draft(
        name: String = "Milk",
        quantity: Double = 1.0,
        inDays: Long? = 7,
        location: Location = Location.Fridge,
        unit: QuantityUnit = QuantityUnit.Pieces,
        min: Double? = null,
    ) = StockDraft(
        name, quantity, unit,
        inDays?.let {
            TODAY.plusDays(it)
        },
        DateType.UseBy, location, Category.Dairy, " ", min, true, null,
    )

    private fun data(products: List<Product> = emptyList(), batches: List<StockBatch> = emptyList()) =
        PantryData(products, batches, fromCache = false)

    @Test
    fun `opening a whole batch shortens its date, remembers the days and undoes`() = runTest {
        val milk = product("milk")
        val b = batch("m1", "milk", quantity = 1.0, inDays = 20)
        val undo = service.markOpened("h", actor, b, 1.0, 5, milk)
        assertEquals(
            listOf(
                PantryChange.MarkOpened("m1", TODAY, TODAY.plusDays(5)),
                PantryChange.SetOpenedShelfLife("milk", 5),
            ),
            repo.last,
        )
        assertEquals(listOf(PantryChange.MarkOpened("m1", null, TODAY.plusDays(20))), undo.changes)
    }

    @Test
    fun `opening never moves the date later, and a remembered number of days is not written again`() = runTest {
        val milk = product("milk").copy(openedShelfLifeDays = 7)
        service.markOpened("h", actor, batch("m1", "milk", inDays = 2), 1.0, 7, milk)
        assertEquals(listOf(PantryChange.MarkOpened("m1", TODAY, TODAY.plusDays(2))), repo.last)
        service.markOpened("h", actor, batch("m2", "milk", inDays = null), 1.0, 7, milk)
        assertEquals(listOf(PantryChange.MarkOpened("m2", TODAY, TODAY.plusDays(7))), repo.last)
    }

    @Test
    fun `opening some of a batch in pieces moves them to their own batch`() = runTest {
        val yogurts = batch("y1", "yogurt", quantity = 6.0, inDays = 14)
        val undo = service.markOpened("h", actor, yogurts, 1.0, 3, null)
        val part = (repo.last[1] as PantryChange.CreateBatch).batch
        assertEquals(PantryChange.AddToBatch("y1", -1.0), repo.last[0])
        assertEquals(1.0, part.quantity, 0.0)
        assertEquals(TODAY, part.openedOn)
        assertEquals(TODAY.plusDays(3), part.expiryDate)
        assertEquals(listOf(PantryChange.DeleteBatch(part.id), PantryChange.AddToBatch("y1", 1.0)), undo.changes)
    }

    @Test
    fun `new stock is not added to an opened batch`() = runTest {
        val opened = batch("m1", "milk", quantity = 1.0, inDays = 7).copy(openedOn = TODAY)
        service.add(
            "h",
            actor,
            draft(name = "Milk", quantity = 1.0, inDays = 7),
            data(listOf(product("milk")), listOf(opened)),
        )
        assertTrue(repo.last.any { it is PantryChange.CreateBatch })
        assertTrue(repo.last.none { it is PantryChange.AddToBatch })
    }

    @Test
    fun `using takes the batch that expires first, uses up whole batches and undoes everything`() = runTest {
        val soon = batch("e1", "egg", quantity = 2.0, inDays = 2)
        val later = batch("e2", "egg", quantity = 6.0, inDays = 9)
        val noDate = batch("e3", "egg", quantity = 1.0, inDays = null)
        val data = data(listOf(product("egg")), listOf(later, noDate, soon))
        val extra = PantryChange.DeleteEvent("x")
        val undo = service.use("h", actor, mapOf("egg" to 5.0), data, listOf(extra), listOf(extra))!!
        val month = YearMonth.from(TODAY)
        assertEquals(
            listOf(
                PantryChange.DeleteBatch("e1"),
                StatsChange.CountUsedUp(month, 1),
                PantryChange.AddToBatch("e2", -3.0),
                extra,
            ),
            repo.last,
        )
        assertEquals(
            listOf(
                PantryChange.RestoreBatch(soon),
                StatsChange.CountUsedUp(month, -1),
                PantryChange.AddToBatch("e2", 3.0),
                extra,
            ),
            undo.changes,
        )
    }

    @Test
    fun `using more than there is takes it all, and nothing to take writes nothing`() = runTest {
        val only = batch("m1", "milk", quantity = 1.0)
        service.use("h", actor, mapOf("milk" to 3.0), data(listOf(product("milk")), listOf(only)))
        assertEquals(PantryChange.DeleteBatch("m1"), repo.last.first())
        val before = repo.last
        assertNull(service.use("h", actor, mapOf("milk" to 1.0), data(listOf(product("milk")))))
        assertEquals(before, repo.last)
    }

    @Test
    fun `each add remembers its amount on the product, in the product's unit only`() = runTest {
        service.add("h", actor, draft(name = "Onions", quantity = 3.0, unit = QuantityUnit.Pounds), data())
        assertEquals(3.0, (repo.last[0] as PantryChange.CreateProduct).product.lastAddedQuantity!!, 0.0)
        val onions = product("onions", "Onions", unit = QuantityUnit.Pounds).copy(lastAddedQuantity = 3.0)
        service.add(
            "h",
            actor,
            draft(name = "Onions", quantity = 2.0, unit = QuantityUnit.Pounds),
            data(listOf(onions)),
        )
        assertEquals(2.0, (repo.last[0] as PantryChange.UpdateProduct).product.lastAddedQuantity!!, 0.0)
        // With stock the unit stays pounds, so an amount in pieces is not remembered.
        val stocked = data(listOf(onions), listOf(batch("o1", "onions")))
        service.add("h", actor, draft(name = "Onions", quantity = 5.0), stocked)
        val updated = repo.last.filterIsInstance<PantryChange.UpdateProduct>().singleOrNull()?.product
        assertEquals(3.0, (updated ?: onions).lastAddedQuantity!!, 0.0)
    }

    @Test
    fun `a new name creates a product, a batch and an added event`() = runTest {
        val result = service.add("h", actor, draft(name = "  Greek   yogurt "), data())
        val product = (repo.last[0] as PantryChange.CreateProduct).product
        assertEquals("Greek yogurt", product.name)
        assertEquals("greek yogurt", product.nameKey)
        assertEquals(listOf(7), product.recentShelfLifeDays)
        assertEquals(7, product.typicalShelfLifeDays)
        assertTrue(product.restockArmed)
        val created = (repo.last[1] as PantryChange.CreateBatch).batch
        assertEquals(product.id, created.productId)
        assertNull("blank note is stored as null", created.note)
        assertTrue(repo.last[2] is PantryChange.LogEvent)
        assertFalse(result.merged)
    }

    @Test
    fun `the same name in any case reuses the product`() = runTest {
        val milk = product("milk", recent = listOf(5))
        service.add("h", actor, draft(name = "MILK", inDays = 9), data(listOf(milk)))
        val update = repo.last.filterIsInstance<PantryChange.UpdateProduct>().single().product
        assertEquals("milk", update.id)
        assertEquals(listOf(5, 9), update.recentShelfLifeDays)
        assertEquals(7, update.typicalShelfLifeDays)
        assertTrue(repo.last.none { it is PantryChange.CreateProduct })
    }

    @Test
    fun `same product, date and location increments instead of duplicating`() = runTest {
        val milk = product("milk")
        val existing = batch("m1", "milk", quantity = 2.0, inDays = 7)
        val result = service.add("h", actor, draft(quantity = 3.0), data(listOf(milk), listOf(existing)))
        assertEquals(PantryChange.AddToBatch("m1", 3.0), repo.last.filterIsInstance<PantryChange.AddToBatch>().single())
        assertTrue(result.merged)
        assertEquals("m1", result.batchId)
    }

    @Test
    fun `a different date or location is a new batch`() = runTest {
        val milk = product("milk")
        val existing = batch("m1", "milk", inDays = 7)
        service.add("h", actor, draft(inDays = 8), data(listOf(milk), listOf(existing)))
        assertEquals(1, repo.last.filterIsInstance<PantryChange.CreateBatch>().size)
        service.add("h", actor, draft(location = Location.Freezer), data(listOf(milk), listOf(existing)))
        assertEquals(1, repo.last.filterIsInstance<PantryChange.CreateBatch>().size)
    }

    @Test
    fun `the unit stays fixed while the product has stock`() = runTest {
        val milk = product("milk")
        service.add(
            "h",
            actor,
            draft(unit = QuantityUnit.Liters, inDays = 8),
            data(listOf(milk), listOf(batch("m1", "milk"))),
        )
        assertEquals(QuantityUnit.Pieces, repo.last.filterIsInstance<PantryChange.CreateBatch>().single().batch.unit)
        service.add("h", actor, draft(unit = QuantityUnit.Liters), data(listOf(milk)))
        assertEquals(QuantityUnit.Liters, repo.last.filterIsInstance<PantryChange.CreateBatch>().single().batch.unit)
    }

    @Test
    fun `minus one at one uses the batch up, counts it, and undo takes both back`() = runTest {
        val b = batch("m1", "milk", quantity = 1.0)
        val undo = service.adjust("h", actor, b, -1.0)
        val month = YearMonth.from(TODAY)
        assertEquals(listOf(PantryChange.DeleteBatch("m1"), StatsChange.CountUsedUp(month, 1)), repo.last)
        service.undo("h", actor, undo!!)
        assertEquals(listOf(PantryChange.RestoreBatch(b), StatsChange.CountUsedUp(month, -1)), repo.last)
    }

    @Test
    fun `plus and minus one use increments`() = runTest {
        assertNull(service.adjust("h", actor, batch("m1", "milk", quantity = 2.0), -1.0))
        assertEquals(listOf(PantryChange.AddToBatch("m1", -1.0)), repo.last)
        service.adjust("h", actor, batch("m1", "milk", quantity = 2.0), 1.0)
        assertEquals(listOf(PantryChange.AddToBatch("m1", 1.0)), repo.last)
    }

    @Test
    fun `throw away deletes, logs the reason, and undo reverses both`() = runTest {
        val b = batch("m1", "milk", quantity = 2.0)
        val undo = service.throwAway("h", actor, b, DiscardReason.Spoiled)
        val event = repo.last[1] as PantryChange.LogEvent
        assertEquals(PantryChange.DeleteBatch("m1"), repo.last[0])
        assertEquals(PantryEvent.Type.Discarded, event.type)
        assertEquals(DiscardReason.Spoiled, event.reason)
        assertEquals(2.0, event.quantity!!, 0.0)
        val counted = StatsChange.CountThrownAway(YearMonth.from(TODAY), TODAY.dayOfMonth, DiscardReason.Spoiled, 1)
        assertEquals(counted, repo.last[2])
        assertEquals(
            listOf(
                PantryChange.RestoreBatch(b),
                PantryChange.DeleteEvent(event.eventId),
                counted.copy(delta = -1),
            ),
            undo.changes,
        )
    }

    @Test
    fun `throwing away several is one write, counted once, and one undo`() = runTest {
        val a = batch("m1", "milk", inDays = -1)
        val b = batch("y1", "yogurt", inDays = -3)
        val undo = service.throwAwayAll("h", actor, listOf(a, b), DiscardReason.Expired)!!
        val counted = StatsChange.CountThrownAway(YearMonth.from(TODAY), TODAY.dayOfMonth, DiscardReason.Expired, 2)
        assertEquals(5, repo.last.size)
        assertEquals(
            listOf(PantryChange.DeleteBatch("m1"), PantryChange.DeleteBatch("y1")),
            repo.last.filterIsInstance<PantryChange.DeleteBatch>(),
        )
        assertEquals(
            2,
            repo.last.filterIsInstance<PantryChange.LogEvent>().count {
                it.reason == DiscardReason.Expired
            },
        )
        assertEquals(counted, repo.last.last())
        assertEquals(
            listOf(PantryChange.RestoreBatch(a), PantryChange.RestoreBatch(b)),
            undo.changes.filterIsInstance<PantryChange.RestoreBatch>(),
        )
        assertEquals(counted.copy(delta = -2), undo.changes.last())
        assertNull(service.throwAwayAll("h", actor, emptyList(), DiscardReason.Expired))
    }

    @Test
    fun `editing to zero uses the batch up, moving keeps everything else`() = runTest {
        val b = batch("m1", "milk", quantity = 2.0)
        val undo = service.edit("h", actor, b, BatchEdit(0.0, null, DateType.UseBy, Location.Fridge, null))
        val month = YearMonth.from(TODAY)
        assertEquals(listOf(PantryChange.DeleteBatch("m1"), StatsChange.CountUsedUp(month, 1)), repo.last)
        assertEquals(listOf(PantryChange.RestoreBatch(b), StatsChange.CountUsedUp(month, -1)), undo!!.changes)
        service.move("h", actor, b, Location.Freezer)
        val edit = (repo.last.single() as PantryChange.UpdateBatch).edit
        assertEquals(BatchEdit(2.0, b.expiryDate, b.dateType, Location.Freezer, null), edit)
    }

    @Test
    fun `renaming a product updates all its batches in the same write`() = runTest {
        val milk = product("milk")
        val batches = (1..3).map { batch("m$it", "milk", inDays = it.toLong()) } + batch("o1", "oat")
        service.updateProduct("h", actor, milk, edit(milk, name = "Whole milk"), data(listOf(milk), batches))
        assertEquals("whole milk", (repo.last[0] as PantryChange.UpdateProduct).product.nameKey)
        assertEquals(
            listOf("m1", "m2", "m3"),
            repo.last.filterIsInstance<PantryChange.SyncBatchProduct>().map {
                it.batchId
            },
        )
    }

    @Test
    fun `a rename onto another product's name is a conflict`() = runTest {
        val milk = product("milk")
        val oat = product("oat", name = "Oat milk")
        try {
            service.updateProduct("h", actor, milk, edit(milk, name = "oat MILK"), data(listOf(milk, oat)))
            throw AssertionError("expected a conflict")
        } catch (e: AppError.Conflict) {
            assertTrue(repo.last.isEmpty())
        }
    }

    @Test
    fun `the unit can only change without stock, and raising keep at least re-arms restock`() = runTest {
        val milk = product("milk", min = 1.0).copy(restockArmed = false)
        service.updateProduct(
            "h",
            actor,
            milk,
            edit(milk, unit = QuantityUnit.Liters, min = 2.0),
            data(listOf(milk), listOf(batch("m1", "milk"))),
        )
        val updated = (repo.last[0] as PantryChange.UpdateProduct).product
        assertEquals(QuantityUnit.Pieces, updated.unit)
        assertTrue(updated.restockArmed)

        service.updateProduct("h", actor, milk, edit(milk, unit = QuantityUnit.Liters, min = 1.0), data(listOf(milk)))
        val again = (repo.last[0] as PantryChange.UpdateProduct).product
        assertEquals(QuantityUnit.Liters, again.unit)
        assertFalse("same minimum does not re-arm", again.restockArmed)
    }

    @Test
    fun `clearing keep at least turns automatic add off`() = runTest {
        val milk = product("milk", min = 2.0)
        service.updateProduct("h", actor, milk, edit(milk, min = null), data(listOf(milk)))
        val updated = (repo.last[0] as PantryChange.UpdateProduct).product
        assertNull(updated.minQuantity)
        assertFalse(updated.autoAddToGrocery)
    }

    @Test
    fun `empty batches are deleted unless a local write is pending`() = runTest {
        val data =
            data(
                batches = listOf(
                    batch("a", "milk", quantity = 0.0),
                    batch("b", "milk", quantity = 0.0).copy(pendingSync = true),
                    batch("c", "milk"),
                ),
            )
        service.deleteEmptyBatches("h", actor, data)
        assertEquals(listOf(PantryChange.DeleteBatch("a")), repo.last)
    }

    private fun edit(p: Product, name: String = p.name, unit: QuantityUnit = p.unit, min: Double? = p.minQuantity) =
        ProductEdit(
            name,
            p.category,
            p.defaultLocation,
            unit,
            min,
            autoAddToGrocery = true,
            buyQuantity = null,
            typicalShelfLifeDays = p.typicalShelfLifeDays,
        )

    private fun itemEdit(
        name: String = "Milk",
        quantity: Double = 2.0,
        unit: QuantityUnit = QuantityUnit.Pieces,
        category: Category = Category.Dairy,
        autoAdd: Boolean = false,
        min: Double? = null,
        buy: Double? = null,
    ) = ItemEdit(name, quantity, unit, category, TODAY.plusDays(4), Location.Freezer, autoAdd, min, buy)

    @Test
    fun `the edit window changes the purchase and its product in one write, keeping date type and note`() {
        val milk = product("milk")
        val b1 = batch("b1", "milk").copy(dateType = DateType.BestBefore, note = "Lactose free")
        val data = PantryData(listOf(milk), listOf(b1), false)
        val changes = service.planEdit(b1, itemEdit(name = "Oat milk", autoAdd = true, min = 2.0, buy = 4.0), data)
        val batch = changes.filterIsInstance<PantryChange.UpdateBatch>().single()
        assertEquals(
            BatchEdit(2.0, TODAY.plusDays(4), DateType.BestBefore, Location.Freezer, "Lactose free"),
            batch.edit,
        )
        val product = changes.filterIsInstance<PantryChange.UpdateProduct>().single().product
        assertEquals("Oat milk", product.name)
        assertEquals(2.0, product.minQuantity!!, 0.0)
        assertTrue(product.autoAddToGrocery)
        assertEquals(4.0, product.buyQuantity!!, 0.0)
        assertTrue(product.restockArmed)
        assertEquals(listOf("b1"), changes.filterIsInstance<PantryChange.SyncBatchProduct>().map { it.batchId })
    }

    @Test
    fun `turning auto-add off clears Keep at least and Buy quantity`() {
        val rice = product("rice", min = 5.0).copy(buyQuantity = 10.0)
        val b1 = batch("b1", "rice")
        val changes = service.planEdit(
            b1,
            itemEdit(name = "Rice", min = 5.0, buy = 10.0),
            PantryData(listOf(rice), listOf(b1), false),
        )
        val product = changes.filterIsInstance<PantryChange.UpdateProduct>().single().product
        assertNull(product.minQuantity)
        assertFalse(product.autoAddToGrocery)
        assertNull(product.buyQuantity)
    }

    @Test
    fun `the unit changes only when this is the product's only purchase`() {
        val rice = product("rice")
        val only = PantryData(listOf(rice), listOf(batch("b1", "rice")), false)
        val two = PantryData(listOf(rice), listOf(batch("b1", "rice"), batch("b2", "rice")), false)
        val kg = itemEdit(name = "Rice", unit = QuantityUnit.Kilograms)
        val alone = service.planEdit(only.batches[0], kg, only)
        assertEquals(QuantityUnit.Kilograms, alone.filterIsInstance<PantryChange.UpdateProduct>().single().product.unit)
        val shared = service.planEdit(two.batches[0], kg, two)
        assertTrue(
            shared.filterIsInstance<PantryChange.UpdateProduct>().none {
                it.product.unit != QuantityUnit.Pieces
            },
        )
    }

    @Test
    fun `a rename onto another product's name is refused`() {
        val data = PantryData(listOf(product("milk"), product("eggs")), listOf(batch("b1", "milk")), false)
        assertThrows(AppError.NameTaken::class.java) {
            service.planEdit(data.batches[0], itemEdit(name = "eggs"), data)
        }
    }

    private class RecordingWriter : HouseholdWriter {
        var last: List<HouseholdChange> = emptyList()
        private var next = 0

        override fun newId() = "id${next++}"

        override suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>) {
            last = changes
        }

        override val errors: Flow<AppError> = emptyFlow()
    }
}
