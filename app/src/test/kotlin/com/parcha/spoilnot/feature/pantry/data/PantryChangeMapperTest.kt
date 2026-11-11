package com.parcha.spoilnot.feature.pantry.data

import com.google.firebase.firestore.FieldValue
import com.parcha.spoilnot.core.firebase.FakeServer
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.toBatch
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.toProduct
import com.parcha.spoilnot.feature.pantry.domain.BatchEdit
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pantry writes as the server would store them (after v1, DESIGN 8.6 and 9.4): an edit writes only what changed, and
 * Undo of a deletion removes the tombstone and nothing else, so another member's change, fields a newer app added and
 * the creator stay.
 */
class PantryChangeMapperTest {
    private val server = FakeServer()
    private val alice = Actor("alice", "Alice")
    private val bob = Actor("bob", "Bob")
    private val path = "households/h1/stock/m1"
    private val productPath = "households/h1/products/milk"

    private fun write(actor: Actor, vararg changes: HouseholdChange) = changes.forEach {
        assertTrue(PantryChangeMapper().write(server.batch, server.db, "h1", actor, it))
    }

    @Test
    fun `a batch comes back from Undo as the server had it, unknown fields and creator included`() {
        val milk = batch("m1", "milk", quantity = 3.0)
        write(bob, PantryChange.CreateBatch(milk))
        server.docs.getValue(path)["futureField"] = "from a newer app"
        val before = server.docs.getValue(path).toMap()

        write(alice, PantryChange.DeleteBatch("m1"))
        assertTrue("deletedAt" in server.docs.getValue(path))
        // Bob's phone hadn't seen the deletion yet and used one.
        server.docs.getValue(path)["quantity"] = 2.0
        write(alice, PantryChange.RestoreBatch(milk))

        val (_, kind, fields) = server.writes.last()
        assertEquals("merge", kind)
        assertEquals(setOf("deletedAt", "updatedAt", "updatedBy"), fields.keys)
        val after = server.docs.getValue(path)
        assertFalse("deletedAt" in after)
        assertEquals("from a newer app", after["futureField"])
        assertEquals("bob", after["createdBy"])
        assertEquals(before - "updatedBy" - "quantity", after - "updatedBy" - "quantity")
        assertEquals(milk.copy(quantity = 2.0, createdAt = null, updatedBy = "alice"), server.snapshot(path).toBatch())
    }

    @Test
    fun `an edit writes the changed fields only, over another member's change`() {
        val milk = batch("m1", "milk", quantity = 3.0, inDays = 5)
        write(bob, PantryChange.CreateBatch(milk))
        server.docs.getValue(path)["quantity"] = 1.0
        server.docs.getValue(path)["location"] = Location.Freezer.wire

        val opened = BatchEdit.of(milk)
        write(alice, PantryChange.UpdateBatch("m1", opened, opened.copy(expiryDate = TODAY.plusDays(9))))

        val (_, kind, fields) = server.writes.last()
        assertEquals("update", kind)
        assertEquals(setOf("expiryDate", "updatedAt", "updatedBy"), fields.keys)
        assertEquals(
            milk.copy(quantity = 1.0, location = Location.Freezer, expiryDate = TODAY.plusDays(9), createdAt = null)
                .copy(updatedBy = "alice"),
            server.snapshot(path).toBatch(),
        )
    }

    @Test
    fun `a product edit writes the changed fields only`() {
        val milk = product("milk", min = 2.0)
        write(bob, PantryChange.CreateProduct(milk))
        val renamed = milk.copy(name = "Whole milk", nameKey = "whole milk")
        write(alice, PantryChange.UpdateProduct(renamed, milk))
        assertEquals(setOf("name", "nameKey", "updatedAt", "updatedBy"), server.writes.last().third.keys)
        assertEquals(renamed.copy(updatedBy = "alice"), server.snapshot(productPath).toProduct())

        // Nothing changed: nothing is written.
        val count = server.writes.size
        write(alice, PantryChange.UpdateProduct(renamed, renamed))
        assertEquals(count, server.writes.size)
    }

    @Test
    fun `undo of a minus one that used the batch up adds it back and removes a tombstone`() {
        write(bob, PantryChange.CreateBatch(batch("m1", "milk")))
        write(alice, PantryChange.AddToBatch("m1", 1.0, revive = true))
        val fields = server.writes.last().third
        assertEquals(setOf("quantity", "deletedAt", "updatedAt", "updatedBy"), fields.keys)
        assertEquals(FieldValue.delete(), fields["deletedAt"])
        write(alice, PantryChange.AddToBatch("m1", -1.0))
        assertFalse("deletedAt" in server.writes.last().third)
    }
}
