package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeGrocery
import com.parcha.spoilnot.feature.recipes.domain.recipe
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
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
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
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                IngredientChecklist(listOf("Dough" to "2 cups flour", null to "1 egg"), checked)
            }
        }
        compose.onNodeWithText("Dough").assertExists()
        compose.onNodeWithText("2 cups flour").assertIsOff().performClick()
        assertEquals(listOf(0), checked.toList())
        compose.onNodeWithText("2 cups flour").assertIsOn()
    }

    @Test
    fun `Change offers pantry products and saves the chosen name`() {
        val candidate = RecipeGrocery.candidates(
            recipe("r1", "Stir fry", ingredients = listOf("3 scallions, sliced")),
            PantryData(emptyList(), emptyList(), false),
            emptyList(),
        ).single()
        var edits = 0
        var saved: String? = null
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                CandidateRow(candidate, selected = false, onChange = {}, onEdit = { edits++ })
                RenameDialog(
                    candidate = candidate,
                    products = listOf(product("green", "Green onions"), product("onion", "Onion")),
                    onSave = { saved = it },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Adds as Scallions × 3").assertExists()
        compose.onNodeWithContentDescription("Change what 3 scallions, sliced is added as").performClick()
        assertEquals(1, edits)
        compose.onNodeWithTag("rename_field").performTextReplacement("onion")
        compose.onNodeWithText("Green onions").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals("Green onions", saved)
    }
}
