package com.parcha.spoilnot.feature.recipes

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import com.parcha.spoilnot.feature.recipes.domain.Cooking
import com.parcha.spoilnot.feature.recipes.domain.ImportMethod
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeBoxData
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeGrocery
import com.parcha.spoilnot.feature.recipes.domain.RecipeLines
import com.parcha.spoilnot.feature.recipes.domain.SaveResult
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

/** The recipe box shared by two phones against the emulators and the real security rules (DESIGN 5.5, 12.3). */
@RunWith(AndroidJUnit4::class)
class RecipeFlowTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var householdId: String

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
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Recipe test", code)
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

    private suspend fun TestPhone.box(predicate: (RecipeBoxData) -> Boolean): RecipeBoxData =
        withTimeout(20_000) { recipeRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    private val curry = RecipeContent(
        title = "Chicken curry",
        sourceUrl = "https://example.com/curry",
        sourceName = "Sam",
        imageUrl = "https://example.com/curry.jpg",
        servings = 4,
        servingsText = "4 servings",
        prepMinutes = 15,
        cookMinutes = 30,
        totalMinutes = 45,
        ingredients = RecipeLines.ingredients("Curry:\n1 kg chicken thighs\n2 tbsp curry paste\nRice:\n1 cup rice"),
        steps = RecipeLines.steps("Brown the chicken.\nSimmer with the paste.\nCook the rice."),
        notes = "Spicy",
        tags = listOf("Dinner", "Thai"),
        importMethod = ImportMethod.Web,
    )

    @Test
    fun savedRecipeReachesTheOtherPhoneWithEveryField() = runBlocking {
        val saved: SaveResult
        val elapsed = measureTime {
            saved = alice.recipes.save(householdId, alice.actor, curry, emptyList())
            bob.box { it.recipes.isNotEmpty() }
        }
        Log.i("RecipeFlowTest", "save to other phone: $elapsed")
        val id = (saved as SaveResult.Saved).recipeId
        val onBob: Recipe = bob.box { it.recipes.isNotEmpty() }.recipes.single()
        assertEquals(id, onBob.id)
        assertEquals(curry, onBob.content)
        assertEquals(alice.uid, onBob.createdBy)
        // recipe_saved is in the activity log (DESIGN 8.2), accepted by the rules.
        val events = bob.firestore.current.collection("households/$householdId/activity")
            .whereEqualTo("recipeId", id).get(Source.SERVER).await()
        assertEquals("recipe_saved", events.documents.single().getString("type"))
        // The same link again is a duplicate (DESIGN 5.5).
        val again = bob.recipes.save(
            householdId,
            bob.actor,
            curry.copy(sourceUrl = "https://www.example.com/curry/"),
            listOf(onBob),
        )
        assertTrue(again is SaveResult.AlreadySaved)
    }

    @Test
    fun favoriteEditDeleteAndUndo() = runBlocking {
        val id = (alice.recipes.save(householdId, alice.actor, curry, emptyList()) as SaveResult.Saved).recipeId
        val recipe = bob.box { it.recipes.isNotEmpty() }.recipes.single()

        bob.recipes.setFavorite(householdId, bob.actor, recipe, true)
        alice.box { b -> b.recipes.singleOrNull()?.favorite == true }

        alice.recipes.update(householdId, alice.actor, id, curry.copy(title = "Green curry", tags = listOf("Dinner")))
        bob.box { b ->
            b.recipes.single().let {
                it.title == "Green curry" && it.favorite &&
                    it.content.tags == listOf("Dinner")
            }
        }

        val current = alice.box { b -> b.recipes.single().title == "Green curry" }.recipes.single()
        val undo = alice.recipes.delete(householdId, alice.actor, current)
        bob.box { it.recipes.isEmpty() }
        alice.recipes.undo(householdId, alice.actor, undo)
        val restored = bob.box { it.recipes.isNotEmpty() }.recipes.single()
        assertEquals(id, restored.id)
        assertEquals("Green curry", restored.title)
        assertTrue(restored.favorite)
        assertEquals(current.createdAt, restored.createdAt)
    }

    @Test
    fun ingredientsGoToTheGroceryListWithTheRecipeSource() = runBlocking {
        val id = (alice.recipes.save(householdId, alice.actor, curry, emptyList()) as SaveResult.Saved).recipeId
        val recipe = alice.box { it.recipes.isNotEmpty() }.recipes.single()
        val list = withTimeout(10_000) { alice.groceryRepository.observeLists(householdId).first { it.isNotEmpty() } }
            .single { it.isDefault }
        val pantry = PantryData(emptyList(), emptyList(), fromCache = false)
        val chosen = RecipeGrocery.candidates(recipe, pantry, emptyList())
        assertEquals(3, alice.recipes.addToGrocery(householdId, alice.actor, list.id, recipe, chosen, pantry))
        val items = withTimeout(20_000) {
            bob.groceryRepository.observeItems(householdId, list.id).first { it.size == 3 }
        }
        assertTrue(items.all { it.source == ItemSource.Recipe && it.recipeId == id })
        // Shopping names, with the recipe line as the note and no recipe measure as the amount (DESIGN 5.5).
        assertEquals(setOf("Chicken thighs", "Curry paste", "Rice"), items.map { it.name }.toSet())
        val paste = items.single { it.name == "Curry paste" }
        assertEquals("Chicken curry: 2 tbsp curry paste", paste.note)
        assertEquals(null, paste.quantity)
    }

    @Test
    fun cookingTakesWhatTheRecipeUsedOffThePantryAndUndoPutsItBack() = runBlocking {
        val today = LocalDate.now()
        val egg = StockDraft(
            "Eggs", 6.0, QuantityUnit.Pieces, today.plusDays(10), DateType.UseBy, Location.Fridge, Category.Dairy,
            null, null, false, null,
        )
        val empty = withTimeout(20_000) { alice.pantryRepository.observe(householdId).first { !it.fromCache } }
        alice.pantry.add(householdId, alice.actor, egg, empty)
        val omelette =
            RecipeContent(title = "Omelette", ingredients = RecipeLines.ingredients("3 large eggs\nSalt to taste"))
        alice.recipes.save(householdId, alice.actor, omelette, emptyList())
        val recipe = alice.box { it.recipes.isNotEmpty() }.recipes.single()
        val pantry = withTimeout(20_000) {
            alice.pantryRepository.observe(householdId).first { d ->
                !d.fromCache &&
                    d.batches.any { it.quantity == 6.0 }
            }
        }

        val lines = Cooking.lines(recipe, pantry)
        assertEquals(listOf("Eggs"), lines.map { it.product.name })
        val (event, removeEvent) = alice.recipes.cookedEvent(recipe)
        val undo = alice.pantry.use(
            householdId,
            alice.actor,
            lines.associate { it.product.id to it.defaultAmount },
            pantry,
            listOf(event),
            listOf(removeEvent),
        )!!
        withTimeout(20_000) {
            bob.pantryRepository.observe(householdId).first { d -> d.batches.singleOrNull()?.quantity == 3.0 }
        }
        val cooked = withTimeout(20_000) {
            bob.firestore.current.collection("households/$householdId/activity")
                .whereEqualTo("type", "recipe_cooked").get().await()
        }
        assertEquals("Omelette", cooked.documents.single().get("params.name"))

        alice.pantry.undo(householdId, alice.actor, undo)
        withTimeout(20_000) {
            bob.pantryRepository.observe(householdId).first { d -> d.batches.singleOrNull()?.quantity == 6.0 }
        }
        Unit
    }
}
