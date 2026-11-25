package com.parcha.spoilnot.core.write

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.error.AppError.LostRace.Race
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
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.recipes.domain.Ingredient
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.SaveResult
import java.time.LocalDate
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * When two phones finish the same thing, the rules pick the winner and refuse the other phone's whole batch, and that
 * phone says why (DESIGN 8.5): Add to pantry at once or from an offline phone (review 04's p02 and p03), Cooked on
 * both phones (p07), and an item added while another member deletes the list (p09).
 */
@RunWith(AndroidJUnit4::class)
class OnePhoneWinsFlowTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var householdId: String
    private lateinit var listId: String
    private val today: LocalDate = LocalDate.now()

    /** What each phone's writer reported, collected from before the action. */
    private val refusals = Collections.synchronizedList(mutableListOf<Pair<String, AppError>>())
    private val collectors = mutableListOf<Job>()

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
        householdId = alice.households.createHousehold(alice.uid, "Alice", "One phone wins", code)
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
    }

    @After
    fun tearDown() {
        collecting.cancel()
        if (::alice.isInitialized) {
            runBlocking { runCatching { alice.firestore.current.enableNetwork().await() } }
            alice.close()
        }
        if (::bob.isInitialized) {
            runBlocking { runCatching { bob.firestore.current.enableNetwork().await() } }
            bob.close()
        }
    }

    /** Collects outside the test's runBlocking, which would otherwise wait for the collectors forever. */
    private val collecting = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun collectRefusals() {
        for ((name, phone) in listOf("alice" to alice, "bob" to bob)) {
            collectors += collecting.launch(start = CoroutineStart.UNDISPATCHED) {
                phone.writer.errors.collect { refusals += name to it }
            }
        }
    }

    /** The one refusal both phones reported, once it has arrived and nothing else followed for a moment. */
    private suspend fun singleRefusal(): Pair<String, AppError> {
        withTimeoutOrNull(20_000) { while (refusals.isEmpty()) delay(100) }
        delay(1_500)
        assertEquals("refusals: $refusals", 1, refusals.size)
        return refusals.single()
    }

    private suspend fun TestPhone.flush() = withTimeout(30_000) { firestore.current.waitForPendingWrites().await() }

    private suspend fun TestPhone.pantryData(predicate: (PantryData) -> Boolean = { true }): PantryData =
        withTimeout(20_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    private suspend fun TestPhone.items(predicate: (List<GroceryItem>) -> Boolean): List<GroceryItem> =
        withTimeout(20_000) { groceryRepository.observeItems(householdId, listId).first(predicate) }

    /** What the server holds, tombstones left out, read past both phones' caches. */
    private suspend fun server(path: String) = alice.firestore.current.collection(path).get(Source.SERVER).await()
        .documents.filter { it.get("deletedAt") == null }

    private fun checkedItem(id: String, name: String, quantity: Double) = GroceryItem(
        id = id, name = name, nameKey = NameKey.of(name), quantity = quantity, unit = QuantityUnit.Pieces, note = null,
        category = Category.Dairy, checked = true, checkedBy = null, source = ItemSource.Manual, productId = null,
        recipeId = null, createdBy = null, createdAt = null,
    )

    private suspend fun seedCheckedList() {
        bob.writer.apply(
            householdId,
            bob.actor,
            listOf(
                GroceryChange.CreateItem(listId, checkedItem(bob.writer.newId(), "Milk", 2.0)),
                GroceryChange.CreateItem(listId, checkedItem(bob.writer.newId(), "Rice", 1.0)),
            ),
        )
        bob.flush()
    }

    private suspend fun TestPhone.pressAddToPantry(items: List<GroceryItem>, data: PantryData? = null) =
        grocery.addToPantry(
            householdId,
            actor,
            listId,
            items.map { PantryEntry(it, it.quantity ?: 1.0, QuantityUnit.Pieces, today.plusDays(7), Location.Pantry) },
            emptyList(),
            data ?: pantryData(),
        )

    private suspend fun assertAddedOnce() {
        val products = server("households/$householdId/products").mapNotNull { it.getString("name") }.sorted()
        val stock = server("households/$householdId/stock")
            .map { "${it.getString("productName")}:${it.getDouble("quantity")}" }.sorted()
        assertEquals("products", listOf("Milk", "Rice"), products)
        assertEquals("purchases", listOf("Milk:2.0", "Rice:1.0"), stock)
        assertEquals("items left", 0, server("households/$householdId/groceryLists/$listId/items").size)
    }

    // p02
    @Test
    fun addToPantryOnBothPhonesAtOnce_addsOnce_andTheLaterPhoneSaysWhy() = runBlocking {
        seedCheckedList()
        val a = alice.items { it.size == 2 && it.all { i -> i.checked } }
        val b = bob.items { it.size == 2 && it.all { i -> i.checked } }
        collectRefusals()
        listOf(async { alice.pressAddToPantry(a) }, async { bob.pressAddToPantry(b) }).awaitAll()
        alice.flush()
        bob.flush()
        val (loser, error) = singleRefusal()
        assertAddedOnce()
        val winner = if (loser == "alice") bob else alice
        assertTrue("$error", error is AppError.LostRace && error.race == Race.AddedToPantry)
        assertEquals(winner.uid, (error as AppError.LostRace).byUid)
        // Both phones converge on one of each.
        for (phone in listOf(alice, bob)) {
            val seen = phone.pantryData { it.batches.size == 2 }
            assertEquals(listOf("Milk", "Rice"), seen.products.map { it.name }.sorted())
        }
    }

    // p03
    @Test
    fun addToPantryOfflineThenOnline_addsOnce_andTheOfflinePhoneSaysWhy() = runBlocking {
        seedCheckedList()
        val a = alice.items { it.size == 2 && it.all { i -> i.checked } }
        val b = bob.items { it.size == 2 && it.all { i -> i.checked } }
        val aliceData = alice.pantryData()
        collectRefusals()
        alice.firestore.current.disableNetwork().await()
        // In the store with no signal: the cache takes it, the screen says it was added.
        alice.pressAddToPantry(a, aliceData)
        delay(1_000)
        bob.pressAddToPantry(b)
        bob.flush()
        alice.firestore.current.enableNetwork().await()
        alice.flush()
        val (loser, error) = singleRefusal()
        assertEquals("the phone that was offline loses", "alice", loser)
        assertEquals(Race.AddedToPantry, (error as AppError.LostRace).race)
        assertEquals(bob.uid, error.byUid)
        assertAddedOnce()
        val seen = alice.pantryData { it.batches.size == 2 && it.products.size == 2 }
        assertEquals(listOf("Milk", "Rice"), seen.products.map { it.name }.sorted())
    }

    // p07
    @Test
    fun cookedOnBothPhones_takesOnce_andUndoStillPutsItBack() = runBlocking {
        alice.pantry.add(householdId, alice.actor, rice(5.0), alice.pantryData())
        val pantry = alice.pantryData { it.products.size == 1 }
        val rice = pantry.products.single()
        val content = RecipeContent(
            title = "Risotto",
            servings = 2,
            ingredients = listOf(Ingredient("2 rice", 2.0, null, "rice")),
        )
        val saved = alice.recipes.save(householdId, alice.actor, content, emptyList()) as SaveResult.Saved
        val recipe = Recipe(saved.recipeId, content)
        alice.mealPlan.addRecipe(householdId, alice.actor, today, MealSlot.Dinner, recipe, emptyList(), emptyList())
        alice.flush()
        val aMeal = withTimeout(20_000) {
            alice.mealPlanRepository.observe(householdId, today, today).first { !it.fromCache && it.meals.size == 1 }
        }.meals.single()
        val bMeal = withTimeout(20_000) {
            bob.mealPlanRepository.observe(householdId, today, today).first { !it.fromCache && it.meals.size == 1 }
        }.meals.single()
        val bobData = bob.pantryData { it.batches.size == 1 }
        val amounts = mapOf(rice.id to 2.0)
        collectRefusals()
        val undos = listOf(
            async { "alice" to alice.mealPlan.cooked(householdId, alice.actor, aMeal, recipe, amounts, pantry) },
            async { "bob" to bob.mealPlan.cooked(householdId, bob.actor, bMeal, recipe, amounts, bobData) },
        ).awaitAll().toMap()
        alice.flush()
        bob.flush()
        val (loser, error) = singleRefusal()
        val winner = if (loser == "alice") bob else alice
        assertEquals(Race.Cooked, (error as AppError.LostRace).race)
        assertEquals(winner.uid, error.byUid)
        val left = server("households/$householdId/stock").sumOf { it.getDouble("quantity") ?: 0.0 }
        assertEquals("rice left", 3.0, left, 0.0)
        val cooked = server("households/$householdId/activity").count { it.getString("type") == "recipe_cooked" }
        assertEquals("cooked events", 1, cooked)
        // The winner's Undo puts the rice back and the meal is not cooked again.
        val winnerName = if (loser == "alice") "bob" else "alice"
        winner.mealPlan.undo(householdId, winner.actor, checkNotNull(undos[winnerName]))
        winner.flush()
        val back = server("households/$householdId/stock").sumOf { it.getDouble("quantity") ?: 0.0 }
        assertEquals("rice after Undo", 5.0, back, 0.0)
        val meal = alice.firestore.current.document("households/$householdId/meals/${aMeal.id}").get(Source.SERVER)
            .await()
        assertEquals(null, meal.get("cookedAt"))
    }

    // p09
    @Test
    fun listDeletedWhileTheOtherAddsOffline_theAddIsRefusedAndSaysSo() = runBlocking {
        val party = alice.grocery.createList(householdId, alice.actor, "Party")
        alice.flush()
        withTimeout(10_000) { bob.groceryRepository.observeLists(householdId).first { l -> l.any { it.id == party } } }
        collectRefusals()
        bob.firestore.current.disableNetwork().await()
        bob.grocery.quickAdd(
            householdId,
            bob.actor,
            party,
            "Chips",
            emptyList(),
            PantryData(emptyList(), emptyList(), false),
        )
        val aliceList = withTimeout(10_000) {
            alice.groceryRepository.observeLists(householdId).first { l -> l.any { it.id == party } }
        }.single { it.id == party }
        alice.grocery.deleteList(householdId, alice.actor, aliceList)
        alice.flush()
        bob.firestore.current.enableNetwork().await()
        bob.flush()
        val (loser, error) = singleRefusal()
        assertEquals("bob", loser)
        assertEquals(Race.ListDeleted, (error as AppError.LostRace).race)
        val orphans = alice.firestore.current.collection("households/$householdId/groceryLists/$party/items")
            .get(Source.SERVER).await().documents
        assertTrue("no item left under a deleted list: ${orphans.map { it.id }}", orphans.isEmpty())
    }

    private fun rice(quantity: Double) = StockDraft(
        "Rice", quantity, QuantityUnit.Pieces, today.plusDays(300), DateType.BestBefore, Location.Pantry,
        Category.Other, note = null, minQuantity = null, autoAddToGrocery = false, buyQuantity = null,
    )
}
