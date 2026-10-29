package com.parcha.spoilnot.feature.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.parcha.spoilnot.feature.plan.domain.MealPlanData
import com.parcha.spoilnot.feature.plan.domain.MealPlanGrocery
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.recipes.domain.Cooking
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeLines
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The meal planner shared by two phones (DESIGN 5.9), against the emulators. */
@RunWith(AndroidJUnit4::class)
class PlanFlowTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone
    private lateinit var householdId: String
    private val today: LocalDate = LocalDate.now()
    private val weekStart = MealWeek.start(today, DayOfWeek.MONDAY)
    private val weekEnd = MealWeek.end(weekStart)

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
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Plan test", code)
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

    private suspend fun TestPhone.week(predicate: (MealPlanData) -> Boolean): MealPlanData =
        withTimeout(20_000) { mealPlanRepository.observe(householdId, weekStart, weekEnd).first(predicate) }

    @Test
    fun aPlannedMealWithTwoCooksReachesTheOtherPhone_changesAndRemovalFollow() = runBlocking {
        alice.recipes.save(householdId, alice.actor, RecipeContent(title = "Lasagna", servings = 6), emptyList())
        val recipe = withTimeout(15_000) {
            alice.recipeRepository.observe(householdId).first { it.recipes.isNotEmpty() }
        }.recipes.single()
        alice.mealPlan.addRecipe(
            householdId,
            alice.actor,
            today,
            MealSlot.Dinner,
            recipe,
            listOf(alice.uid, bob.uid),
            emptyList(),
        )
        alice.mealPlan.addOther(householdId, alice.actor, today, MealSlot.Lunch, "Leftovers", emptyList(), emptyList())

        val seen = bob.week { d -> !d.fromCache && d.meals.size == 2 }
        val lasagna = seen.meals.single { it.slot == MealSlot.Dinner }
        assertEquals("Lasagna" to 6, lasagna.title to lasagna.servings)
        assertEquals(setOf(alice.uid, bob.uid), lasagna.cooks.toSet())
        assertEquals(recipe.id, lasagna.recipeId)
        assertEquals(null, seen.meals.single { it.slot == MealSlot.Lunch }.recipeId)

        // Bob changes it; Alice sees the change.
        bob.mealPlan.update(
            householdId,
            bob.actor,
            lasagna,
            servings = 8,
            cooks = listOf(bob.uid),
            note = "Double batch",
        )
        val changed = alice.week { d -> d.meals.any { it.id == lasagna.id && it.servings == 8 } }
            .meals.single { it.id == lasagna.id }
        assertEquals(listOf(bob.uid) to "Double batch", changed.cooks to changed.note)

        // Alice removes it; Bob sees it go; Undo brings it back for both.
        val undo = alice.mealPlan.remove(householdId, alice.actor, changed)
        bob.week { d -> d.meals.none { it.id == lasagna.id } }
        alice.mealPlan.undo(householdId, alice.actor, undo)
        val back = bob.week { d -> d.meals.any { it.id == lasagna.id } }.meals.single { it.id == lasagna.id }
        assertEquals(8, back.servings)
    }

    @Test
    fun anotherWeekShowsOnlyItsOwnMeals() = runBlocking {
        alice.mealPlan.addOther(householdId, alice.actor, today, MealSlot.Dinner, "Tacos", emptyList(), emptyList())
        alice.mealPlan.addOther(
            householdId,
            alice.actor,
            weekStart.plusWeeks(1),
            MealSlot.Dinner,
            "Pizza",
            emptyList(),
            emptyList(),
        )
        assertEquals(listOf("Tacos"), bob.week { d -> !d.fromCache && d.meals.isNotEmpty() }.meals.map { it.title })
        val next = withTimeout(20_000) {
            bob.mealPlanRepository.observe(householdId, weekStart.plusWeeks(1), weekEnd.plusWeeks(1))
                .first { !it.fromCache && it.meals.isNotEmpty() }
        }
        assertEquals(listOf("Pizza"), next.meals.map { it.title })
    }

    @Test
    fun cookingAPlannedRecipeUpdatesThePantryForBoth_andUndoPutsItBack() = runBlocking {
        val eggs = StockDraft(
            "Eggs", 6.0, QuantityUnit.Pieces, today.plusDays(10), DateType.UseBy, Location.Fridge, Category.Dairy,
            null, null, false, null,
        )
        val empty = withTimeout(20_000) { alice.pantryRepository.observe(householdId).first { !it.fromCache } }
        alice.pantry.add(householdId, alice.actor, eggs, empty)
        val omelette = RecipeContent(title = "Omelette", servings = 1, ingredients = RecipeLines.ingredients("2 eggs"))
        alice.recipes.save(householdId, alice.actor, omelette, emptyList())
        val recipe = withTimeout(15_000) {
            alice.recipeRepository.observe(householdId).first { it.recipes.isNotEmpty() }
        }.recipes.single()
        // Planned for two: the sheet suggests 4 eggs.
        alice.mealPlan.addRecipe(householdId, alice.actor, today, MealSlot.Breakfast, recipe, emptyList(), emptyList())
        val meal = bob.week { d -> d.meals.isNotEmpty() }.meals.single()
        bob.mealPlan.update(householdId, bob.actor, meal, servings = 2, cooks = listOf(bob.uid), note = null)
        val planned = bob.week { d -> d.meals.singleOrNull()?.servings == 2 }.meals.single()
        val pantry = withTimeout(20_000) {
            bob.pantryRepository.observe(householdId).first { d -> d.batches.singleOrNull()?.quantity == 6.0 }
        }
        val line = Cooking.lines(recipe, pantry, MealPlanGrocery.factor(planned, recipe)).single()
        assertEquals(4.0, line.defaultAmount, 0.0)

        val undo = bob.mealPlan.cooked(householdId, bob.actor, planned, recipe, mapOf(line.product.id to 4.0), pantry)
        val cooked = alice.week { d -> d.meals.singleOrNull()?.cookedAt != null }.meals.single()
        assertEquals(bob.uid, cooked.cookedBy)
        withTimeout(20_000) {
            alice.pantryRepository.observe(householdId).first { d -> d.batches.singleOrNull()?.quantity == 2.0 }
        }

        bob.mealPlan.undo(householdId, bob.actor, undo)
        alice.week { d -> d.meals.singleOrNull()?.let { it.cookedAt == null && it.cookedBy == null } == true }
        withTimeout(20_000) {
            alice.pantryRepository.observe(householdId).first { d -> d.batches.singleOrNull()?.quantity == 6.0 }
        }
        Unit
    }

    @Test
    fun theWeeksIngredientsLandOnTheListWithTheMealPlanSource() = runBlocking {
        alice.recipes.save(
            householdId,
            alice.actor,
            RecipeContent(title = "Tacos", ingredients = RecipeLines.ingredients("8 tortillas\n1 lime")),
            emptyList(),
        )
        val recipe = withTimeout(15_000) {
            alice.recipeRepository.observe(householdId).first { it.recipes.isNotEmpty() }
        }.recipes.single()
        alice.mealPlan.addRecipe(householdId, alice.actor, today, MealSlot.Dinner, recipe, emptyList(), emptyList())
        val days = MealWeek.build(weekStart, alice.week { d -> d.meals.isNotEmpty() }.meals, today)
        val pantry = PantryData(emptyList(), emptyList(), fromCache = false)
        val sections = MealPlanGrocery.sections(MealPlanGrocery.toShop(days, listOf(recipe)), pantry, emptyList())
        val list = withTimeout(10_000) { alice.groceryRepository.observeLists(householdId).first { it.isNotEmpty() } }
            .single { it.isDefault }
        val count = alice.recipes.addItems(
            householdId,
            alice.actor,
            list.id,
            sections.flatMap { it.candidates },
            pantry,
            list.name,
            ItemSource.MealPlan,
            "the meal plan",
        )
        assertEquals(2, count)
        val items = withTimeout(20_000) {
            bob.groceryRepository.observeItems(householdId, list.id).first { it.size == 2 }
        }
        assertTrue(items.all { it.source == ItemSource.MealPlan && it.recipeId == recipe.id })
        assertEquals(setOf("Tortillas", "Lime"), items.map { it.name }.toSet())
    }

    @Test
    fun moveCopyLastWeekAndClearReachTheOtherPhone_withUndo() = runBlocking {
        val lastStart = weekStart.minusWeeks(1)
        alice.mealPlan.addOther(
            householdId,
            alice.actor,
            lastStart,
            MealSlot.Dinner,
            "Tacos",
            listOf(bob.uid),
            emptyList(),
        )
        alice.mealPlan.addOther(
            householdId,
            alice.actor,
            lastStart.plusDays(2),
            MealSlot.Lunch,
            "Soup",
            emptyList(),
            emptyList(),
        )
        val previous = withTimeout(20_000) {
            alice.mealPlanRepository.observe(householdId, lastStart, MealWeek.end(lastStart))
                .first { !it.fromCache && it.meals.size == 2 }
        }.meals

        // Bob copies last week into this one; Alice sees both meals on the same weekdays.
        val (count, undoCopy) = bob.mealPlan.copyWeek(
            householdId,
            bob.actor,
            previous,
            lastStart,
            weekStart,
            emptyList(),
        )!!
        assertEquals(2, count)
        val copied = alice.week { d -> d.meals.size == 2 }.meals
        assertEquals(
            setOf(weekStart to "Tacos", weekStart.plusDays(2) to "Soup"),
            copied.map {
                it.date to it.title
            }.toSet(),
        )
        assertEquals(listOf(bob.uid), copied.single { it.title == "Tacos" }.cooks)

        // Alice moves Tacos to Friday lunch; Bob sees it.
        val tacos = copied.single { it.title == "Tacos" }
        alice.mealPlan.move(householdId, alice.actor, tacos, weekStart.plusDays(4), MealSlot.Lunch, emptyList())
        bob.week { d ->
            d.meals.any { it.id == tacos.id && it.date == weekStart.plusDays(4) && it.slot == MealSlot.Lunch }
        }

        // Clear the week, then Undo: everything comes back for Bob.
        val week = bob.week { d -> d.meals.size == 2 }.meals
        val undoClear = alice.mealPlan.clear(householdId, alice.actor, week)!!
        bob.week { d -> d.meals.isEmpty() }
        alice.mealPlan.undo(householdId, alice.actor, undoClear)
        bob.week { d -> d.meals.size == 2 }

        // Undo of the copy removes the copies again.
        bob.mealPlan.undo(householdId, bob.actor, undoCopy)
        alice.week { d -> d.meals.isEmpty() }
        Unit
    }
}
