package com.parcha.spoilnot.a11y

import androidx.compose.material3.R as Material3R
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.grocery.domain.GroceryService
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import com.parcha.spoilnot.feature.plan.domain.MealPlanService
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.recipes.domain.ImportMethod
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeLines
import com.parcha.spoilnot.feature.recipes.domain.RecipeRepository
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The accessibility pass (DESIGN 6.8): Google's Accessibility Test Framework checks the 13 main screens with real
 * data, and the sheets, menus and dialogs they open, for touch targets under 48 dp, missing labels, duplicate
 * descriptions and low text contrast. The app's own rule adds what ATF misses: every control has a name
 * ([assertEveryControlHasAName]). The floating buttons are found by their names, as TalkBack, Voice Access and the
 * benchmark journey find them. TalkBack itself is covered by the manual QA script.
 *
 * Every stop is checked and the test fails at the end with every problem it saw; a screen it can't reach ends the walk
 * there, and the failure lists it with the problems found before it.
 * `enableAccessibilityChecks()` needs Android 14 (API 34), so older phones skip this class (review 11, CODE-12).
 */
@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 34)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var session: SessionManager

    @Inject lateinit var households: HouseholdRepository

    @Inject lateinit var pantry: PantryService

    @Inject lateinit var pantryRepository: PantryRepository

    @Inject lateinit var grocery: GroceryService

    @Inject lateinit var groceryRepository: GroceryRepository

    @Inject lateinit var recipes: RecipeService

    @Inject lateinit var recipeRepository: RecipeRepository

    @Inject lateinit var mealPlan: MealPlanService

    private var scenario: ActivityScenario<MainActivity>? = null
    private val problems = mutableListOf<String>()

    private fun resources() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun str(id: Int) = resources().getString(id)

    @Before
    fun householdWithData() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        hilt.inject()
        FirebaseAuth.getInstance().signOut()
        session.signUp("Ari", uniqueEmail("ari"), PASSWORD)
        withTimeout(15_000) { session.session.first { it is Session.NoHousehold } }
        val uid = checkNotNull(FirebaseAuth.getInstance().currentUser).uid
        val hid = households.createHousehold(uid, "Ari", "A11y home", InviteCodes.generate())
        withTimeout(15_000) { session.session.first { it is Session.InHousehold } }
        val actor = Actor(uid, "Ari")
        val today = LocalDate.now()
        listOf("Milk" to 1L, "Yogurt" to -1L, "Rice" to 200L).forEach { (name, days) ->
            val data = withTimeout(10_000) { pantryRepository.observe(hid).first() }
            pantry.add(
                hid,
                actor,
                StockDraft(
                    name, 2.0, QuantityUnit.Pieces, today.plusDays(days), DateType.UseBy, Location.Fridge,
                    Category.Dairy, null, if (name == "Rice") 5.0 else null, name == "Rice", null,
                ),
                data,
            )
        }
        val list = withTimeout(10_000) { groceryRepository.observeLists(hid).first { it.isNotEmpty() } }.first()
        val data = withTimeout(10_000) { pantryRepository.observe(hid).first() }
        grocery.quickAdd(hid, actor, list.id, "2 kg flour", emptyList(), data, listName = list.name)
        recipes.save(
            hid,
            actor,
            RecipeContent(
                title = "Pancakes",
                servings = 2,
                totalMinutes = 20,
                ingredients = RecipeLines.ingredients("1 cup flour\n2 eggs"),
                steps = RecipeLines.steps("Whisk.\nFry."),
                importMethod = ImportMethod.Manual,
            ),
            emptyList(),
        )
        // A planned recipe with ingredients, so Plan offers the week's ingredients.
        val pancakes = withTimeout(15_000) { recipeRepository.observe(hid).first { it.recipes.isNotEmpty() } }.recipes
        mealPlan.addRecipe(hid, actor, today, MealSlot.Dinner, pancakes.single(), listOf(uid), emptyList())
        Unit
    }

    @After
    fun tearDown() {
        scenario?.close()
        FirebaseAuth.getInstance().signOut()
    }

    private fun tagPrefix(prefix: String) = tagMatching(Regex("${Regex.escape(prefix)}.*"))

    private fun tagMatching(pattern: Regex) = SemanticsMatcher("test tag matches $pattern") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.matches(pattern) == true
    }

    private fun await(matcher: SemanticsMatcher) = compose.waitUntilAtLeastOneExists(matcher, 20_000)

    private fun awaitTag(tag: String): SemanticsNodeInteraction {
        await(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    /** A floating button, found by its name: what A11Y-01 broke and B22's journey relies on. */
    private fun fab(text: String): SemanticsNodeInteraction {
        val matcher = hasText(text) and hasClickAction()
        await(matcher)
        return compose.onNode(matcher)
    }

    private fun tab(label: Int) = compose.onNode(hasText(str(label)) and isSelectable()).performClick()

    private fun windows() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size

    /** Waits for a sheet, menu or dialog to open its own window. */
    private fun awaitWindow() = compose.waitUntil(20_000) { windows() >= 2 }

    private fun back() {
        Espresso.pressBackUnconditionally()
        compose.waitForIdle()
    }

    /**
     * Back until only the screen's own window is left (sheets animate out). A sheet that opened the keyboard (Add
     * item focuses Name, after v1) takes a second Back: the first one closes the keyboard.
     */
    private fun closeWindows() {
        repeat(3) {
            back()
            val closed = runCatching { compose.waitUntil(5_000) { windows() == 1 } }.isSuccess
            if (closed) return
        }
        compose.waitUntil(20_000) { windows() == 1 }
    }

    /** Runs ATF and the name rule on every window open now, and keeps what fails. */
    private fun check(stop: String) {
        compose.waitForIdle()
        runCatching { compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks() }
            .onFailure { problems += "$stop (ATF): ${it.message}" }
        runCatching { compose.assertEveryControlHasAName() }
            .onFailure { problems += "$stop (names): ${it.message}" }
    }

    @Test
    fun mainScreensAndTheirSheetsPassAccessibilityChecks() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.enableAccessibilityChecks(
            spoilNotAccessibilityValidator(str(Material3R.string.m3c_bottom_sheet_drag_handle_description)),
        )
        val stopped = runCatching { walk() }.exceptionOrNull()
        val report = buildList {
            stopped?.let { add("Stopped before the end: $it") }
            addAll(problems)
        }
        if (report.isNotEmpty()) {
            throw AssertionError(
                "Accessibility problems:\n" + report.joinToString("\n\n"),
                stopped,
            )
        }
    }

    private fun walk() {
        awaitTag("card_home_attention")
        check("Home")

        tab(R.string.tab_pantry)
        awaitTag("pantry_search")
        check("Pantry")
        fab(str(R.string.action_add_item)).performClick()
        awaitTag("sheet_add_item")
        check("Add item sheet")
        closeWindows()
        compose.onAllNodes(tagPrefix("pantry_item_"))[0].performClick()
        awaitTag("sheet_edit_item")
        check("Edit item sheet")
        closeWindows()
        compose.onAllNodes(tagPrefix("menu_"))[0].performClick()
        awaitWindow()
        check("Pantry row menu")
        compose.onNode(hasText(str(R.string.action_mark_opened))).performClick()
        awaitTag("sheet_opened")
        check("Mark opened sheet")
        closeWindows()
        compose.onAllNodes(tagPrefix("menu_"))[0].performClick()
        awaitWindow()
        compose.onNode(hasText(str(R.string.action_throw_away))).performClick()
        await(hasText(str(R.string.reason_spoiled))) // the dialog, not the menu still closing
        check("Throw away dialog")
        closeWindows()

        tab(R.string.tab_plan)
        awaitTag("screen_plan")
        check("Plan")
        // Plan opens scrolled to today, so the week's first day can be laid out above the visible list, where a tap
        // lands on nothing (every day but the first day of the week). Use today's button, scrolled into view.
        compose.onNodeWithTag("plan_add_${LocalDate.now()}_${MealSlot.Breakfast.wire}")
            .performScrollTo()
            .performClick()
        awaitTag("sheet_add_meal")
        check("Add meal sheet")
        closeWindows()
        compose.onAllNodes(tagPrefix("plan_meal_"))[0].performScrollTo().performClick()
        awaitTag("sheet_meal")
        check("Meal sheet")
        closeWindows()
        awaitTag("plan_shop").performClick()
        awaitTag("sheet_week_grocery")
        check("This week's ingredients sheet")
        closeWindows()

        tab(R.string.tab_recipes)
        awaitTag("recipe_card")
        check("Recipes")
        fab(str(R.string.recipe_add)).performClick()
        awaitTag("add_recipe_manual")
        check("Add recipe sheet")
        closeWindows()
        awaitTag("recipe_card").performClick()
        awaitTag("recipe_title")
        check("Recipe detail")
        fab(str(R.string.recipe_cook)).performClick()
        awaitTag("screen_cook_mode")
        check("Cook mode")
        back()
        awaitTag("recipe_cooked").performClick()
        awaitTag("sheet_cooked")
        check("I cooked this sheet")
        closeWindows()
        awaitTag("recipe_add_to_list").performClick()
        awaitTag("recipe_add_items")
        check("Add to grocery list sheet")
        closeWindows()
        awaitTag("recipe_edit").performClick()
        awaitTag("screen_recipe_edit")
        check("Recipe edit")
        back()
        awaitTag("recipe_title")
        back()

        tab(R.string.tab_groceries)
        await(tagPrefix("grocery_item_"))
        check("Groceries")
        compose.onAllNodes(tagPrefix("grocery_item_"))[0].performClick()
        fab(resources().getQuantityString(R.plurals.grocery_add_to_pantry, 1, 1)).performClick()
        awaitTag("sheet_add_to_pantry")
        check("Add to pantry sheet")
        closeWindows()

        compose.onNode(hasContentDescription(str(R.string.open_settings))).performClick()
        awaitTag("screen_settings")
        check("Settings")
        awaitTag("row_household").performScrollTo().performClick()
        awaitTag("screen_household")
        check("Household")
        back()
        awaitTag("row_notifications").performScrollTo().performClick()
        awaitTag("screen_notifications")
        check("Notifications")
        back()
        awaitTag("row_about").performScrollTo().performClick()
        awaitTag("screen_about")
        check("About")
        back()
        awaitTag("screen_settings")
        back()

        tab(R.string.tab_home)
        await(hasText(str(R.string.insights_see)))
        compose.onNode(hasText(str(R.string.insights_see))).performScrollTo().performClick()
        awaitTag("screen_insights")
        check("Insights")
    }
}
