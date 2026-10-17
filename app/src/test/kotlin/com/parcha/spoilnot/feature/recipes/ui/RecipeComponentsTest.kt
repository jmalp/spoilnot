package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the card shows the total time and toggles the favorite star`() {
        var toggles = 0
        compose.setContent {
            SpoilNotTheme(dynamicColor = false) {
                RecipeCard(Recipe("a", RecipeContent(title = "Chili", totalMinutes = 75)), {}, { toggles++ })
            }
        }
        compose.onNodeWithText("Chili").assertExists()
        compose.onNodeWithContentDescription("Total 1 hr 15 min").assertExists()
        compose.onNodeWithContentDescription("Add to favorites").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `checklist lines tick off and show group headings`() {
        val checked = mutableStateListOf<Int>()
        compose.setContent {
            SpoilNotTheme(dynamicColor = false) {
                IngredientChecklist(listOf("Dough" to "2 cups flour", null to "1 egg"), checked)
            }
        }
        compose.onNodeWithText("Dough").assertExists()
        compose.onNodeWithText("2 cups flour").assertIsOff().performClick()
        assertEquals(listOf(0), checked.toList())
        compose.onNodeWithText("2 cups flour").assertIsOn()
    }
}
