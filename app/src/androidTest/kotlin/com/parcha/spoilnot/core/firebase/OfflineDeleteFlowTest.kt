package com.parcha.spoilnot.core.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With the network off, what is deleted leaves the screen at once (DESIGN 9.8): a tombstone whose `deletedAt` is
 * still a pending server timestamp counts as deleted. Before, it read as null until the server confirmed it, so
 * Add to pantry could be pressed twice and added everything again (REL-01).
 */
@RunWith(AndroidJUnit4::class)
class OfflineDeleteFlowTest {
    private lateinit var phone: TestPhone
    private lateinit var householdId: String
    private lateinit var listId: String
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        phone = TestPhone("offline")
        phone.sessionManager.signUp("Olive", uniqueEmail("olive"), PASSWORD)
        phone.awaitSession { it is Session.NoHousehold }
        householdId = phone.households.createHousehold(phone.uid, "Olive", "Offline test", InviteCodes.generate())
        phone.awaitSession { it is Session.InHousehold }
        listId = withTimeout(10_000) {
            phone.groceryRepository.observeLists(householdId).first { it.any { l -> l.isDefault } }
        }.single { it.isDefault }.id
    }

    @After
    fun tearDown() {
        if (::phone.isInitialized) {
            runBlocking { phone.firestore.current.enableNetwork().await() }
            phone.close()
        }
    }

    private fun draft(name: String) = StockDraft(
        name, 1.0, QuantityUnit.Pieces, today.plusDays(4), DateType.UseBy, Location.Fridge, Category.Other,
        null, null, false, null,
    )

    /** What the screen shows: the cache's view while offline, the server's once [confirmed]. */
    private suspend fun pantry(confirmed: Boolean = false, predicate: (PantryData) -> Boolean): PantryData =
        withTimeout(10_000) {
            phone.pantryRepository.observe(householdId).first { (!confirmed || !it.fromCache) && predicate(it) }
        }

    private suspend fun items(predicate: (List<GroceryItem>) -> Boolean): List<GroceryItem> =
        withTimeout(10_000) { phone.groceryRepository.observeItems(householdId, listId).first(predicate) }

    private suspend fun quickAdd(name: String, items: List<GroceryItem>) = phone.grocery.quickAdd(
        householdId,
        phone.actor,
        listId,
        name,
        items,
        pantry { true },
    )

    private fun PantryData.quantityOf(name: String) = batches.filter { it.productName == name }.sumOf { it.quantity }

    @Test
    fun deletionsAndAddToPantryLeaveTheScreenOffline() = runBlocking {
        // Online: one batch in the pantry and three items on the list, all on the server.
        phone.pantry.add(householdId, phone.actor, draft("Cheese"), pantry(confirmed = true) { true })
        pantry(confirmed = true) { it.batches.size == 1 }
        quickAdd("Onions", items { true })
        quickAdd("Bananas", items { it.size == 1 })
        quickAdd("Coffee", items { it.size == 2 })
        items { it.size == 3 }
        phone.firestore.current.waitForPendingWrites().await()

        phone.firestore.current.disableNetwork().await()

        // Used up: the batch leaves the pantry while the delete is still waiting for the server.
        val cheese = pantry { it.batches.size == 1 }.batches.single()
        phone.pantry.usedUp(householdId, phone.actor, cheese)
        pantry { it.batches.none { b -> b.productName == "Cheese" } }

        // Removing an item: gone at once, and Undo brings it back, also offline.
        val onions = items { it.size == 3 }.single { it.name == "Onions" }
        val undo = phone.grocery.delete(householdId, phone.actor, listId, onions)
        items { list -> list.none { it.name == "Onions" } }
        phone.grocery.undo(householdId, phone.actor, undo)
        items { list -> list.any { it.name == "Onions" } }

        // Add to pantry: the cart empties at once, so it cannot be pressed a second time.
        val listed = items { it.size == 3 }
        val bought = listed.filter { it.name == "Bananas" || it.name == "Coffee" }
        bought.forEach { phone.grocery.setChecked(householdId, phone.actor, listId, it, checked = true) }
        val cart = items { list -> list.count { it.checked } == 2 }.filter { it.checked }
        phone.grocery.addToPantry(
            householdId,
            phone.actor,
            listId,
            cart.map { PantryEntry(it, 1.0, QuantityUnit.Pieces, today.plusDays(5), Location.Pantry) },
            emptyList(),
            pantry { true },
        )
        val left = items { list -> list.none { it.checked } }
        assertEquals(listOf("Onions"), left.map { it.name })
        val added = pantry { it.quantityOf("Bananas") == 1.0 && it.quantityOf("Coffee") == 1.0 }
        assertTrue("waiting to sync", added.batches.filter { it.productName == "Bananas" }.all { it.pendingSync })

        // Back online: the server agrees, one banana stayed one.
        phone.firestore.current.enableNetwork().await()
        phone.firestore.current.waitForPendingWrites().await()
        val synced = pantry(confirmed = true) { d -> d.batches.none { it.pendingSync } }
        assertEquals(setOf("Bananas", "Coffee"), synced.batches.map { it.productName }.toSet())
        assertEquals(1.0, synced.quantityOf("Bananas"), 0.0)
        val server = phone.firestore.current.collection("households/$householdId/groceryLists/$listId/items")
            .get(Source.SERVER).await().documents
            .filterNot { with(Tombstones) { it.isTombstone() } }
        assertEquals(listOf("Onions"), server.map { it.getString("name") })
    }
}
