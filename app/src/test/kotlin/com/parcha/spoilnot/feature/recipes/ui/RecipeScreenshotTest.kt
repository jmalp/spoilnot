package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.recipes.domain.GroceryCandidate
import com.parcha.spoilnot.feature.recipes.domain.IngredientParser
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Recipe components in light and dark at font scale 1.0 and 2.0 (DESIGN 13.1). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecipeScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    private val suffix get() = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                    Surface { Column { content() } }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_$suffix.png")
    }

    @Test
    fun recipeCards() = snap("recipe_cards") {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecipeCard(
                Recipe(
                    "a",
                    RecipeContent(title = "Weeknight chicken curry with rice", totalMinutes = 75),
                    favorite = true,
                ),
                onOpen = {},
                onToggleFavorite = {},
                modifier = Modifier.width(180.dp),
            )
            RecipeCard(
                Recipe("b", RecipeContent(title = "Pancakes")),
                onOpen = {},
                onToggleFavorite = {},
                modifier = Modifier.width(180.dp),
            )
        }
    }

    @Test
    fun ingredientChecklist() = snap("recipe_checklist") {
        Column(Modifier.padding(16.dp)) {
            IngredientChecklist(
                listOf("Dough" to "2 cups flour", "Dough" to "1 tsp salt", "Filling" to "3 apples, sliced"),
                remember { mutableStateListOf(1) },
            )
        }
    }

    @Test
    fun groceryCandidates() = snap("recipe_grocery_candidates") {
        val eggs = IngredientParser.parse("3 large eggs")
        val onions = product("onion", "Onions", unit = QuantityUnit.Pounds)
        Column(Modifier.padding(16.dp)) {
            val egg = product("egg", "Eggs")
            CandidateRow(
                GroceryCandidate(
                    eggs,
                    "Eggs",
                    3.0,
                    null,
                    "Pancakes: 3 large eggs",
                    egg,
                    egg,
                    false,
                ),
                false,
                onChange = {
                },
            )
            val salt = IngredientParser.parse("Salt to taste")
            CandidateRow(
                GroceryCandidate(salt, "Salt", null, null, "Pancakes: Salt to taste", null, null, true),
                false,
                onChange = {},
            )
            val onion = IngredientParser.parse("1 extra large onion, diced")
            CandidateRow(
                GroceryCandidate(
                    onion,
                    "Onions",
                    3.0,
                    QuantityUnit.Pounds,
                    "Pancakes: 1 extra large onion, diced",
                    onions,
                    null,
                    false,
                ),
                true,
                onChange = {},
            )
        }
    }

    @Test
    fun browserBars() = snap("recipe_browser_bars") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FoundBar("Fluffy buttermilk pancakes", heuristic = true, saving = false, savedId = null, onSave = {
            }, onOpen = {})
            FoundBar("Fluffy buttermilk pancakes", heuristic = false, saving = false, savedId = "r1", onSave = {
            }, onOpen = {})
            NotFoundBar(onCreateManually = {})
        }
    }
}
