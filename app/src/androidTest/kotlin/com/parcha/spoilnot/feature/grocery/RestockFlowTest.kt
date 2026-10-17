package com.parcha.spoilnot.feature.grocery

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M3 acceptance (DESIGN 14): dropping stock below the minimum on phone A creates exactly one item visible on
 * phone B; deleting it does not recreate it; recovering and dropping again does. Both phones run the restock
 * engine, as real phones do, so this also covers two concurrent evaluations.
 */
@RunWith(AndroidJUnit4::class)
class RestockFlowTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var householdId: String
    private lateinit var listId: String
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        alice = TestPhone("alice")
        bob = TestPhone("bob")
        alice.sessionManager.signUp("Alice", uniqueEmail("alice"), PASSWORD)
        bob.sessionManager.signUp("Bob", uniqueEmail("bob"), PASSWORD)
        alice.awaitSession { it is Session.NoHousehold }
        bob.awaitSession { it is Session.NoHousehold }
        val code = InviteCodes.generate()
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Restock test", code)
        alice.awaitSession { it is Session.InHousehold }
        bob.households.requestToJoin(bob.uid, "Bob", checkNotNull(bob.households.findInviteCode(code)))
        val request = withTimeout(10_000) {
            alice.households.observeJoinRequests(householdId).first { it.isNotEmpty() }
        }.single()
        alice.households.approve(householdId, request)
        bob.awaitSession { it is Session.InHousehold }
        listId =
            withTimeout(10_000) {
                bob.groceryRepository.observeLists(householdId).first { it.any { l -> l.isDefault } }
            }
                .single { it.isDefault }.id
        alice.startRestock()
        bob.startRestock()
    }

    @After
    fun tearDown() {
        if (::alice.isInitialized) alice.close()
        if (::bob.isInitialized) bob.close()
    }

    private suspend fun TestPhone.pantryData(predicate: (PantryData) -> Boolean = { true }): PantryData =
        withTimeout(15_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    private suspend fun TestPhone.items(predicate: (List<GroceryItem>) -> Boolean): List<GroceryItem> =
        withTimeout(15_000) { groceryRepository.observeItems(householdId, listId).first(predicate) }

    /**
     * The list once it has settled: a new listener's first snapshot can come from a stale local cache (a phone
     * that was not listening to the list), so watch for a while and keep the last value.
     */
    private suspend fun TestPhone.settledItems(windowMs: Long = 2_000): List<GroceryItem> {
        var last: List<GroceryItem> = emptyList()
        withTimeoutOrNull(windowMs) { groceryRepository.observeItems(householdId, listId).collect { last = it } }
        return last
    }

    private fun draft(name: String, quantity: Double, min: Double? = null) = StockDraft(
        name, quantity, QuantityUnit.Pieces, today.plusDays(5), DateType.UseBy, Location.Fridge, Category.Dairy,
        note = null, minQuantity = min, autoAddToGrocery = min != null, buyQuantity = null,
    )

    /** Uses up [amount] from Milk's only batch on [phone]. */
    private suspend fun drop(phone: TestPhone, amount: Double) {
        val batch = phone.pantryData { it.batches.isNotEmpty() }.batches.single()
        phone.pantry.adjust(householdId, phone.actor, batch, -amount)
    }

    @Test
    fun dropAddsExactlyOneItem_deleteSticks_recoverAndDropAddsAgain() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Milk", 3.0, min = 2.0), alice.pantryData())
        assertTrue("nothing while stock is at or above the minimum", bob.settledItems().isEmpty())

        // 1. Phone A drops below the minimum: exactly one Low stock item appears on phone B.
        drop(alice, 2.0)
        val added = bob.items { it.isNotEmpty() }
        assertEquals(1, added.size)
        val item = added.single()
        assertEquals(ItemSource.LowStock, item.source)
        assertEquals("Milk", item.name)
        assertEquals(1.0, item.quantity!!, 0.0)
        // Both engines have seen the change by now.
        assertEquals("still exactly one, even with two engines", 1, bob.settledItems(3_000).size)
        assertTrue(!alice.pantryData().products.single().restockArmed)

        // 2. Phone B deletes it: neither engine adds it back while stock stays low.
        bob.grocery.delete(householdId, bob.actor, listId, item)
        bob.items { it.isEmpty() }
        assertTrue("not recreated after deletion", alice.settledItems(3_000).isEmpty())

        // 3. Stock recovers (re-arms), then drops again: the item comes back.
        alice.pantry.add(householdId, alice.actor, draft("Milk", 2.0), alice.pantryData())
        alice.pantryData { d -> d.products.single().restockArmed }
        assertTrue("no item while recovered", bob.settledItems().isEmpty())
        drop(alice, 2.0)
        val again = bob.items { it.isNotEmpty() }
        assertEquals(1, again.size)
        assertEquals(ItemSource.LowStock, again.single().source)
    }

    @Test
    fun aCheckedLowStockItemStaysCheckedAndAddToPantryRestocks() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Butter", 1.0, min = 2.0), alice.pantryData())
        val low = bob.items { it.isNotEmpty() }.single()

        bob.grocery.setChecked(householdId, bob.actor, listId, low, checked = true)
        val checked = alice.items { list -> list.singleOrNull()?.checked == true }.single()

        // Done shopping on phone B: one write adds the stock and removes the item.
        val data = bob.pantryData()
        bob.grocery.addToPantry(
            householdId,
            bob.actor,
            listId,
            listOf(PantryEntry(checked, 2.0, QuantityUnit.Pieces, today.plusDays(10), Location.Fridge)),
            emptyList(),
            data,
        )
        alice.items { it.isEmpty() }
        val after = alice.pantryData { d -> d.batches.sumOf { it.quantity } == 3.0 }
        assertEquals("the same product, not a duplicate", 1, after.products.size)
        alice.pantryData { d -> d.products.single().restockArmed }
        assertTrue("restocked: nothing re-added", alice.settledItems(3_000).isEmpty())
    }
}
