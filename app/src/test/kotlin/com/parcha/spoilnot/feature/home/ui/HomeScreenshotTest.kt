package com.parcha.spoilnot.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.home.domain.HomeDashboard
import com.parcha.spoilnot.feature.insights.domain.Insights
import com.parcha.spoilnot.feature.insights.domain.MonthStats
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.recipes.domain.UseItUp
import com.parcha.spoilnot.feature.recipes.domain.recipe
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home cards in light and dark at font scale 1.0 and 2.0 (DESIGN 13.1; contrast from beta feedback). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeCards() {
        val pantry = PantryData(
            listOf(
                product("milk"),
                product("yogurt"),
                product("cheese"),
                product("oil", name = "Olive oil", min = 1.0),
            ),
            // Two expired: Throw away all 2 expired. No activity: the compact card (after v1).
            listOf(
                batch("m1", "milk", inDays = 1),
                batch("y1", "yogurt", inDays = -1),
                batch("c1", "cheese", inDays = -3),
            ),
            fromCache = false,
        )
        val summary = HomeDashboard.summary(pantry, emptyList(), emptyList(), TODAY)
        val navigation = HomeNavigation({}, {}, {}, {})
        val useItUp = UseItUp.suggestions(
            listOf(recipe("r1", "Creamy milk pudding", ingredients = listOf("2 cups milk")), recipe("r2", "Toast")),
            pantry,
            TODAY,
        )
        val october = YearMonth.from(TODAY)
        val insights = Insights.summary(
            listOf(
                MonthStats(october, used = 11, thrown = mapOf(DiscardReason.Expired to 1), thrownByDay = mapOf(3 to 1)),
            ),
            TODAY,
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                    Surface {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) { HomeCards(summary, TODAY, "me", navigation, insights, useItUp) }
                    }
                }
            }
        }
        val suffix = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_cards_$suffix.png")
    }

    /** Nothing needs doing: the empty cards are one compact row each (after v1). */
    @Test
    fun calmCards() {
        val pantry = PantryData(listOf(product("rice")), listOf(batch("r1", "rice", inDays = 90)), fromCache = false)
        val summary = HomeDashboard.summary(pantry, emptyList(), emptyList(), TODAY)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                    Surface {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) { HomeCards(summary, TODAY, "me", HomeNavigation({}, {}, {}, {})) }
                    }
                }
            }
        }
        val suffix = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_calm_$suffix.png")
    }
}
