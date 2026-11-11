package com.parcha.spoilnot.feature.pantry

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.EditWatch
import com.parcha.spoilnot.feature.pantry.domain.ItemEditBase
import com.parcha.spoilnot.feature.pantry.domain.ItemEditWatch
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
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
 * Edits don't undo someone else's change (after v1, DESIGN 9.2 and 9.4), on two phones against the emulators and the
 * rules: the Edit window open while the other phone uses or removes the item (review 04's p05 and p08), -1 on an
 * offline phone while the other adds (p04), and Undo after the other phone's change.
 */
@RunWith(AndroidJUnit4::class)
class EditConflictFlowTest {
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
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Edit conflicts", code)
        alice.awaitSession { it is Session.InHousehold }
        bob.households.requestToJoin(bob.uid, "Bob", checkNotNull(bob.households.findInviteCode(code)))
        val request = withTimeout(10_000) {
            alice.households.observeJoinRequests(householdId).first { r -> r.any { it.uid == bob.uid } }
        }.single { it.uid == bob.uid }
        alice.households.approve(householdId, request)
        bob.awaitSession { it is Session.InHousehold }
    }

    @After
    fun tearDown() {
        for (phone in listOf(if (::alice.isInitialized) alice else null, if (::bob.isInitialized) bob else null)) {
            phone ?: continue
            runBlocking { runCatching { phone.firestore.current.enableNetwork().await() } }
            phone.close()
        }
    }

    private suspend fun TestPhone.flush() = withTimeout(30_000) { firestore.current.waitForPendingWrites().await() }

    /** This phone's pantry once its listeners have heard from the server and [predicate] holds. */
    private suspend fun TestPhone.pantryData(predicate: (PantryData) -> Boolean = { true }): PantryData =
        withTimeout(20_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    /** The batch on the server, tombstone or not, read past this phone's cache. */
    private suspend fun TestPhone.serverBatch(id: String) =
        firestore.current.document("households/$householdId/stock/$id").get(Source.SERVER).await()

    private suspend fun addMilk(quantity: Double): StockBatch {
        val result = alice.pantry.add(householdId, alice.actor, milk(quantity), alice.pantryData())
        alice.flush()
        return bob.pantryData { d -> d.batches.any { it.id == result.batchId } }.batches.single {
            it.id == result.batchId
        }
    }

    @Test
    fun theEditWindowKeepsAnotherMembersUseAndSaysSo() = runBlocking {
        // p05: Alice opens Milk x3; Bob uses 2; Alice changes only the date and saves.
        val milk = addMilk(3.0)
        val opened = alice.pantryData { d -> d.batches.any { it.id == milk.id } }
        val base = ItemEditBase(opened.batches.single { it.id == milk.id }, opened.products.single())

        bob.pantry.adjust(householdId, bob.actor, milk, -2.0)
        bob.flush()
        val now = alice.pantryData { d -> d.batches.single { it.id == milk.id }.quantity == 1.0 }
        val watch = ItemEditWatch.check(base, now, alice.uid)
        assertTrue("$watch", watch is EditWatch.Changed && watch.byUid == bob.uid)

        val dateOnly = base.edit.copy(expiryDate = today.plusDays(20))
        assertNull(alice.pantry.editItem(householdId, alice.actor, base, dateOnly, now))
        alice.flush()
        val server = alice.serverBatch(milk.id)
        assertEquals(1.0, server.getDouble("quantity")!!, 0.0)
        assertEquals(today.plusDays(20).toString(), server.getString("expiryDate"))
    }

    @Test
    fun theEditWindowLearnsWhoRemovedItsItem() = runBlocking {
        // p08: Bob throws the milk away while Alice has it open.
        val milk = addMilk(2.0)
        val opened = alice.pantryData { d -> d.batches.any { it.id == milk.id } }
        val base = ItemEditBase(opened.batches.single { it.id == milk.id }, opened.products.single())
        bob.pantry.throwAway(householdId, bob.actor, milk, DiscardReason.Spoiled)
        bob.flush()
        val now = alice.pantryData { d -> d.batches.none { it.id == milk.id } }
        assertEquals(EditWatch.Removed(null), ItemEditWatch.check(base, now, alice.uid))
        assertEquals(bob.uid, alice.pantryRepository.removedBy(householdId, milk.id))
    }

    @Test
    fun minusOneOnAnOfflinePhoneKeepsWhatAnotherMemberAdded() = runBlocking {
        // p04: Yogurt x1 on both phones; Alice goes offline; Bob adds 2; Alice, still seeing 1, presses -1.
        val milk = addMilk(1.0)
        alice.pantryData { d -> d.batches.any { it.id == milk.id } }
        alice.firestore.current.disableNetwork().await()
        bob.pantry.adjust(householdId, bob.actor, milk, 2.0)
        bob.flush()
        assertTrue(alice.pantry.adjust(householdId, alice.actor, milk, -1.0) != null)
        alice.firestore.current.enableNetwork().await()
        alice.flush()
        val server = bob.serverBatch(milk.id)
        assertEquals(2.0, server.getDouble("quantity")!!, 0.0)
        assertNull("not removed", server.get("deletedAt"))
        assertEquals(
            2.0,
            bob.pantryData { d -> d.batches.any { it.id == milk.id && it.quantity == 2.0 } }
                .batches.single { it.id == milk.id }.quantity,
            0.0,
        )
    }

    @Test
    fun undoAfterAnotherMembersChangeKeepsItAndTheCreator() = runBlocking {
        // Alice created the milk. Bob throws it away; Alice's offline phone, which hadn't seen that, uses 1; Bob
        // presses Undo after Alice's change has reached the server.
        val milk = addMilk(3.0)
        alice.pantryData { d -> d.batches.any { it.id == milk.id } }
        alice.firestore.current.disableNetwork().await()
        val undo = bob.pantry.throwAway(householdId, bob.actor, milk, DiscardReason.Expired)
        bob.flush()
        alice.pantry.adjust(householdId, alice.actor, milk, -1.0)
        alice.firestore.current.enableNetwork().await()
        alice.flush()
        assertTrue("still deleted", bob.serverBatch(milk.id).get("deletedAt") != null)

        bob.pantry.undo(householdId, bob.actor, undo)
        bob.flush()
        val server = bob.serverBatch(milk.id)
        assertNull(server.get("deletedAt"))
        assertEquals("Alice's -1 stays", 2.0, server.getDouble("quantity")!!, 0.0)
        assertEquals("the creator stays", alice.uid, server.getString("createdBy"))
        assertEquals(bob.uid, server.getString("updatedBy"))
    }

    @Test
    fun undoOfALastMinusOneBringsBackABatchAnotherPhoneRemoved() = runBlocking {
        val milk = addMilk(1.0)
        val undo = checkNotNull(alice.pantry.adjust(householdId, alice.actor, milk, -1.0))
        alice.flush()
        // Bob's Pantry notices the empty batch and removes it (8.5 invariant 1).
        val empty = bob.pantryData { d -> d.batches.any { it.id == milk.id && it.quantity <= 0 && !it.pendingSync } }
        bob.pantry.deleteEmptyBatches(householdId, bob.actor, empty)
        bob.flush()
        assertTrue(alice.serverBatch(milk.id).get("deletedAt") != null)

        alice.pantry.undo(householdId, alice.actor, undo)
        alice.flush()
        val back = bob.pantryData { d -> d.batches.any { it.id == milk.id && it.quantity == 1.0 } }
        assertEquals(1.0, back.batches.single { it.id == milk.id }.quantity, 0.0)
    }

    private fun milk(quantity: Double) = StockDraft(
        "Milk", quantity, QuantityUnit.Pieces, today.plusDays(7), DateType.UseBy, Location.Fridge, Category.Dairy,
        note = null, minQuantity = null, autoAddToGrocery = false, buyQuantity = null,
    )
}
