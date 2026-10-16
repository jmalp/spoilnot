package com.parcha.spoilnot.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parcha.spoilnot.HiltTestActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppShellTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    private val session = Session.InHousehold(AuthUser("u1", "u1@example.com", "Sam", true), "Sam", "h1", Role.Member)

    private fun str(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    // A tab label also appears as the screen title, so match the selectable bar item.
    private fun tab(id: Int) = compose.onNode(hasText(str(id)) and isSelectable())

    @Test
    fun showsFourTabsAndStartsOnHome() {
        compose.setContent { SpoilNotTheme { AppShell(session) } }

        listOf(R.string.tab_home, R.string.tab_pantry, R.string.tab_recipes, R.string.tab_groceries).forEach {
            tab(it).assertIsDisplayed()
        }
        tab(R.string.tab_home).assertIsSelected()
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
    }

    @Test
    fun switchingTabsShowsEachScreen() {
        compose.setContent { SpoilNotTheme { AppShell(session) } }

        mapOf(
            R.string.tab_pantry to "screen_pantry",
            R.string.tab_recipes to "screen_recipes",
            R.string.tab_groceries to "screen_groceries",
            R.string.tab_home to "screen_home",
        ).forEach { (label, screen) ->
            tab(label).performClick()
            compose.onNodeWithTag(screen).assertIsDisplayed()
        }
    }

    @Test
    fun eachTabKeepsItsBackStack() {
        compose.setContent { SpoilNotTheme { AppShell(session) } }

        tab(R.string.tab_pantry).performClick()
        compose.onNodeWithContentDescription(str(R.string.open_settings)).performClick()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()

        tab(R.string.tab_groceries).performClick()
        compose.onNodeWithTag("screen_groceries").assertIsDisplayed()

        tab(R.string.tab_pantry).performClick()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()

        compose.onNodeWithContentDescription(str(R.string.back)).performClick()
        compose.onNodeWithTag("screen_pantry").assertIsDisplayed()
    }
}
