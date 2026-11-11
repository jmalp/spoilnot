package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryEvent
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the restock engine runs and which phone writes (DESIGN 9.6): not on a first sync until both products and
 * stock have come from the server, at once for a change made on this phone, after a delay for one from another phone
 * (and not at all if that phone's write arrives first), and again after a failed listener. After v1 (B09) it reads
 * the local cache only and never opens a server listener of its own.
 */
class RestockRunnerTest {
    private val list = GroceryList("g1", "Groceries", isDefault = true)
    private val other = RestockEngine.OTHER_PHONE_DELAY_MS

    private fun milk(armed: Boolean = true) = product("milk", min = 2.0).copy(restockArmed = armed)

    private fun data(armed: Boolean = true, quantity: Double = 1.0) =
        PantryData(listOf(milk(armed)), listOf(batch("b1", "milk", quantity = quantity)), false, serverRead = true)

    private val session = mockk<SessionManager> {
        every { session } returns MutableStateFlow(
            Session.InHousehold(AuthUser("u1", null, "Sam", true), "Sam", "h1", Role.Owner),
        )
    }

    /** Server listeners the engine asked for; after v1 (B09) it reads the cache only, so none. */
    private val serverListeners = mutableListOf<String>()

    private val grocery = object : GroceryRepository {
        override fun observeLists(householdId: String): Flow<List<GroceryList>> {
            serverListeners += "lists"
            return flowOf(listOf(list))
        }

        override fun observeCachedLists(householdId: String) = flowOf(listOf(list))

        override fun observeItems(householdId: String, listId: String): Flow<List<GroceryItem>> = emptyFlow()

        override suspend fun isCached(householdId: String, listId: String, itemId: String) = false

        override suspend fun itemIds(householdId: String, listId: String) = emptyList<String>()
    }

    private class Writes : HouseholdWriter {
        val plans = mutableListOf<List<HouseholdChange>>()
        override val localPlans = MutableSharedFlow<List<HouseholdChange>>(extraBufferCapacity = 8)
        override val errors: Flow<AppError> = emptyFlow()

        override fun newId() = "id"

        override suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>) {
            plans += changes
        }
    }

    private inner class Pantry(private val flows: () -> Flow<PantryData>) : PantryRepository {
        override fun observe(householdId: String): Flow<PantryData> {
            serverListeners += "pantry"
            return flows()
        }

        override fun observeCached(householdId: String) = flows()

        override suspend fun removedBy(householdId: String, batchId: String): String? = null

        override fun observeEvents(householdId: String, productId: String, limit: Int): Flow<List<PantryEvent>> =
            emptyFlow()
    }

    /** A clock that follows the test's virtual time. */
    private class VirtualClock(private val scope: TestScope) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(scope.testScheduler.currentTime)
    }

    private fun TestScope.runner(pantry: PantryRepository, writer: Writes) =
        RestockRunner(session, pantry, grocery, writer, backgroundScope, VirtualClock(this)).also { it.start() }

    private val lowStockPlan = listOf(GroceryChange.UpsertLowStock::class, PantryChange.SetRestockArmed::class)

    private fun Writes.types() = plans.map { plan -> plan.map { it::class } }

    @Test
    fun `on a first sync it waits until both products and stock have come from the server`() = runTest {
        // LOGIC-02: the products arrive before the stock, so every staple looks empty for a moment.
        val snapshots = MutableSharedFlow<PantryData>(replay = 1)
        val writer = Writes()
        runner(Pantry { snapshots }, writer)
        snapshots.emit(PantryData(listOf(milk()), emptyList(), fromCache = true, serverRead = false))
        advanceTimeBy(400 + other)
        snapshots.emit(PantryData(listOf(milk()), emptyList(), fromCache = false, serverRead = false))
        advanceTimeBy(400 + other)
        assertTrue(writer.plans.isEmpty())
        // The stock arrives: Milk has plenty, so nothing is added.
        snapshots.emit(data(quantity = 3.0))
        advanceTimeBy(400 + other)
        assertTrue(writer.plans.isEmpty())
    }

    @Test
    fun `acts at once on a change made on this phone`() = runTest {
        val snapshots = MutableSharedFlow<PantryData>(replay = 1)
        val writer = Writes()
        runner(Pantry { snapshots }, writer)
        runCurrent()
        // Used up on this phone: the writer reports the plan, then the cache shows it.
        writer.localPlans.emit(listOf(PantryChange.DeleteBatch("b2")))
        snapshots.emit(data())
        advanceTimeBy(400)
        assertEquals(listOf(lowStockPlan), writer.types())
    }

    @Test
    fun `two phones evaluating at once - the other phone leaves it to the one that made the change`() = runTest {
        // EFF-09: phone A used up the milk and wrote the item at once; phone B saw the same drop.
        val snapshots = MutableSharedFlow<PantryData>(replay = 1)
        val phoneB = Writes()
        runner(Pantry { snapshots }, phoneB)
        snapshots.emit(data())
        advanceTimeBy(400 + other / 2)
        assertTrue(phoneB.plans.isEmpty())
        // Phone A's write arrives: the product is disarmed, so phone B has nothing left to write.
        snapshots.emit(data(armed = false))
        advanceTimeBy(400 + other)
        assertTrue(phoneB.plans.isEmpty())
    }

    @Test
    fun `the other phone writes after the delay when nobody else did`() = runTest {
        val snapshots = MutableSharedFlow<PantryData>(replay = 1)
        val writer = Writes()
        runner(Pantry { snapshots }, writer)
        snapshots.emit(data())
        advanceTimeBy(300 + other - 100)
        assertTrue(writer.plans.isEmpty())
        advanceTimeBy(200)
        assertEquals(listOf(lowStockPlan), writer.types())
    }

    @Test
    fun `the engine's own writes do not count as this phone's change`() = runTest {
        val snapshots = MutableSharedFlow<PantryData>(replay = 1)
        val writer = Writes()
        runner(Pantry { snapshots }, writer)
        runCurrent()
        writer.localPlans.emit(listOf(PantryChange.SetRestockArmed("milk", true)))
        snapshots.emit(data())
        advanceTimeBy(400)
        assertTrue(writer.plans.isEmpty())
    }

    @Test
    fun `listens again with a backoff after a failed listener instead of stopping`() = runTest {
        // SYNC-10: one listener error used to stop the engine until the app restarted.
        var attempts = 0
        val pantry = Pantry {
            flow {
                if (attempts++ < 2) throw AppError.QuotaExceeded()
                emit(data())
            }
        }
        val writer = Writes()
        runner(pantry, writer)
        advanceTimeBy(900)
        assertEquals(1, attempts)
        advanceTimeBy(200) // 1 s
        assertEquals(2, attempts)
        advanceTimeBy(2_000) // then 2 s
        assertEquals(3, attempts)
        advanceTimeBy(300 + other + 100)
        assertEquals(listOf(lowStockPlan), writer.types())
    }

    @Test
    fun `reads the cache only and opens no server listener`() = runTest {
        // EFF-01: the engine's own server listeners never stopped, so every return after 30 minutes re-read them.
        val snapshots = MutableSharedFlow<PantryData>(replay = 1)
        val writer = Writes()
        runner(Pantry { snapshots }, writer)
        snapshots.emit(data())
        advanceTimeBy(400 + other)
        assertEquals(listOf(lowStockPlan), writer.types())
        assertEquals(emptyList<String>(), serverListeners)
    }

    @Test
    fun `a cache with no full read on record waits, then acts once the full read is recorded`() = runTest {
        // The cache-only flow says serverRead once a full read of both collections is on record (DESIGN 9.6).
        val snapshots = MutableSharedFlow<PantryData>(replay = 1)
        val writer = Writes()
        runner(Pantry { snapshots }, writer)
        snapshots.emit(data().copy(fromCache = true, serverRead = false))
        advanceTimeBy(400 + other)
        assertTrue(writer.plans.isEmpty())
        snapshots.emit(data().copy(fromCache = true, serverRead = true))
        advanceTimeBy(400 + other)
        assertEquals(listOf(lowStockPlan), writer.types())
    }
}
