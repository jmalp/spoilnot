package com.parcha.spoilnot.core.firebase

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where a changes-only listener starts (DESIGN 9.8, after v1 2026-11-09, SYNC-01): from the newest change the
 * phone's server listener received, never from the cache. Before, a phone that wrote to the list, the week or
 * Recent activity while not showing them (the restock engine, Plan this recipe) skipped the other members' earlier
 * changes until the next daily full read. Review 04's `p01`, with a margin of [MARGIN] instead of 5 minutes.
 */
@RunWith(AndroidJUnit4::class)
class SyncCursorFlowTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var householdId: String
    private lateinit var listId: String
    private val today: LocalDate = LocalDate.now()
    private val noPantry = PantryData(emptyList(), emptyList(), fromCache = false)

    @Before
    fun setUp() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        alice = TestPhone("alice", cursorMargin = MARGIN)
        bob = TestPhone("bob", cursorMargin = MARGIN)
        alice.sessionManager.signUp("Alice", uniqueEmail("alice"), PASSWORD)
        bob.sessionManager.signUp("Bob", uniqueEmail("bob"), PASSWORD)
        alice.awaitSession { it is Session.NoHousehold }
        bob.awaitSession { it is Session.NoHousehold }
        val code = InviteCodes.generate()
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Cursor test", code)
        alice.awaitSession { it is Session.InHousehold }
        bob.households.requestToJoin(bob.uid, "Bob", checkNotNull(bob.households.findInviteCode(code)))
        val request = withTimeout(10_000) {
            alice.households.observeJoinRequests(householdId).first { it.isNotEmpty() }
        }.single()
        alice.households.approve(householdId, request)
        bob.awaitSession { it is Session.InHousehold }
        listId = withTimeout(10_000) {
            bob.groceryRepository.observeLists(householdId).first { it.any { l -> l.isDefault } }
        }.single { it.isDefault }.id
    }

    @After
    fun tearDown() {
        if (::alice.isInitialized) alice.close()
        if (::bob.isInitialized) bob.close()
    }

    private suspend fun TestPhone.items(predicate: (List<GroceryItem>) -> Boolean): List<GroceryItem> =
        withTimeout(20_000) { groceryRepository.observeItems(householdId, listId).first(predicate) }

    private suspend fun TestPhone.flush() = withTimeout(30_000) { firestore.current.waitForPendingWrites().await() }

    /** The last value [this] emits within [ms]: a new listener's first snapshot may be the stale cache. */
    private suspend fun <T> Flow<T>.settled(ms: Long = 5_000): T? {
        var last: T? = null
        withTimeoutOrNull(ms) { collect { last = it } }
        return last
    }

    /** What the server holds (tombstones left out), read through Bob: a server read on Alice would refill her cache. */
    private suspend fun server(path: String) = bob.firestore.current.collection(path).get(Source.SERVER).await()
        .documents.filterNot { with(Tombstones) { it.isTombstone() } }

    @Test
    fun ownWritesWhileNotListeningDoNotSkipOtherMembersChanges() = runBlocking {
        val itemsPath = "households/$householdId/groceryLists/$listId/items"
        val activityPath = "households/$householdId/activity"
        val from = today
        val to = today.plusDays(6)
        val mealsKey = "households/$householdId/meals?date=$from..$to"

        bob.grocery.quickAdd(householdId, bob.actor, listId, "Butter", emptyList(), noPantry)
        bob.grocery.quickAdd(householdId, bob.actor, listId, "Bread", emptyList(), noPantry)
        bob.mealPlan.addOther(householdId, bob.actor, today, MealSlot.Lunch, "Pasta", emptyList(), emptyList())
        bob.flush()

        // Alice opens Groceries, Home's activity and the week once (full reads), then leaves those screens.
        alice.items { it.size == 2 }
        withTimeout(20_000) {
            alice.groceryRepository.observeItems(householdId, listId)
                .first { alice.syncLog.newestSeen(itemsPath) != null && alice.syncLog.lastFullSync(itemsPath) != null }
        }
        withTimeout(20_000) {
            alice.activity.observeRecent(householdId, 30).first { alice.syncLog.newestSeen(activityPath) != null }
        }
        withTimeout(20_000) {
            alice.mealPlanRepository.observe(householdId, from, to).first { alice.syncLog.newestSeen(mealsKey) != null }
        }
        val fullRead = alice.syncLog.lastFullSync(itemsPath)
        val seen = alice.syncLog.newestSeen(itemsPath)

        // While Alice listens to none of them, Bob adds Eggs, checks Bread, deletes Butter and plans Tacos.
        val bobItems = bob.items { it.size == 2 }
        bob.grocery.quickAdd(householdId, bob.actor, listId, "Eggs", bobItems, noPantry)
        bob.grocery.setChecked(householdId, bob.actor, listId, bobItems.single { it.name == "Bread" }, true)
        bob.grocery.delete(householdId, bob.actor, listId, bobItems.single { it.name == "Butter" })
        bob.mealPlan.addOther(
            householdId,
            bob.actor,
            today.plusDays(1),
            MealSlot.Dinner,
            "Tacos",
            emptyList(),
            emptyList(),
        )
        bob.flush()

        delay(MARGIN.multipliedBy(3).toMillis())

        // Alice writes to all three without listening (as the restock engine and Plan this recipe do), and the
        // writes reach the server: Alice's cache now holds newer server times than anything received.
        alice.grocery.quickAdd(householdId, alice.actor, listId, "Milk", emptyList(), noPantry)
        alice.mealPlan.addOther(
            householdId,
            alice.actor,
            today.plusDays(2),
            MealSlot.Lunch,
            "Soup",
            emptyList(),
            emptyList(),
        )
        alice.flush()
        assertEquals("Alice's own write doesn't move the cursor", seen, alice.syncLog.newestSeen(itemsPath))

        val aliceItems = checkNotNull(alice.groceryRepository.observeItems(householdId, listId).settled())
        val aliceEvents = checkNotNull(alice.activity.observeRecent(householdId, 30).settled())
        val aliceMeals = checkNotNull(alice.mealPlanRepository.observe(householdId, from, to).settled())

        fun label(name: String?, checked: Boolean) = "$name${if (checked) " (checked)" else ""}"
        val truthItems = server(itemsPath).map { label(it.getString("name"), it.getBoolean("checked") == true) }
        val truthMeals = server("households/$householdId/meals").mapNotNull { it.getString("title") }
        val truthEvents = server(activityPath).mapNotNull { it.get("params.name") as? String }
        Log.i(TAG, "items server=${truthItems.sorted()} alice=${aliceItems.map { label(it.name, it.checked) }}")
        assertEquals(listOf("Bread (checked)", "Eggs", "Milk"), truthItems.sorted())
        assertEquals("Alice's list", truthItems.sorted(), aliceItems.map { label(it.name, it.checked) }.sorted())
        assertEquals("Alice's week", truthMeals.sorted(), aliceMeals.meals.map { it.title }.sorted())
        assertEquals("Alice's Recent activity", truthEvents.sorted(), aliceEvents.mapNotNull { it.name }.sorted())
        assertEquals("changes only: no new full read", fullRead, alice.syncLog.lastFullSync(itemsPath))
        val after = checkNotNull(alice.syncLog.newestSeen(itemsPath))
        assertTrue("the cursor moved once the server sent the changes", after > checkNotNull(seen))
    }

    private companion object {
        const val TAG = "SyncCursorFlowTest"
        val MARGIN: Duration = Duration.ofSeconds(2)
    }
}
