package com.parcha.spoilnot.core.firebase

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.ListenSource
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SnapshotListenOptions
import com.parcha.spoilnot.EmulatorAdmin
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bad data skips one document, not the screen (after v1, review 05 REL-04 and REL-11): review 05's round-1 documents,
 * one wrong-typed field per collection written past the rules, now leave every list showing, the wrong document
 * included with the field read as missing. Before, each one blanked its screen on every phone until the app restarted.
 */
@RunWith(AndroidJUnit4::class)
class BadDataFlowTest {
    private lateinit var phone: TestPhone
    private lateinit var householdId: String
    private lateinit var listId: String
    private val today: LocalDate = LocalDate.now()
    private val others = mutableListOf<TestPhone>()

    @Before
    fun setUp() = runBlocking<Unit> {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        phone = TestPhone("bad")
        phone.sessionManager.signUp("Bea", uniqueEmail("bea"), PASSWORD)
        phone.awaitSession { it is Session.NoHousehold }
        householdId = phone.households.createHousehold(phone.uid, "Bea", "Bad data", InviteCodes.generate())
        phone.awaitSession { it is Session.InHousehold }
        listId = withTimeout(10_000) {
            phone.groceryRepository.observeLists(householdId).first { it.any { l -> l.isDefault } }
        }.single { it.isDefault }.id
    }

    @After
    fun tearDown() {
        others.forEach { it.close() }
        if (::phone.isInitialized) phone.close()
    }

    private fun draft(name: String) = StockDraft(
        name, 1.0, QuantityUnit.Pieces, today.plusDays(4), DateType.UseBy, Location.Fridge, Category.Other,
        null, null, false, null,
    )

    private suspend fun pantry(predicate: (PantryData) -> Boolean): PantryData =
        withTimeout(15_000) { phone.pantryRepository.observe(householdId).first(predicate) }

    private fun admin(path: String, fields: Map<String, Any?>) =
        EmulatorAdmin.putDocument("households/$householdId/$path", fields)

    private fun stamps(): Map<String, Any?> {
        val now = Instant.now()
        return mapOf("createdAt" to now, "createdBy" to phone.uid, "updatedAt" to now, "updatedBy" to phone.uid)
    }

    @Test
    fun reviewFivesRoundOneShowsEveryList() = runBlocking<Unit> {
        phone.pantry.add(householdId, phone.actor, draft("Cheese"), pantry { true })
        pantry { it.batches.any { b -> b.productName == "Cheese" } }

        withContext(Dispatchers.IO) {
            admin(
                "stock/bad-qty",
                mapOf(
                    "productId" to "p-b0", "productName" to "Milk", "unit" to "pcs", "category" to "dairy",
                    "quantity" to "2", "expiryDate" to today.plusDays(3).toString(), "dateType" to "use_by",
                    "location" to "fridge", "note" to null, "openedOn" to null,
                ) + stamps(),
            )
            admin(
                "groceryLists/$listId/items/bad-checked",
                mapOf("name" to "Bad checked", "nameKey" to "bad checked", "checked" to "yes", "source" to "manual") +
                    stamps(),
            )
            admin(
                "recipes/bad-servings",
                mapOf("title" to "Bad servings", "servings" to "4", "ingredients" to emptyList<Any>()) + stamps(),
            )
            admin(
                "meals/bad-position",
                mapOf("date" to today.toString(), "slot" to "lunch", "position" to "1", "title" to "Bad position") +
                    stamps(),
            )
            admin(
                "activity/bad-name",
                mapOf(
                    "type" to "stock_added",
                    "actorUid" to phone.uid,
                    "actorName" to 42L,
                    "params" to mapOf("name" to "Bad name type"),
                    "createdAt" to Instant.now(),
                ),
            )
        }

        // Pantry (and so Groceries and Home, which combine it): the good batch and the bad one, read as 0.
        val data = pantry { d -> d.batches.any { it.id == "bad-qty" } && d.batches.any { it.productName == "Cheese" } }
        assertEquals(0.0, data.batches.single { it.id == "bad-qty" }.quantity, 0.0)
        val items = withTimeout(15_000) {
            phone.groceryRepository.observeItems(householdId, listId).first { l -> l.any { it.id == "bad-checked" } }
        }
        assertEquals(false, items.single { it.id == "bad-checked" }.checked)
        val box = withTimeout(15_000) {
            phone.recipeRepository.observe(householdId).first { b -> b.recipes.any { it.id == "bad-servings" } }
        }
        assertNull(box.recipes.single { it.id == "bad-servings" }.content.servings)
        val week = withTimeout(15_000) {
            phone.mealPlanRepository.observe(householdId, today, today).first { p ->
                p.meals.any {
                    it.id ==
                        "bad-position"
                }
            }
        }
        assertEquals(0L, week.meals.single { it.id == "bad-position" }.position)
        val recent = withTimeout(15_000) {
            phone.activity.observeRecent(householdId, 10).first { e -> e.any { it.id == "bad-name" } }
        }
        assertEquals("", recent.single { it.id == "bad-name" }.actorName)

        // A second phone opening the household for the first time reads all of it in full, and loads too.
        val other = TestPhone("bad-other").also { others += it }
        other.sessionManager.signIn(checkNotNull(phone.authRepository.currentUser?.email), PASSWORD)
        other.awaitSession { it is Session.InHousehold }
        withTimeout(15_000) {
            other.pantryRepository.observe(householdId).first { d -> d.batches.any { it.id == "bad-qty" } }
        }
    }

    @Test
    fun aCleanUpTheRulesRefuseIsTriedOnceAndSaysNothing() = runBlocking<Unit> {
        val refusals = Collections.synchronizedList(mutableListOf<AppError>())
        val cleanUps = Collections.synchronizedList(mutableListOf<List<HouseholdChange>>())
        val scope = CoroutineScope(Dispatchers.Default)
        phone.writer.errors.onEach { refusals += it }.launchIn(scope)
        phone.writer.localPlans.onEach { plan -> if (plan.any { it is PantryChange.DeleteBatch }) cleanUps += plan }
            .launchIn(scope)
        try {
            // An empty batch with a field the rules don't accept: every write to it, its clean-up included, is
            // refused (review 05 REL-11 had a −3 batch, which the rules accept since 2026-11-10).
            withContext(Dispatchers.IO) {
                admin(
                    "stock/stuck",
                    mapOf(
                        "productId" to "p-stuck",
                        "productName" to "Stuck",
                        "unit" to "pcs",
                        "category" to "other",
                        "quantity" to 0L,
                        "dateType" to "use_by",
                        "location" to "fridge",
                        "futureField" to true,
                    ) + stamps(),
                )
            }
            val seen = pantry { d -> d.batches.any { it.id == "stuck" && !it.pendingSync } }
            phone.pantry.deleteEmptyBatches(householdId, phone.actor, seen)
            // The server refuses it and the phone rolls its tombstone back.
            withTimeout(15_000) { phone.firestore.current.waitForPendingWrites().await() }
            val back = pantry { d -> d.batches.any { it.id == "stuck" && !it.pendingSync } }
            delay(500)
            // Seen again, as Pantry sees it on every change: no second write, and no message.
            repeat(3) { phone.pantry.deleteEmptyBatches(householdId, phone.actor, back) }
            delay(1_000)
            assertEquals(1, cleanUps.size)
            assertEquals(emptyList<AppError>(), refusals.toList())
            assertTrue(
                withContext(Dispatchers.IO) {
                    EmulatorAdmin.documentExists("households/$householdId/stock/stuck")
                },
            )
        } finally {
            scope.cancel()
        }
    }

    /**
     * B11 found on the web that the SDK hands a refused server listen to the cache listener of the same query. This
     * checks it on Android: a phone that isn't a member listens to a household's products from the server and from
     * the cache at once; the cache listener of [cachedLiveDocuments] listens again ([retryCacheRefusals]) instead of
     * failing with the server's refusal.
     */
    @Test
    fun aServerRefusalDoesNotEndTheCacheListenerOfTheSameQuery() = runBlocking<Unit> {
        val stranger = TestPhone("stranger").also { others += it }
        stranger.sessionManager.signUp("Sid", uniqueEmail("sid"), PASSWORD)
        stranger.awaitSession { it is Session.NoHousehold }
        val db = stranger.firestore.current
        val products = db.collection("households/$householdId/products")

        // The SDK itself: does the raw cache listener get the server's refusal? Each raw listener is removed as soon
        // as it fails, as the app's callbackFlow does: removing a refused server listener after another listener of
        // the same query has started trips an assertion in the SDK ("stopListening called on target not currently
        // watched").
        val rawErrors = Collections.synchronizedList(mutableListOf<String>())
        val cacheOnly = SnapshotListenOptions.Builder()
            .setSource(ListenSource.CACHE)
            .setMetadataChanges(MetadataChanges.INCLUDE)
            .build()
        lateinit var raw: ListenerRegistration
        raw = products.addSnapshotListener(cacheOnly) { _, e ->
            if (e != null) {
                rawErrors += e.code.name
                raw.remove()
            }
        }
        val serverErrors = Collections.synchronizedList(mutableListOf<String>())
        lateinit var server: ListenerRegistration
        server = products.addSnapshotListener { _, e ->
            if (e != null) {
                serverErrors += e.code.name
                server.remove()
            }
        }

        // The app's cache listener on the same query, with its retry.
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch { products.cachedLiveDocuments().catch { failures += it }.collect {} }
        try {
            withTimeout(10_000) { while (serverErrors.isEmpty()) delay(100) }
            delay(1_500)
            Log.i("BadDataFlowTest", "server refusal $serverErrors; raw cache listener got $rawErrors")
            // Android behaves as the web: the cache listener gets the server's refusal of the same query.
            assertEquals(listOf("PERMISSION_DENIED"), rawErrors.toList())
            assertEquals(emptyList<Throwable>(), failures.toList())
        } finally {
            scope.cancel()
        }
    }
}
