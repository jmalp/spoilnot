package com.parcha.spoilnot.core.firebase

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
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Changes-only listeners (DESIGN 9.8) between two phones: deletions arrive as tombstones and disappear, and a phone
 * that comes back after a full read catches up with only the changes, including deletions made while it was away.
 */
@RunWith(AndroidJUnit4::class)
class DeltaSyncFlowTest {
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
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Delta test", code)
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

    private fun draft(name: String) = StockDraft(
        name, 1.0, QuantityUnit.Pieces, today.plusDays(4), DateType.UseBy, Location.Fridge, Category.Other,
        null, null, false, null,
    )

    private suspend fun TestPhone.data(predicate: (PantryData) -> Boolean): PantryData =
        withTimeout(20_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    private fun PantryData.names() = batches.map { it.productName }.toSet()

    @Test
    fun deletionsAndChangesReachTheOtherPhoneLiveAndAfterReopening() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Milk"), alice.data { true })
        alice.pantry.add(householdId, alice.actor, draft("Bread"), alice.data { it.batches.size == 1 })
        alice.pantry.add(householdId, alice.actor, draft("Eggs"), alice.data { it.batches.size == 2 })

        // Bob's first read is a full one and is recorded.
        val first = bob.data { it.names() == setOf("Milk", "Bread", "Eggs") }
        val stockPath = "households/$householdId/stock"
        assertNotNull("full read recorded", bob.syncLog.lastFullSync(stockPath))

        // Alice throws away Milk while Bob listens: it disappears for Bob (a tombstone, not a delete).
        alice.pantry.throwAway(
            householdId,
            alice.actor,
            first.batches.single {
                it.productName == "Milk"
            },
            DiscardReason.Spoiled,
        )
        bob.data { it.names() == setOf("Bread", "Eggs") }
        val milk = alice.firestore.current.document(
            "$stockPath/${first.batches.single {
                it.productName == "Milk"
            }.id}",
        )
            .get().await()
        assertTrue("kept as a tombstone", milk.exists() && milk.get(Tombstones.FIELD) != null)

        // Bob "closes the app" (no listener). Alice uses up Bread and adds Rice meanwhile.
        alice.pantry.usedUp(householdId, alice.actor, first.batches.single { it.productName == "Bread" })
        alice.pantry.add(householdId, alice.actor, draft("Rice"), alice.data { it.names() == setOf("Eggs") })

        // Reopening reads only the changes (a recent full read is on record) and still ends up right.
        val before = bob.syncLog.lastFullSync(stockPath)
        val reopened = bob.data { it.names() == setOf("Eggs", "Rice") }
        assertEquals("no new full read", before, bob.syncLog.lastFullSync(stockPath))
        assertEquals(2, reopened.batches.size)
    }
}
