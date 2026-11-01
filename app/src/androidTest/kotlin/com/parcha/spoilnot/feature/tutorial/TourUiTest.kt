package com.parcha.spoilnot.feature.tutorial

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.HiltTestActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.core.navigation.AppShell
import com.parcha.spoilnot.core.prefs.AppPreferences
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.tutorial.domain.Tour
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app tour in the real shell (DESIGN 5.8): it follows real taps on the tabs and is remembered once skipped. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TourUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var preferences: AppPreferences

    private val session = Session.InHousehold(AuthUser("u1", "u1@example.com", "Sam", true), "Sam", "h1", Role.Member)

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Before
    fun tourNotTakenYet() {
        hilt.inject()
        EmulatorSupport.skipAppTour(done = false)
    }

    @After
    fun noTourForOtherTests() = EmulatorSupport.skipAppTour()

    @Test
    fun followsTheTabsAndSkipIsRemembered() {
        compose.setContent { SpoilNotTheme { AppShell(session) } }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(str(R.string.tour_start))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("coach_mark_next").performClick() // Start the tour
        compose.onNodeWithText(str(R.string.tour_progress, 1, Tour.countedSteps)).assertIsDisplayed()
        compose.onNodeWithTag("coach_mark_next").performClick() // Home, then "Tap Pantry"
        compose.onNodeWithText(str(R.string.tour_open_pantry)).assertIsDisplayed()

        // A real tap through the hole opens the tab, and the tour moves on with it.
        compose.onNode(hasText(str(R.string.tab_pantry)) and isSelectable()).performTouchInput { click() }
        compose.onNodeWithTag("screen_pantry").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.tour_progress, 3, Tour.countedSteps)).assertIsDisplayed()

        // Next on "Tap Plan" and "Tap Recipes" switches the tab for the user.
        compose.onNodeWithTag("coach_mark_next").performClick()
        compose.onNodeWithTag("coach_mark_next").performClick()
        compose.onNodeWithText(str(R.string.tour_open_plan)).assertIsDisplayed()
        compose.onNodeWithTag("coach_mark_next").performClick()
        compose.onNodeWithTag("screen_plan").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.tour_open_recipes)).assertIsDisplayed()
        compose.onNodeWithTag("coach_mark_next").performClick()
        compose.onNodeWithTag("screen_recipes").assertIsDisplayed()

        compose.onNodeWithTag("coach_mark_skip").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(str(R.string.tour_skip))).fetchSemanticsNodes().isEmpty()
        }
        // Skip hides the tour at once and saves the choice right after.
        val remembered = runBlocking { withTimeoutOrNull(5_000) { preferences.tourDone.first { it } } }
        assertTrue("Skip was not remembered within 5 s", remembered == true)
    }

    @Test
    fun takenFromSettingsTheHomeStepShowsHome() {
        EmulatorSupport.skipAppTour()
        compose.setContent { SpoilNotTheme { AppShell(session) } }
        // Settings opened on the Home tab, then Take the app tour.
        compose.onNodeWithContentDescription(str(R.string.open_settings)).performClick()
        compose.onNodeWithTag("row_take_tour").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(str(R.string.tour_start))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("coach_mark_next").performClick() // Start the tour

        // The Home step closes Settings, so its highlight has something to point at.
        compose.onNodeWithText(str(R.string.tour_progress, 1, Tour.countedSteps)).assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("screen_settings").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
        compose.onNodeWithTag("coach_mark_skip").performClick()
    }
}
