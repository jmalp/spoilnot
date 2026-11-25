package com.parcha.spoilnot.feature.plan.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.navigation.ShellLayout
import com.parcha.spoilnot.core.navigation.TabBar
import com.parcha.spoilnot.core.navigation.TopLevelDestination
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.recipe
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Plan tab in the 2026-11 look (DESIGN 5.9, 19.7, 19.12): "This week" above the title, the week switcher and the
 * row of days, today as the highlight card and the other days as groups of rows. Light and dark, text at 1.0 and 2.0.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlanScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 27)
    private val start = LocalDate.of(2026, 10, 26)

    private val handlers = PlanHandlers({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})

    private fun tab(state: PlanViewModel.UiState) {
        compose.setThemed(dark, fontScale) {
            ShellLayout(bar = { TabBar(TopLevelDestination.Plan, it) {} }) {
                PlanTab(
                    state = state,
                    snackbar = remember { SnackbarHostState() },
                    onOpenSettings = {},
                    handlers = handlers,
                )
            }
        }
    }

    @Test
    fun week() {
        val meals = listOf(
            planned("m1", 26, MealSlot.Dinner, "Tacos", "r1", 4, listOf("u1")),
            // Cooked: a check and the word, not color alone.
            planned("m2", 27, MealSlot.Breakfast, "Pancakes", "r2", 2, listOf("u2"))
                .copy(cookedAt = Instant.parse("2026-10-27T08:00:00Z"), cookedBy = "u2"),
            planned("m3", 27, MealSlot.Dinner, "Weeknight chicken curry with rice", "r3", 4, listOf("u1", "u2")),
            planned("m4", 27, MealSlot.Lunch, "Leftovers", null, null, emptyList()),
            planned("m5", 28, MealSlot.Dinner, "Old soup", "gone", 3, emptyList()),
        )
        tab(weekState(meals))
        compose.onNodeWithText("This week").assertIsDisplayed()
        compose.onNodeWithTag("plan_day_2026-10-27").assertIsDisplayed()
        compose.onRoot().captureRoboImage(Screenshots.screenshotName("plan_week", dark, fontScale))
    }

    /** An empty week says how to fill it, with Copy last week (after v1, UX-15). */
    @Test
    fun emptyWeek() {
        tab(weekState(emptyList()))
        compose.onNodeWithTag("plan_week_empty").assertIsDisplayed()
        compose.onNodeWithTag("plan_empty_copy").assertIsDisplayed()
        compose.onRoot().captureRoboImage(Screenshots.screenshotName("plan_empty_week", dark, fontScale))
    }

    private fun weekState(meals: List<PlannedMeal>) = PlanViewModel.UiState(
        loading = false,
        weekStart = start,
        isThisWeek = true,
        days = MealWeek.build(start, meals, today),
        members = listOf(Member("u1", "Sam", Role.Owner, null), Member("u2", "Alex", Role.Member, null)),
        recipes = listOf(recipe("r1", "Tacos"), recipe("r2", "Pancakes"), recipe("r3", "Curry")),
        myUid = "u1",
        canShop = meals.isNotEmpty(),
    )
}

/** A planned meal on a day of October 2026. */
internal fun planned(
    id: String,
    day: Int,
    slot: MealSlot,
    title: String,
    recipeId: String?,
    servings: Int?,
    cooks: List<String>,
) = PlannedMeal(
    id = id, date = LocalDate.of(2026, 10, day), slot = slot, position = 0, recipeId = recipeId,
    title = title, servings = servings, cooks = cooks, note = null,
)
