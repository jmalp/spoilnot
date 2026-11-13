package com.parcha.spoilnot.feature.recipes

import android.content.Intent
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.plan.domain.MealPlanService
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real app against the emulators, with fixed pages instead of the network (FakeRecipeImportModule). */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecipeUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var session: SessionManager

    @Inject lateinit var households: HouseholdRepository

    @Inject lateinit var mealPlan: MealPlanService

    private var scenario: ActivityScenario<MainActivity>? = null

    private var householdId = ""

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    @Before
    fun signedInWithAHousehold() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        hilt.inject()
        FirebaseAuth.getInstance().signOut()
        session.signUp("Robin", uniqueEmail("robin"), PASSWORD)
        withTimeout(15_000) { session.session.first { it is Session.NoHousehold } }
        val uid = checkNotNull(FirebaseAuth.getInstance().currentUser).uid
        households.createHousehold(uid, "Robin", "Recipe UI", InviteCodes.generate())
        householdId =
            (withTimeout(15_000) { session.session.first { it is Session.InHousehold } } as Session.InHousehold)
                .householdId
        Unit
    }

    @After
    fun tearDown() {
        scenario?.close()
        FirebaseAuth.getInstance().signOut()
    }

    private fun awaitTag(tag: String, timeoutMs: Long = 20_000): SemanticsNodeInteraction {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)
        return compose.onNodeWithTag(tag)
    }

    private fun share(text: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)

    @Test
    fun sharedLinkOpensAPreviewThenSavesAndDetectsTheDuplicate() {
        val start = System.nanoTime()
        scenario = ActivityScenario.launch(share("Try these: ${FakeRecipeImportModule.PANCAKES_URL}?utm_source=share"))
        val launched = (System.nanoTime() - start) / 1_000_000
        compose.waitUntilAtLeastOneExists(hasTestTag("preview_title") and hasText("Test pancakes"), 5_000)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        Log.i("RecipeUiTest", "share to preview: launch $launched ms, preview $elapsed ms")
        // The app's own work (resumed to preview) has the 5 s budget; the activity start depends on the device
        // and the host (an emulator on a busy computer took up to 31 s to start it), so the whole share only has to
        // show the preview within a minute.
        assertTrue("preview ${elapsed - launched} ms after launch", elapsed - launched < 5_000)
        assertTrue("share to preview $elapsed ms", elapsed < 60_000)

        awaitTag("preview_save").performClick()
        awaitTag("screen_recipe_detail")
        compose.waitUntilAtLeastOneExists(hasTestTag("recipe_title") and hasText("Test pancakes"), 10_000)

        // Sharing the same page again offers Open or Save a copy (DESIGN 5.5).
        scenario?.onActivity { it.startActivity(share(FakeRecipeImportModule.PANCAKES_URL)) }
        awaitTag("preview_save").performClick()
        awaitTag("already_saved_open").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("recipe_title") and hasText("Test pancakes"), 10_000)
    }

    @Test
    fun createManuallyScaleServingsAndCook() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitTag("screen_home")
        compose.onNode(hasText(str(R.string.tab_recipes)) and isSelectable()).performClick()
        awaitTag("fab_add_recipe").performClick()
        awaitTag("add_recipe_manual").performClick()

        awaitTag("recipe_field_title").performTextInput("Test flatbread")
        compose.onNodeWithTag("recipe_field_servings").performTextInput("2")
        compose.onNodeWithTag(
            "recipe_field_ingredients",
        ).performScrollTo().performTextInput("Dough:\n2 cups flour\n1 tsp salt")
        compose.onNodeWithTag("recipe_field_steps").performScrollTo().performTextInput("Mix.\nRest.\nBake.")
        compose.onNodeWithTag("recipe_save").performScrollTo().performClick()

        awaitTag("screen_recipe_detail")
        compose.waitUntilAtLeastOneExists(hasText("2 cups flour"), 10_000)
        compose.onNodeWithText("Dough").assertExists()
        compose.onNodeWithTag(
            "recipe_servings",
        ).assertTextEquals(context.resources.getQuantityString(R.plurals.recipe_servings, 2, 2))
        compose.onNode(hasContentDescription(str(R.string.recipe_more_servings))).performScrollTo().performClick()
        compose.onNodeWithTag(
            "recipe_servings",
        ).assertTextEquals(context.resources.getQuantityString(R.plurals.recipe_servings, 3, 3))
        compose.waitUntilAtLeastOneExists(hasText("3 cups flour"), 5_000)

        awaitTag("recipe_cook").performClick()
        awaitTag("screen_cook_mode")
        compose.onNodeWithText(str(R.string.recipe_step_of, 1, 3)).assertExists()
        awaitTag("cook_next").performClick()
        compose.waitUntilAtLeastOneExists(hasText(str(R.string.recipe_step_of, 2, 3)), 5_000)
    }

    /** One rule for confirmations (after v1, R13, DESIGN 6.1): Delete recipe deletes at once and offers Undo. */
    @Test
    fun deleteRecipeHasUndoAndNoDialog() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitTag("screen_home")
        compose.onNode(hasText(str(R.string.tab_recipes)) and isSelectable()).performClick()
        awaitTag("fab_add_recipe").performClick()
        awaitTag("add_recipe_manual").performClick()
        awaitTag("recipe_field_title").performTextInput("Test soup")
        compose.onNodeWithTag("recipe_field_steps").performScrollTo().performTextInput("Heat.")
        compose.onNodeWithTag("recipe_save").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("recipe_title") and hasText("Test soup"), 10_000)

        awaitTag("recipe_more").performClick()
        awaitTag("recipe_delete").performClick()
        // No "Delete recipe?" dialog: the box opens with the snackbar.
        awaitTag("screen_recipes")
        compose.onNodeWithText(str(R.string.recipe_delete_title)).assertDoesNotExist()
        compose.waitUntilAtLeastOneExists(hasText(str(R.string.recipe_deleted, "Test soup")), 10_000)
        compose.onNodeWithText(str(R.string.action_undo)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Test soup"), 10_000)
    }

    /** Clear this week clears at once and offers Undo (after v1, R13); it asked first before. */
    @Test
    fun clearThisWeekHasUndoAndNoDialog() {
        val uid = checkNotNull(FirebaseAuth.getInstance().currentUser).uid
        runBlocking {
            mealPlan.addOther(
                householdId,
                Actor(uid, "Robin"),
                LocalDate.now(),
                MealSlot.Dinner,
                "Leftovers",
                emptyList(),
                emptyList(),
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitTag("screen_home")
        compose.onNode(hasText(str(R.string.tab_plan)) and isSelectable()).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Leftovers"), 20_000)
        awaitTag("plan_menu").performClick()
        awaitTag("plan_clear_week").performClick()
        compose.onNodeWithText(str(R.string.plan_clear_title)).assertDoesNotExist()
        compose.waitUntilAtLeastOneExists(hasText(str(R.string.plan_cleared)), 10_000)
        compose.waitUntilDoesNotExist(hasText("Leftovers"), 10_000)
        compose.onNodeWithText(str(R.string.action_undo)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Leftovers"), 10_000)
    }
}
