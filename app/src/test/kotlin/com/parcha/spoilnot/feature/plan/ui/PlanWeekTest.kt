package com.parcha.spoilnot.feature.plan.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.recipes.domain.recipe
import com.parcha.spoilnot.testing.setThemed
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Plan tab's week (DESIGN 5.9, 19.12): the row of days, the header's line, and how rows read. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlanWeekTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 27)
    private val start = LocalDate.of(2026, 10, 26)
    private val handlers = PlanHandlers({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})

    private val meals = listOf(
        planned("m1", 27, MealSlot.Dinner, "Curry", "r1", 4, listOf("u1", "u2")),
        planned("m2", 27, MealSlot.Dinner, "Rice", null, null, emptyList()),
        planned("m3", 31, MealSlot.Lunch, "Soup", null, 2, listOf("u2")),
    )

    private fun state(isThisWeek: Boolean = true) = PlanViewModel.UiState(
        loading = false,
        weekStart = start,
        isThisWeek = isThisWeek,
        days = MealWeek.build(start, meals, today),
        members = listOf(Member("u1", "Sam", Role.Owner, null), Member("u2", "Alex", Role.Member, null)),
        recipes = listOf(recipe("r1", "Curry")),
        myUid = "u1",
    )

    private fun show(state: PlanViewModel.UiState, fontScale: Float = 1f) {
        compose.setThemed(dark = false, fontScale = fontScale) {
            PlanTab(state, remember { SnackbarHostState() }, onOpenSettings = {}, handlers = handlers)
        }
    }

    @Test
    fun eachDayButtonIsNamedInFull_todaySaysSo() {
        show(state())
        compose.onNodeWithTag("plan_day_button_2026-10-27")
            .assertContentDescriptionEquals("Tuesday, Oct 27, today")
            .assertHasClickAction()
        compose.onNodeWithTag("plan_day_button_2026-10-28").assertContentDescriptionEquals("Wednesday, Oct 28")
        // What a tap does, for TalkBack: "double tap to show this day".
        val label = compose.onNodeWithTag("plan_day_button_2026-10-28").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].label
        assertEquals("Show this day", label)
    }

    @Test
    fun aDayButtonScrollsToThatDay() {
        show(state())
        compose.onNodeWithTag("plan_day_button_2026-10-29").performClick()
        compose.waitForIdle()
        // Thursday is at the top of the list, under its 12 dp of padding.
        val list = compose.onNodeWithTag("plan_days").getUnclippedBoundsInRoot()
        val day = compose.onNodeWithTag("plan_day_2026-10-29").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals((list.top + 12.dp).value, day.top.value, 1f)
        // The last day can't reach the top; the list scrolls to its end, where it shows.
        compose.onNodeWithTag("plan_day_button_2026-11-01").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("plan_day_2026-11-01").assertIsDisplayed()
    }

    @Test
    fun atNormalTextTheRowFitsTheWidth() {
        show(state())
        compose.onNode(hasTestTag("plan_day_row") and hasScrollAction()).assertDoesNotExist()
    }

    @Test
    fun atLargeTextTheRowScrollsSideways() {
        show(state(), fontScale = 2f)
        compose.onNode(hasTestTag("plan_day_row") and hasScrollAction()).assertExists()
    }

    @Test
    fun thisWeekIsSaidAboveTheTitle_otherWeeksOfferThisWeek() {
        show(state())
        compose.onNodeWithText("This week").assertIsDisplayed()
        compose.onNodeWithTag("plan_this_week").assertDoesNotExist()
    }

    @Test
    fun anotherWeekHasNoLineAboveTheTitle_andGoesBackToThisWeek() {
        show(state(isThisWeek = false))
        compose.onNodeWithText("This week").assertDoesNotExist()
        compose.onNodeWithTag("plan_this_week").assertIsDisplayed()
    }

    @Test
    fun aMealIsOneSentence_andEachSlotHasOnePlus() {
        show(state())
        compose.onNodeWithTag("plan_meal_m1")
            .assertContentDescriptionEquals("Dinner: Curry, 4 servings, cooking: You, Alex")
            .assertHasClickAction()
        // Two dinners, one + for the slot, its own stop.
        assertEquals(1, compose.onAllNodesWithTag("plan_add_2026-10-27_dinner").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Add to Dinner, Tuesday, Oct 27").assertHasClickAction()
        // Today's title and the other days' are headings, so the rotor jumps between days.
        compose.onNode(isHeading() and hasText("Tuesday, Oct 27")).assertExists()
        compose.onNode(isHeading() and hasText("Wednesday, Oct 28")).assertExists()
    }
}
