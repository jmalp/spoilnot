package com.parcha.spoilnot.a11y

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
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
import com.parcha.spoilnot.feature.recipes.domain.ImportMethod
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeLines
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
 * The accessibility pass (DESIGN 6.8, M6): Google's Accessibility Test Framework checks every main screen with real
 * data for touch targets under 48 dp, missing labels, duplicate descriptions and low text contrast. TalkBack itself
 * is covered by the manual QA script.
 */
@OptIn(ExperimentalTestApi::class)
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

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun str(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

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
        Unit
    }

    @After
    fun tearDown() {
        scenario?.close()
        FirebaseAuth.getInstance().signOut()
    }

    private fun awaitTag(tag: String): SemanticsNodeInteraction {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), 20_000)
        return compose.onNodeWithTag(tag)
    }

    private fun tab(label: Int) = compose.onNode(hasText(str(label)) and isSelectable())

    private fun check() {
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun mainScreensPassAccessibilityChecks() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.enableAccessibilityChecks()

        awaitTag("card_home_attention")
        check()

        tab(R.string.tab_pantry).performClick()
        awaitTag("pantry_search")
        check()

        tab(R.string.tab_recipes).performClick()
        awaitTag("recipe_card").performClick()
        awaitTag("recipe_title")
        check()

        tab(R.string.tab_groceries).performClick()
        compose.waitUntilAtLeastOneExists(hasText("flour"), 20_000)
        check()

        compose.onNode(hasContentDescription(str(R.string.open_settings))).performClick()
        awaitTag("screen_settings")
        check()
    }
}
