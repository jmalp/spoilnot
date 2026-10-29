package com.parcha.spoilnot.feature.plan.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.recipe
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Plan tab's week (DESIGN 5.9) in light and dark at font scale 1.0 and 2.0. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlanScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 27)
    private val start = LocalDate.of(2026, 10, 26)

    private fun meal(
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

    @Test
    fun week() {
        val meals = listOf(
            meal("m1", 26, MealSlot.Dinner, "Tacos", "r1", 4, listOf("u1")),
            // Cooked: a check and the word, not color alone.
            meal("m2", 27, MealSlot.Breakfast, "Pancakes", "r2", 2, listOf("u2"))
                .copy(cookedAt = Instant.parse("2026-10-27T08:00:00Z"), cookedBy = "u2"),
            meal("m3", 27, MealSlot.Dinner, "Weeknight chicken curry with rice", "r3", 4, listOf("u1", "u2")),
            meal("m4", 27, MealSlot.Lunch, "Leftovers", null, null, emptyList()),
            meal("m5", 28, MealSlot.Dinner, "Old soup", "gone", 3, emptyList()),
        )
        val state = PlanViewModel.UiState(
            loading = false,
            weekStart = start,
            isThisWeek = true,
            days = MealWeek.build(start, meals, today),
            members = listOf(Member("u1", "Sam", Role.Owner, null), Member("u2", "Alex", Role.Member, null)),
            recipes = listOf(recipe("r1", "Tacos"), recipe("r2", "Pancakes"), recipe("r3", "Curry")),
            myUid = "u1",
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                    Surface { PlanWeek(state, {}, {}, {}, { _, _ -> }, {}) }
                }
            }
        }
        val suffix = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/plan_week_$suffix.png")
    }
}
