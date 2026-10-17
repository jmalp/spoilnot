package com.parcha.spoilnot.feature.pantry

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryEvent
import com.parcha.spoilnot.feature.pantry.domain.PantryList
import com.parcha.spoilnot.feature.pantry.domain.ProductEdit
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import java.time.LocalDate
import kotlin.time.measureTime
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
 * M2 acceptance (DESIGN 14): 100 items added; a second client sees edits in under 2 s; offline edits sync after
 * reconnect. Two simulated phones in one household, against the emulators.
 */
@RunWith(AndroidJUnit4::class)
class PantryFlowTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var householdId: String
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
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Pantry test", code)
        alice.awaitSession { it is Session.InHousehold }
        bob.households.requestToJoin(bob.uid, "Bob", checkNotNull(bob.households.findInviteCode(code)))
        val request = withTimeout(10_000) {
            alice.households.observeJoinRequests(householdId).first { it.isNotEmpty() }
        }.single()
        alice.households.approve(householdId, request)
        bob.awaitSession { it is Session.InHousehold }
        Unit
    }

    @After
    fun tearDown() {
        if (::alice.isInitialized) alice.close()
        if (::bob.isInitialized) bob.close()
    }

    private fun draft(name: String, quantity: Double = 1.0, inDays: Long? = 5, location: Location = Location.Fridge) =
        StockDraft(
            name, quantity, QuantityUnit.Pieces,
            inDays?.let {
                today.plusDays(it)
            },
            DateType.UseBy, location, Category.Other, null, null, false, null,
        )

    private suspend fun TestPhone.data(predicate: (PantryData) -> Boolean): PantryData =
        withTimeout(20_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    @Test
    fun hundredItemsAddedAndVisibleOnBothPhones() = runBlocking {
        val elapsed = measureTime {
            repeat(100) { i ->
                val data = alice.data { true }
                alice.pantry.add(householdId, alice.actor, draft("Item ${i + 1}", inDays = (i % 15).toLong() - 2), data)
            }
        }
        Log.i("PantryFlowTest", "100 adds took $elapsed")
        // Every write confirmed by the server (none left waiting to sync).
        val aliceData = alice.data { d -> d.batches.size == 100 && d.batches.none { it.pendingSync } }
        assertEquals(100, aliceData.products.size)
        val bobData = bob.data { it.batches.size == 100 }
        assertEquals(100, PantryList.rows(bobData.products, bobData.batches, today).size)
    }

    @Test
    fun editsReachTheOtherPhoneInUnder2sMedian() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Milk", quantity = 1.0), alice.data { true })
        val start = bob.data { d -> d.batches.any { it.productName == "Milk" } }
        var quantity = start.batches.single().quantity
        val times = (1..7).map {
            val batch = alice.data { d -> d.batches.single().quantity == quantity }.batches.single()
            quantity += 1
            measureTime {
                alice.pantry.adjust(householdId, alice.actor, batch, 1.0)
                bob.data { d -> d.batches.singleOrNull()?.quantity == quantity }
            }.inWholeMilliseconds
        }.sorted()
        Log.i("PantryFlowTest", "propagation ms: $times")
        assertTrue("median ${times[times.size / 2]} ms of $times", times[times.size / 2] < 2_000)
    }

    @Test
    fun offlineEditsSyncAfterReconnect() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Bread"), alice.data { true })
        val seen = bob.data { d -> d.batches.any { it.productName == "Bread" } }

        val db = bob.firestore.current
        db.disableNetwork().await()
        val offline = withTimeout(10_000) { bob.pantryRepository.observe(householdId).first { it.fromCache } }
        bob.pantry.add(householdId, bob.actor, draft("Eggs", quantity = 12.0), offline)
        bob.pantry.adjust(householdId, bob.actor, seen.batches.single { it.productName == "Bread" }, 1.0)
        val pending = withTimeout(10_000) {
            bob.pantryRepository.observe(householdId).first { d -> d.batches.any { it.productName == "Eggs" } }
        }
        assertTrue("shows waiting to sync", pending.batches.single { it.productName == "Eggs" }.pendingSync)

        db.enableNetwork().await()
        val synced = alice.data { d ->
            d.batches.any { it.productName == "Eggs" && it.quantity == 12.0 } &&
                d.batches.any { it.productName == "Bread" && it.quantity == 2.0 }
        }
        assertEquals(2, synced.products.size)
        bob.data { d -> d.batches.none { it.pendingSync } }
        Unit
    }

    @Test
    fun throwAwayLogsTheReasonAndUndoRestores() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Yogurt", quantity = 2.0), alice.data { true })
        val batch = alice.data { it.batches.isNotEmpty() }.batches.single()

        val undo = alice.pantry.throwAway(householdId, alice.actor, batch, DiscardReason.Spoiled)
        bob.data { it.batches.isEmpty() }
        val events = withTimeout(10_000) {
            bob.pantryRepository.observeEvents(householdId, batch.productId, 20).first { list ->
                list.any {
                    it.type ==
                        PantryEvent.Type.Discarded
                }
            }
        }
        assertEquals(DiscardReason.Spoiled, events.first { it.type == PantryEvent.Type.Discarded }.reason)

        alice.pantry.undo(householdId, alice.actor, undo)
        val restored = bob.data { it.batches.size == 1 }.batches.single()
        assertEquals(batch.id, restored.id)
        assertEquals(2.0, restored.quantity, 0.0)
        withTimeout(10_000) {
            bob.pantryRepository.observeEvents(householdId, batch.productId, 20).first { list ->
                list.none {
                    it.type ==
                        PantryEvent.Type.Discarded
                }
            }
        }
        Unit
    }

    @Test
    fun renamingAProductUpdatesItsBatchesEverywhere() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Chese"), alice.data { true })
        alice.pantry.add(householdId, alice.actor, draft("chese", inDays = 9), alice.data { it.batches.size == 1 })
        val data = alice.data { it.batches.size == 2 }
        assertEquals("same name in another case reuses the product", 1, data.products.size)
        val product = data.products.single()
        val edit =
            ProductEdit(
                "Cheese",
                product.category,
                product.defaultLocation,
                product.unit,
                null,
                false,
                null,
                product.typicalShelfLifeDays,
            )
        alice.pantry.updateProduct(householdId, alice.actor, product, edit, data)

        val renamed = bob.data { d ->
            d.products.single().name == "Cheese" &&
                d.batches.all { it.productName == "Cheese" }
        }
        assertEquals(2, renamed.batches.size)
    }
}
