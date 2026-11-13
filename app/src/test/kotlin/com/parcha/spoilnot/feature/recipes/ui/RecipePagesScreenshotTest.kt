package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.recipes.domain.CookLine
import com.parcha.spoilnot.feature.recipes.domain.Ingredient
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeForm
import com.parcha.spoilnot.feature.recipes.domain.Step
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Recipe edit, cook mode and I cooked this, light and dark at 1.0× and 2.0× text (after v1, review CODE-07). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecipePagesScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private fun name(name: String) = Screenshots.screenshotName(name, dark, fontScale)

    @Test
    fun recipeEdit() {
        val form = RecipeForm(
            title = "Test pancakes",
            servings = "4",
            totalMinutes = "25",
            ingredients = "2 cups flour\n2 eggs\n1 cup milk",
            steps = "Mix.\nCook.",
            tags = "Breakfast",
        )
        compose.setThemed(dark, fontScale) {
            RecipeFormFields(form, problems = emptySet(), saving = false, onChange = {}, onSave = {})
        }
        compose.onRoot().captureRoboImage(name("recipe_edit"))
    }

    /** The step, the Back, Ingredients and Next buttons, and the progress bar named "Step 1 of 2" (A11Y-08). */
    @Test
    fun cookMode() {
        val recipe = Recipe(
            "r",
            RecipeContent(
                title = "Test pancakes",
                servings = 4,
                ingredients = listOf(Ingredient("2 cups flour")),
                steps = listOf(Step("Whisk the flour, eggs and milk until smooth."), Step("Cook.")),
            ),
        )
        compose.setThemed(dark, fontScale) { CookSteps(recipe, servings = 4, Modifier.height(700.dp)) }
        compose.onRoot().captureRoboImage(name("recipe_cook_mode"))
    }

    /** Each amount is named for its ingredient; from 1.5× text it sits under the ingredient (A11Y-07). */
    @Test
    fun cooked() {
        val lines = listOf(
            CookLine(product("flour", "Flour", unit = QuantityUnit.Kilograms), listOf("2 cups flour"), 1.0, 0.25),
            CookLine(product("egg", "Eggs"), listOf("2 eggs"), 6.0, 2.0),
        )
        compose.setThemed(dark, fontScale) { CookedSheet(lines, onUse = {}, onDismiss = {}) }
        compose.waitForIdle()
        captureScreenRoboImage(name("recipe_cooked"))
    }
}
