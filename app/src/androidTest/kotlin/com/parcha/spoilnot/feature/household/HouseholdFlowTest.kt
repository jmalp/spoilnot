package com.parcha.spoilnot.feature.household

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.feature.home.domain.ActivityType
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.JoinRequestStatus
import com.parcha.spoilnot.feature.household.domain.JoinState
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import java.time.LocalDate
import kotlin.time.measureTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1 acceptance (DESIGN 14): two users on the emulators. A creates a household, B enters the code and waits,
 * A approves, B is in; when A removes B, B is back at the gate within 5 s. Each user is a separate FirebaseApp,
 * so each has its own sign-in and Firestore cache, like two phones.
 */
@RunWith(AndroidJUnit4::class)
class HouseholdFlowTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone

    @Before
    fun setUp() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        alice = TestPhone("alice")
        bob = TestPhone("bob")
        alice.sessionManager.signUp("Alice", uniqueEmail("alice"), PASSWORD)
        bob.sessionManager.signUp("Bob", uniqueEmail("bob"), PASSWORD)
        alice.awaitSession { it is Session.NoHousehold }
        bob.awaitSession { it is Session.NoHousehold }
        Unit
    }

    @After
    fun tearDown() {
        if (::alice.isInitialized) alice.close()
        if (::bob.isInitialized) bob.close()
    }

    @Test
    fun ownerApprovesThenRemovesMember_memberReturnsToGateWithin5s() = runBlocking {
        val (householdId, code) = aliceCreatesHousehold()
        bobRequestsToJoin(code)

        val request = awaitRequestFromBob(householdId)
        assertEquals("Bob", request.displayName)
        alice.households.approve(householdId, request)

        val joined = bob.awaitSession { it is Session.InHousehold } as Session.InHousehold
        assertEquals(householdId, joined.householdId)
        assertEquals(Role.Member, joined.role)
        val names = alice.households.observeMembers(householdId).first { it.size == 2 }.map { it.displayName }
        assertEquals(setOf("Alice", "Bob"), names.toSet())

        val elapsed = measureTime {
            alice.households.removeMember(householdId, bob.uid)
            bob.awaitSession(timeoutMs = 5_000) { it is Session.NoHousehold && it.join == null }
        }
        assertTrue("Back at the gate after $elapsed", elapsed.inWholeMilliseconds < 5_000)
    }

    @Test
    fun ownerDeclines_requesterSeesItAndCanDismiss() = runBlocking {
        val (householdId, code) = aliceCreatesHousehold()
        bobRequestsToJoin(code)
        awaitRequestFromBob(householdId)

        alice.households.decline(householdId, bob.uid)

        val declined = bob.awaitSession { (it as? Session.NoHousehold)?.join is JoinState.Declined }
        bob.households.abandonJoin(bob.uid, (declined as Session.NoHousehold).join!!.pending)
        bob.awaitSession { it is Session.NoHousehold && it.join == null }
        Unit
    }

    @Test
    fun requesterCancels_requestDisappearsForOwner() = runBlocking {
        val (householdId, code) = aliceCreatesHousehold()
        val waiting = bobRequestsToJoin(code)
        awaitRequestFromBob(householdId)

        bob.households.abandonJoin(bob.uid, waiting.pending)

        bob.awaitSession { it is Session.NoHousehold && it.join == null }
        val remaining = withTimeout(10_000) {
            alice.households.observeJoinRequests(householdId).first { list -> list.none { it.uid == bob.uid } }
        }
        assertFalse(remaining.any { it.status == JoinRequestStatus.Pending })
    }

    @Test
    fun pausedCodeIsInactive_rotatedCodeReplacesIt() = runBlocking {
        val (householdId, code) = aliceCreatesHousehold()
        alice.households.setJoiningPaused(code, paused = true)
        assertFalse(checkNotNull(bob.households.findInviteCode(code)).active)

        val household = alice.households.observeHousehold(householdId).first { it?.inviteCode == code }!!
        val newCode = InviteCodes.generate()
        alice.households.regenerateInviteCode(household, alice.uid, newCode, active = true)

        assertNull(bob.households.findInviteCode(code))
        bobRequestsToJoin(newCode)
        Unit
    }

    @Test
    fun ownershipTransfer_thenFormerOwnerLeaves() = runBlocking {
        val householdId = aliceAndBobInOneHousehold()

        alice.households.transferOwnership(householdId, alice.uid, bob.uid)
        bob.awaitSession { it is Session.InHousehold && it.role == Role.Owner }
        alice.awaitSession { it is Session.InHousehold && it.role == Role.Member }

        alice.households.leave(householdId, alice.uid)
        alice.awaitSession { it is Session.NoHousehold }
        val members = bob.households.observeMembers(householdId).first { it.size == 1 }
        assertEquals(listOf("Bob"), members.map { it.displayName })
    }

    @Test
    fun ownerDeletesHousehold_everyoneReturnsToGate() = runBlocking {
        val householdId = aliceAndBobInOneHousehold()
        val household = withTimeout(10_000) { alice.households.observeHousehold(householdId).first { it != null }!! }
        val bobsFirestore = bob.firestore.current

        alice.households.deleteHousehold(household, alice.uid)

        alice.awaitSession { it is Session.NoHousehold }
        bob.awaitSession(timeoutMs = 5_000) { it is Session.NoHousehold }
        // Reading during Bob's own reset can wait forever on the terminated instance.
        bob.awaitLocalDataCleared(bobsFirestore)
        assertNull(withTimeout(10_000) { bob.households.findInviteCode(household.inviteCode) })
    }

    @Test
    fun ownerCannotDeleteAccount_memberCan() = runBlocking {
        val householdId = aliceAndBobInOneHousehold()
        try {
            alice.sessionManager.deleteAccount(PASSWORD)
            fail("The owner must transfer ownership or delete the household first")
        } catch (e: AppError.OwnerMustTransferFirst) {
            // expected
        }

        bob.sessionManager.deleteAccount(PASSWORD)

        bob.awaitSession { it is Session.SignedOut }
        val members = alice.households.observeMembers(householdId).first { it.size == 1 }
        assertEquals(listOf("Alice"), members.map { it.displayName })
    }

    /** Clear household data (owner, after v1, DESIGN 5.1): Bob's phone ends up with a household as new. */
    @Test
    fun ownerClearsHouseholdData_membersStayAndEveryPhoneSeesItEmpty() = runBlocking {
        val householdId = aliceAndBobInOneHousehold()
        val today = LocalDate.now()
        alice.pantry.add(
            householdId,
            alice.actor,
            StockDraft(
                "Milk", 2.0, QuantityUnit.Pieces, today.plusDays(3), DateType.UseBy, Location.Fridge,
                Category.Dairy, null, null, false, null,
            ),
            withTimeout(15_000) { alice.pantryRepository.observe(householdId).first { !it.fromCache } },
        )
        val defaultList = withTimeout(15_000) {
            alice.groceryRepository.observeLists(householdId).first { it.isNotEmpty() }
        }.single { it.isDefault }
        alice.grocery.quickAdd(
            householdId,
            alice.actor,
            defaultList.id,
            "Eggs",
            emptyList(),
            alice.pantryRepository.observe(householdId).first { !it.fromCache },
        )
        alice.grocery.createList(householdId, alice.actor, "Party")
        alice.recipes.save(householdId, alice.actor, RecipeContent(title = "Soup"), emptyList())
        alice.mealPlan.addOther(householdId, alice.actor, today, MealSlot.Dinner, "Tacos", emptyList(), emptyList())
        // Bob has it all on his phone first, so the clear has to reach his cache.
        withTimeout(20_000) {
            bob.pantryRepository.observe(householdId).first { !it.fromCache && it.batches.isNotEmpty() }
            bob.groceryRepository.observeLists(householdId).first { it.size == 2 }
            bob.recipeRepository.observe(householdId).first { it.recipes.isNotEmpty() }
        }

        alice.households.resetData(householdId, alice.actor)

        withTimeout(20_000) {
            bob.pantryRepository.observe(householdId).first { it.batches.isEmpty() && it.products.isEmpty() }
            val lists = bob.groceryRepository.observeLists(householdId).first { it.size == 1 }
            assertEquals(listOf("Groceries"), lists.map { it.name })
            bob.groceryRepository.observeItems(householdId, lists.single().id).first { it.isEmpty() }
            bob.recipeRepository.observe(householdId).first { it.recipes.isEmpty() }
            bob.mealPlanRepository.observe(householdId, today, today).first { it.meals.isEmpty() }
            val recent = bob.activity.observeRecent(householdId, 10)
                .first { it.firstOrNull()?.type == ActivityType.HouseholdReset }
            assertEquals(1, recent.size)
        }
        val members = alice.households.observeMembers(householdId).first { it.isNotEmpty() }
        assertEquals(setOf("Alice", "Bob"), members.map { it.displayName }.toSet())
        assertTrue(bob.sessionManager.session.value is Session.InHousehold)
    }

    private suspend fun aliceCreatesHousehold(): Pair<String, String> {
        val code = InviteCodes.generate()
        val householdId = alice.households.createHousehold(alice.uid, "Alice", "Test home", code)
        val session = alice.awaitSession { it is Session.InHousehold } as Session.InHousehold
        assertEquals(householdId, session.householdId)
        assertEquals(Role.Owner, session.role)
        return householdId to code
    }

    private suspend fun bobRequestsToJoin(code: String): JoinState {
        val invite = checkNotNull(bob.households.findInviteCode(code))
        assertTrue(invite.active)
        assertEquals("Test home", invite.householdName)
        bob.households.requestToJoin(bob.uid, "Bob", invite)
        val waiting = bob.awaitSession { (it as? Session.NoHousehold)?.join is JoinState.Waiting }
        return checkNotNull((waiting as Session.NoHousehold).join)
    }

    private suspend fun awaitRequestFromBob(householdId: String) = withTimeout(10_000) {
        alice.households.observeJoinRequests(householdId).first { list -> list.any { it.uid == bob.uid } }
    }.single { it.uid == bob.uid }

    private suspend fun aliceAndBobInOneHousehold(): String {
        val (householdId, code) = aliceCreatesHousehold()
        bobRequestsToJoin(code)
        alice.households.approve(householdId, awaitRequestFromBob(householdId))
        bob.awaitSession { it is Session.InHousehold }
        return householdId
    }
}
