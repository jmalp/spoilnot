package com.parcha.spoilnot.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellTest {
    @get:Rule
    val compose = createComposeRule()

    private fun str(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    // A tab label also appears as the screen title, so match the selectable bar item.
    private fun tab(id: Int) = compose.onNode(hasText(str(id)) and isSelectable())

    @Test
    fun showsFourTabsAndStartsOnHome() {
        compose.setContent { SpoilNotTheme { AppShell() } }

        listOf(R.string.tab_home, R.string.tab_pantry, R.string.tab_recipes, R.string.tab_groceries).forEach {
            tab(it).assertIsDisplayed()
        }
        tab(R.string.tab_home).assertIsSelected()
        compose.onNodeWithTag("screen_home").assertIsDisplayed()
    }

    @Test
    fun switchingTabsShowsEachScreen() {
        compose.setContent { SpoilNotTheme { AppShell() } }

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
        compose.setContent { SpoilNotTheme { AppShell() } }

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
