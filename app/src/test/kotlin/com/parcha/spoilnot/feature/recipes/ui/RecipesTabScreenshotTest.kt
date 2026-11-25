package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.navigation.ShellLayout
import com.parcha.spoilnot.core.navigation.TabBar
import com.parcha.spoilnot.core.navigation.TopLevelDestination
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Recipes tab in the 2026-11 look (DESIGN 19.7, 19.10, 19.12): how many recipes above the title, the round search
 * field, Favorites and the tags as chips, and the cards with their letters in leaves. Light and dark, text at 1.0 and
 * 2.0.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecipesTabScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private val recipes = listOf(
        Recipe(
            "curry",
            RecipeContent(title = "Weeknight chicken curry with rice", totalMinutes = 75),
            favorite = true,
        ),
        Recipe("pancakes", RecipeContent(title = "Pancakes", totalMinutes = 20, tags = listOf("Breakfast"))),
        Recipe("tacos", RecipeContent(title = "Tacos")),
        Recipe("pudding", RecipeContent(title = "Creamy milk pudding")),
    )

    @Test
    fun theBoxAsCards() {
        val state = RecipesViewModel.UiState(
            loading = false,
            recipes = recipes,
            tags = listOf("Breakfast"),
            total = recipes.size,
        )
        val handlers = RecipesHandlers({}, {}, {}, {}, {}, {}, {})
        val navigation = RecipesNavigation({}, {}, {}, {}, {})
        compose.setThemed(dark, fontScale) {
            ShellLayout(bar = { TabBar(TopLevelDestination.Recipes, it) {} }) {
                RecipesTab(
                    state = state,
                    snackbar = remember { SnackbarHostState() },
                    navigation = navigation,
                    handlers = handlers,
                )
            }
        }
        compose.onNodeWithText("4 recipes").assertExists()
        compose.onNodeWithTag("recipe_search").assertExists()
        compose.onRoot().captureRoboImage(Screenshots.screenshotName("recipes_tab", dark, fontScale))
    }
}
