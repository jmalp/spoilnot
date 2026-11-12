package com.parcha.spoilnot.feature.grocery

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.ProductEdit
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import java.time.LocalDate
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Add to pantry and its Undo against the rules (after v1, LOGIC-10, LOGIC-03): Bob adds a renamed staple and a new
 * product, then undoes it; the server takes every write, Alice's phone sees the items back in the cart, the new
 * product gone and the staple's stock as it was, and the items can be added again.
 */
@RunWith(AndroidJUnit4::class)
class AddToPantryUndoFlowTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var householdId: String
    private lateinit var listId: String
    private val today: LocalDate = LocalDate.now()
    private val refusals = Collections.synchronizedList(mutableListOf<AppError>())
    private val collecting = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Undo shopping", code)
        alice.awaitSession { it is Session.InHousehold }
        bob.households.requestToJoin(bob.uid, "Bob", checkNotNull(bob.households.findInviteCode(code)))
        val request = withTimeout(10_000) {
            alice.households.observeJoinRequests(householdId).first { r -> r.any { it.uid == bob.uid } }
        }.single { it.uid == bob.uid }
        alice.households.approve(householdId, request)
        bob.awaitSession { it is Session.InHousehold }
        listId = withTimeout(10_000) {
            bob.groceryRepository.observeLists(householdId).first { it.any { l -> l.isDefault } }
        }.single { it.isDefault }.id
        collecting.launch(start = CoroutineStart.UNDISPATCHED) { bob.writer.errors.collect { refusals += it } }
        Unit
    }

    @After
    fun tearDown() {
        collecting.cancel()
        if (::alice.isInitialized) alice.close()
        if (::bob.isInitialized) bob.close()
    }

    private suspend fun TestPhone.flush() = withTimeout(30_000) { firestore.current.waitForPendingWrites().await() }

    private suspend fun TestPhone.pantryData(predicate: (PantryData) -> Boolean = { true }): PantryData =
        withTimeout(20_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    private suspend fun TestPhone.items(predicate: (List<GroceryItem>) -> Boolean): List<GroceryItem> =
        withTimeout(20_000) { groceryRepository.observeItems(householdId, listId).first(predicate) }

    private fun checkedItem(id: String, name: String, productId: String? = null) = GroceryItem(
        id = id, name = name, nameKey = NameKey.of(name), quantity = 1.0, unit = QuantityUnit.Pieces, note = null,
        category = Category.Dairy, checked = true, checkedBy = null, source = ItemSource.Manual, productId = productId,
        recipeId = null, createdBy = null, createdAt = null,
    )

    private suspend fun TestPhone.pressAddToPantry(items: List<GroceryItem>) = grocery.addToPantry(
        householdId,
        actor,
        listId,
        items.map { PantryEntry(it, 1.0, QuantityUnit.Pieces, today.plusDays(7), Location.Fridge) },
        emptyList(),
        pantryData(),
    )

    @Test
    fun undoOfAddToPantryPassesTheRulesAndPutsEverythingBack() = runBlocking {
        // Alice has Milk ×1, renamed Whole milk after "Milk" went on the list linked to it.
        val milkId = alice.pantry.add(
            householdId,
            alice.actor,
            StockDraft(
                "Milk", 1.0, QuantityUnit.Pieces, today.plusDays(3), DateType.UseBy, Location.Fridge,
                Category.Dairy, null, null, false, null,
            ),
            alice.pantryData(),
        ).productId
        val milk = alice.pantryData { it.products.any { p -> p.id == milkId } }.products.single { it.id == milkId }
        alice.writer.apply(
            householdId,
            alice.actor,
            listOf(
                GroceryChange.CreateItem(listId, checkedItem(alice.writer.newId(), "Milk", productId = milkId)),
                GroceryChange.CreateItem(listId, checkedItem(alice.writer.newId(), "Bread")),
            ),
        )
        alice.pantry.updateProduct(
            householdId,
            alice.actor,
            milk,
            ProductEdit(
                name = "Whole milk",
                unit = milk.unit,
                category = milk.category,
                defaultLocation = milk.defaultLocation,
                minQuantity = null,
                autoAddToGrocery = false,
                buyQuantity = null,
                typicalShelfLifeDays = milk.typicalShelfLifeDays,
            ),
            alice.pantryData(),
        )
        alice.flush()

        val cart = bob.items { it.size == 2 && it.all { i -> i.checked } }
        bob.pantryData { d -> d.products.any { it.name == "Whole milk" } }
        val undo = checkNotNull(bob.pressAddToPantry(cart))
        bob.flush()
        val added = alice.pantryData { d -> d.batches.sumOf { it.quantity } == 3.0 }
        assertEquals(
            "one milk product, renamed",
            listOf("Bread", "Whole milk"),
            added.products.map {
                it.name
            }.sorted(),
        )
        alice.items { it.isEmpty() }

        bob.grocery.undo(householdId, bob.actor, undo)
        bob.flush()
        val back = alice.items { it.size == 2 }
        assertTrue("back in the cart", back.all { it.checked })
        assertEquals(setOf("Milk", "Bread"), back.map { it.name }.toSet())
        val after = alice.pantryData { d ->
            d.products.none { it.name == "Bread" } &&
                d.batches.sumOf { it.quantity } == 1.0
        }
        assertEquals(listOf("Whole milk"), after.products.map { it.name })
        // The server took every write: none refused, no new product left behind.
        delay(1_000)
        assertEquals(emptyList<AppError>(), refusals.toList())
        val products = alice.firestore.current.collection("households/$householdId/products").get(Source.SERVER).await()
        assertEquals(1, products.documents.count { it.get("deletedAt") == null })

        // And the items can go to the pantry again.
        bob.pressAddToPantry(bob.items { it.size == 2 })
        bob.flush()
        alice.items { it.isEmpty() }
        assertEquals(emptyList<AppError>(), refusals.toList())
    }
}
