package com.parcha.spoilnot.feature.plan.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.plan.domain.MealPlanGrocery
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.TEST_WORDS
import com.parcha.spoilnot.feature.recipes.domain.UseItUp
import com.parcha.spoilnot.feature.recipes.domain.recipe
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The planner's sheets and Move, in their own windows, light and dark at 1.0× and 2.0× text (after v1, review
 * CODE-07): Add meal, a meal, Move and This week's ingredients.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlanSheetsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private val members = listOf(Member("me", "Sam", Role.Owner, null), Member("u2", "Alex", Role.Member, null))
    private val pancakes =
        recipe("r1", "Pancakes", ingredients = listOf("2 cups flour", "2 eggs", "1 cup milk"), servings = 4)
    private val meal = PlannedMeal(
        id = "m1", date = TODAY, slot = MealSlot.Dinner, position = 0, recipeId = "r1", title = "Pancakes",
        servings = 4, cooks = listOf("me"), note = "Double batch",
    )
    private val week = (0L..6L).map { TODAY.plusDays(it) }

    private fun capture(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(Screenshots.screenshotName(name, dark, fontScale))
    }

    @Test
    fun addMeal() {
        val pantry = PantryData(listOf(product("milk")), listOf(batch("b1", "milk", inDays = 1)), fromCache = false)
        val useItUp = UseItUp.suggestions(listOf(pancakes), pantry, TODAY)
        compose.setThemed(dark, fontScale) {
            AddMealSheet(
                TODAY,
                MealSlot.Dinner,
                listOf(pancakes, recipe("r2", "Tomato soup")),
                useItUp,
                members,
                "me",
                onAddRecipe = { _, _ -> },
                onAddOther = { _, _ -> },
                onDismiss = {},
            )
        }
        capture("plan_add_meal")
    }

    @Test
    fun mealSheet() {
        compose.setThemed(dark, fontScale) {
            MealSheet(
                meal, members, "me", recipeExists = true, recipeServings = 4,
                onSave = { _, _, _ -> }, onCook = {}, onNotCooked = {}, weekDays = week, onMove = { _, _ -> },
                onOpenRecipe = {}, onRemove = {}, onDismiss = {},
            )
        }
        capture("plan_meal")
    }

    @Test
    fun move() {
        compose.setThemed(dark, fontScale) { MoveDialog(meal, week, onMove = { _, _ -> }, onDismiss = {}) }
        capture("plan_move")
    }

    @Test
    fun weekIngredients() {
        val pantry = PantryData(listOf(product("milk", "Milk")), listOf(batch("b1", "milk", inDays = 3)), false)
        val state = PlanViewModel.WeekGroceryState(
            lists = listOf(GroceryList("l1", "Groceries", isDefault = true), GroceryList("l2", "Costco", false)),
            listId = "l1",
            sections = MealPlanGrocery.sections(listOf(meal to pancakes), pantry, emptyList(), TEST_WORDS),
            pantry = pantry,
            loading = false,
        )
        compose.setThemed(dark, fontScale) { WeekGrocerySheet(state, onChooseList = {}, onAdd = {}, onDismiss = {}) }
        capture("plan_week_ingredients")
    }
}
