package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.feature.recipes.domain.Ingredient
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.Step
import com.parcha.spoilnot.testing.setThemed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The recipe screens' new look (DESIGN 19.12): cook mode's top and buttons, and a recipe's numbered steps. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecipeLookTest {
    @get:Rule
    val compose = createComposeRule()

    private val recipe = Recipe(
        "r",
        RecipeContent(
            title = "Test pancakes",
            servings = 4,
            ingredients = listOf(Ingredient("2 cups flour")),
            steps = listOf(Step("Whisk."), Step("Cook.")),
        ),
    )

    @Test
    fun cookModeHasBackTheNameAndTheStepAsAChip_andItsButtonsFollowThePage() {
        var backs = 0
        compose.setThemed(dark = false, fontScale = 1f) {
            CookSteps(recipe, servings = 4, Modifier.height(800.dp), onBack = { backs++ })
        }
        compose.onNode(isHeading() and hasText("Test pancakes")).assertExists()
        compose.onNodeWithText("Step 1 of 2").assertExists()
        // The screen's Back, and the step's Back named for what it does (review A11Y-08).
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
        compose.onNode(hasContentDescription("Back to the previous step")).assertIsNotEnabled()
        compose.onNodeWithTag("cook_next").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Step 2 of 2").assertExists()
        compose.onNode(hasContentDescription("Back to the previous step")).assertIsEnabled()
        compose.onNodeWithTag("cook_next").assertIsNotEnabled()
        compose.onNodeWithText("Ingredients").assertHasClickAction()
    }

    @Test
    fun aRecipesStepsAreReadWithTheirNumbers() {
        compose.setThemed(dark = false, fontScale = 1f) {
            RecipeBody(recipe, servings = 4, onServings = {}, onAddToList = {}, onOpenOriginal = {})
        }
        compose.onNode(isHeading() and hasText("Test pancakes")).assertExists()
        // The number in its leaf and the step are one stop: "1, Whisk."
        compose.onNode(hasText("1") and hasText("Whisk.")).assertExists()
        compose.onNodeWithTag("recipe_servings").assertExists()
    }
}
