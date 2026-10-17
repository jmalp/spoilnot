package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `minus one at one uses the batch up and can be undone`() = runTest {
        val b = batch("m1", "milk", quantity = 1.0)
        val undo = service.adjust("h", actor, b, -1.0)
        assertEquals(listOf(PantryChange.DeleteBatch("m1")), repo.last)
        service.undo("h", actor, undo!!)
        assertEquals(listOf(PantryChange.RestoreBatch(b)), repo.last)
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
        assertEquals(listOf(PantryChange.RestoreBatch(b), PantryChange.DeleteEvent(event.eventId)), undo.changes)
    }

    @Test
    fun `editing to zero uses the batch up, moving keeps everything else`() = runTest {
        val b = batch("m1", "milk", quantity = 2.0)
        val undo = service.edit("h", actor, b, BatchEdit(0.0, null, DateType.UseBy, Location.Fridge, null))
        assertEquals(listOf(PantryChange.DeleteBatch("m1")), repo.last)
        assertEquals(listOf(PantryChange.RestoreBatch(b)), undo!!.changes)
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
