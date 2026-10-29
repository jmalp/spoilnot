package com.parcha.spoilnot.feature.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.plan.domain.MealPlanData
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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
}
