package com.parcha.spoilnot.feature.grocery

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryLayout
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.grocery.domain.QuickAddResult
import com.parcha.spoilnot.feature.grocery.domain.ShoppingTrip
import com.parcha.spoilnot.feature.grocery.domain.StoreOrder
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Groceries end to end on two phones (QA flows 2.4 and 2.16; after v1, review CODE-08): what one phone adds, checks
 * off, deletes, clears or adds to the pantry reaches the other, every Undo too, and the store order one phone learns
 * sorts the other's aisles. Through the services the screen calls, against the emulators (DESIGN 13.1).
 */
@RunWith(AndroidJUnit4::class)
class GroceryFlowTest {
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
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Grocery test", code)
        alice.awaitSession { it is Session.InHousehold }
        bob.households.requestToJoin(bob.uid, "Bob", checkNotNull(bob.households.findInviteCode(code)))
        val request = withTimeout(10_000) {
            alice.households.observeJoinRequests(householdId).first { r -> r.any { it.uid == bob.uid } }
        }.single { it.uid == bob.uid }
        alice.households.approve(householdId, request)
        bob.awaitSession { it is Session.InHousehold }
        listId = defaultList(alice).id
        Unit
    }

    @After
    fun tearDown() {
        if (::alice.isInitialized) alice.close()
        if (::bob.isInitialized) bob.close()
    }

    private suspend fun defaultList(phone: TestPhone, predicate: (GroceryList) -> Boolean = { true }): GroceryList =
        withTimeout(20_000) {
            phone.groceryRepository.observeLists(householdId).first { lists ->
                lists.any { it.isDefault && predicate(it) }
            }
        }.single { it.isDefault }

    private suspend fun TestPhone.items(predicate: (List<GroceryItem>) -> Boolean): List<GroceryItem> =
        withTimeout(20_000) { groceryRepository.observeItems(householdId, listId).first(predicate) }

    private suspend fun TestPhone.pantryData(predicate: (PantryData) -> Boolean = { true }): PantryData =
        withTimeout(20_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    private suspend fun TestPhone.quickAdd(text: String, items: List<GroceryItem>) =
        grocery.quickAdd(householdId, actor, listId, text, items, pantryData())

    private fun List<GroceryItem>.named(name: String) = single { it.name == name }

    @Test
    fun quickAddCheckOffDeleteAndClearCheckedReachTheOtherPhoneWithUndo() = runBlocking {
        // Quick add: an amount and unit, and the aisle guessed from the name (DESIGN 5.3).
        assertEquals(QuickAddResult.Added, alice.quickAdd("2 l milk", emptyList()))
        assertEquals(QuickAddResult.Added, alice.quickAdd("bread", alice.items { it.size == 1 }))
        val onBob = bob.items { it.size == 2 }
        val milk = onBob.named("Milk")
        assertEquals(2.0, milk.quantity!!, 0.0)
        assertEquals(QuantityUnit.Liters, milk.unit)
        assertEquals(Category.Dairy, milk.category)
        assertEquals(Category.Bakery, onBob.named("Bread").category)

        // The same name again asks first (Already on the list); Increase adds to it, and the other phone sees it.
        val again = bob.quickAdd("1 l milk", onBob)
        assertTrue(again is QuickAddResult.AlreadyOnList)
        again as QuickAddResult.AlreadyOnList
        bob.grocery.increase(householdId, bob.actor, listId, again.existing, again.request)
        alice.items { items -> items.singleOrNull { it.name == "Milk" }?.quantity == 3.0 }

        // Checked off on Bob's phone: in Alice's cart, with who checked it.
        bob.grocery.setChecked(householdId, bob.actor, listId, milk, checked = true)
        val checked = alice.items { items -> items.any { it.name == "Milk" && it.checked } }.named("Milk")
        assertEquals(bob.uid, checked.checkedBy)

        // Delete with Undo: gone on Bob's phone, then back.
        val bread = alice.items { it.size == 2 }.named("Bread")
        val deleteUndo = alice.grocery.delete(householdId, alice.actor, listId, bread)
        bob.items { items -> items.none { it.name == "Bread" } }
        alice.grocery.undo(householdId, alice.actor, deleteUndo)
        bob.items { items -> items.any { it.name == "Bread" && !it.checked } }

        // Clear checked takes only the cart, and its Undo puts it back, still checked.
        val clearUndo =
            checkNotNull(
                alice.grocery.clearChecked(
                    householdId,
                    alice.actor,
                    listId,
                    alice.items {
                        it.size ==
                            2
                    },
                ),
            )
        assertEquals(listOf("Bread"), bob.items { it.size == 1 }.map { it.name })
        alice.grocery.undo(householdId, alice.actor, clearUndo)
        val back = bob.items { it.size == 2 }
        assertTrue(back.named("Milk").checked)
        assertTrue(!back.named("Bread").checked)
    }

    @Test
    fun theStoreOrderOnePhoneLearnsSortsTheOtherPhonesAisles() = runBlocking {
        alice.quickAdd("milk", emptyList())
        alice.quickAdd("bread", alice.items { it.size == 1 })
        alice.quickAdd("apples", alice.items { it.size == 2 })
        val items = bob.items { it.size == 3 }
        // The default order puts Produce, then Dairy, then Bakery.
        val before = StoreOrder.sort(GroceryLayout.of(items), defaultList(bob).aisleOrder)
        assertEquals(listOf(Category.Produce, Category.Dairy, Category.Bakery), before.aisles.map { it.first })

        // Bob's trip, as the Groceries screen runs it: Bread first, then Milk. Dairy moves to just after Bakery.
        val trip = ShoppingTrip(Clock.systemUTC())
        for (name in listOf("Bread", "Milk")) {
            val item = items.named(name)
            val list = defaultList(bob)
            bob.grocery.setChecked(householdId, bob.actor, listId, item, checked = true)
            val aisle = checkNotNull(item.category)
            StoreOrder.learn(list.aisleOrder, trip.checked(list.id, aisle), aisle)?.let {
                bob.grocery.setAisleOrder(householdId, bob.actor, list, it)
            }
            defaultList(bob) { name == "Bread" || it.aisleOrder.isNotEmpty() }
        }

        // Alice's next trip: her list puts Bakery before Dairy.
        val learned = defaultList(alice) { it.aisleOrder.isNotEmpty() }.aisleOrder
        val unchecked = alice.items { it.count { i -> i.checked } == 2 }.map { it.copy(checked = false) }
        val after = StoreOrder.sort(GroceryLayout.of(unchecked), learned)
        assertEquals(listOf(Category.Produce, Category.Bakery, Category.Dairy), after.aisles.map { it.first })
    }

    @Test
    fun addToPantryMovesTheCartIntoBothPantriesAndUndoPutsItBack() = runBlocking {
        alice.quickAdd("eggs", emptyList())
        alice.quickAdd("butter", alice.items { it.size == 1 })
        val items = bob.items { it.size == 2 }
        items.forEach { bob.grocery.setChecked(householdId, bob.actor, listId, it, checked = true) }
        val cart = bob.items { list -> list.size == 2 && list.all { it.checked } }

        val undo = checkNotNull(
            bob.grocery.addToPantry(
                householdId,
                bob.actor,
                listId,
                cart.map { PantryEntry(it, 1.0, QuantityUnit.Pieces, today.plusDays(10), Location.Fridge) },
                emptyList(),
                bob.pantryData(),
            ),
        )
        bob.firestore.current.waitForPendingWrites().await()
        val added = alice.pantryData { d -> d.batches.size == 2 }
        assertEquals(listOf("Butter", "Eggs"), added.batches.map { it.productName }.sorted())
        assertTrue(added.batches.all { it.expiryDate == today.plusDays(10) && it.location == Location.Fridge })
        alice.items { it.isEmpty() }

        bob.grocery.undo(householdId, bob.actor, undo)
        val back = alice.items { it.size == 2 }
        assertTrue("back in the cart", back.all { it.checked })
        val after = alice.pantryData { d -> d.batches.isEmpty() }
        assertNull(after.batches.firstOrNull())
    }
}
