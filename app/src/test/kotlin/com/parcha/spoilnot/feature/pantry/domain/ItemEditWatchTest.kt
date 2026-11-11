package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Edit window while someone else changes or removes its item (after v1, DESIGN 9.2 and 9.4). */
class ItemEditWatchTest {
    private val milk = product("milk", min = 2.0)
    private val opened = batch("m1", "milk", quantity = 3.0).copy(updatedBy = "alice")
    private val base = ItemEditBase(opened, milk)

    private fun data(vararg batches: StockBatch, products: List<Product> = listOf(milk)) =
        PantryData(products, batches.toList(), fromCache = false)

    @Test
    fun `nothing the window shows changed`() {
        assertEquals(EditWatch.Unchanged, ItemEditWatch.check(base, data(opened), "alice"))
        // A note or the pending-sync flag isn't in the window.
        val noted = opened.copy(note = "top shelf", pendingSync = true, updatedBy = "bob")
        assertEquals(EditWatch.Unchanged, ItemEditWatch.check(base, data(noted), "alice"))
    }

    @Test
    fun `someone else's use or product change is reported with who did it`() {
        val used = opened.copy(quantity = 1.0, updatedBy = "bob")
        assertEquals(
            EditWatch.Changed("bob", ItemEditBase(used, milk)),
            ItemEditWatch.check(base, data(used), "alice"),
        )
        val renamed = milk.copy(name = "Oat milk", updatedBy = "sam")
        assertEquals(
            EditWatch.Changed("sam", ItemEditBase(opened, renamed)),
            ItemEditWatch.check(base, data(opened, products = listOf(renamed)), "alice"),
        )
    }

    @Test
    fun `this person's own change from another device is not someone else's`() {
        val used = opened.copy(quantity = 2.0, updatedBy = "alice")
        assertEquals(EditWatch.Unchanged, ItemEditWatch.check(base, data(used), "alice"))
    }

    @Test
    fun `a deleted purchase or one used up to 0 is removed`() {
        assertEquals(EditWatch.Removed(null), ItemEditWatch.check(base, data(), "alice"))
        val usedUp = opened.copy(quantity = 0.0, updatedBy = "bob")
        assertEquals(EditWatch.Removed("bob"), ItemEditWatch.check(base, data(usedUp), "alice"))
    }

    @Test
    fun `review keeps the person's changes and takes the rest from the item as it is now`() {
        val then = base.edit
        val now = then.copy(quantity = 1.0, location = Location.Freezer, minQuantity = 4.0, name = "Oat milk")
        // Alice changed the date and took 1 off (3 to 2); Bob used 2 and moved it, and renamed the product.
        val draft = then.copy(expiryDate = TODAY.plusDays(1), quantity = 2.0)
        val rebased = ItemEditWatch.rebase(draft, then, now)
        assertEquals(
            now.copy(expiryDate = TODAY.plusDays(1), quantity = 0.0),
            rebased,
        )
        // Her own Keep at least stays as she set it, the switch and Buy quantity with it.
        val keep = then.copy(minQuantity = 6.0)
        assertEquals(6.0, ItemEditWatch.rebase(keep, then, now).minQuantity!!, 0.0)
        // A name she typed stays.
        assertEquals("Soy milk", ItemEditWatch.rebase(then.copy(name = "Soy milk"), then, now).name)
    }
}
