package com.parcha.spoilnot.feature.insights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.insights.domain.MonthStats
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Insights counts (DESIGN 5.7) through the real rules: one phone uses up and throws away batches, the other sees the
 * month's counts, and Undo takes them back.
 */
@RunWith(AndroidJUnit4::class)
class InsightsFlowTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var householdId: String
    private val today: LocalDate = LocalDate.now()
    private val month: YearMonth = YearMonth.from(today)

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
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Insights test", code)
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
        name, 1.0, QuantityUnit.Pieces, today.plusDays(3), DateType.UseBy, Location.Fridge, Category.Other,
        null, null, false, null,
    )

    private suspend fun TestPhone.data(predicate: (PantryData) -> Boolean): PantryData =
        withTimeout(20_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    private suspend fun TestPhone.month(predicate: (MonthStats?) -> Boolean): MonthStats? = withTimeout(20_000) {
        stats.observeMonths(householdId, month).first { months -> predicate(months.singleOrNull()) }
    }.singleOrNull()

    @Test
    fun usedUpAndThrownAwayAreCountedForTheHouseholdAndUndone() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Milk"), alice.data { true })
        alice.pantry.add(householdId, alice.actor, draft("Bread"), alice.data { it.batches.size == 1 })
        val data = alice.data { it.batches.size == 2 }

        alice.pantry.adjust(householdId, alice.actor, data.batches.single { it.productName == "Milk" }, -1.0)
        val undo = alice.pantry.throwAway(
            householdId,
            alice.actor,
            data.batches.single { it.productName == "Bread" },
            DiscardReason.Spoiled,
        )
        val counted = bob.month { it != null && it.used == 1 && it.thrownTotal == 1 }!!
        assertEquals(mapOf(DiscardReason.Spoiled to 1), counted.thrown)
        assertEquals(mapOf(today.dayOfMonth to 1), counted.thrownByDay)

        alice.pantry.undo(householdId, alice.actor, undo)
        val undone = bob.month { it != null && it.thrownTotal == 0 }!!
        assertEquals(1, undone.used)
        assertEquals(emptyMap<Int, Int>(), undone.thrownByDay)
        bob.data { d -> d.batches.any { it.productName == "Bread" } }
        Unit
    }
}
