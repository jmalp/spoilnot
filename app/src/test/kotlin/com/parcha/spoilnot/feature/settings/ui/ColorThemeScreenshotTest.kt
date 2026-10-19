package com.parcha.spoilnot.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.core.prefs.ThemeMode
import com.parcha.spoilnot.feature.auth.domain.Units
import com.parcha.spoilnot.feature.home.domain.HomeDashboard
import com.parcha.spoilnot.feature.home.ui.HomeCards
import com.parcha.spoilnot.feature.home.ui.HomeNavigation
import com.parcha.spoilnot.feature.insights.domain.Insights
import com.parcha.spoilnot.feature.insights.domain.MonthStats
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Appearance with the color picker (light and dark, font 1.0 and 2.0), and a gallery of Home in every
 * fixed theme so each preset can be reviewed (DESIGN 6.4).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [35])
class ColorThemeScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    private val suffix get() = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"

    @Test
    fun appearanceCard() {
        var picked by mutableStateOf(ColorTheme.Fiery)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = picked) {
                    Surface {
                        Column(Modifier.padding(16.dp)) {
                            AppearanceCard(
                                SettingsViewModel.Device(ThemeMode.System, picked, true),
                                Units.Metric,
                                onThemeMode = {},
                                onColorTheme = { picked = it },
                                onUnits = {},
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("color_theme_fiery").assertIsSelected()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_colors_$suffix.png")
        compose.onNodeWithTag("color_theme_summer_fun").performClick()
        compose.onNodeWithTag("color_theme_summer_fun").assertIsSelected()
    }

    @Test
    fun homeInEveryTheme() {
        if (fontScale != 1f) return // one gallery per mode is enough to judge the colors
        val pantry = PantryData(
            listOf(product("milk"), product("yogurt"), product("oil", name = "Olive oil", min = 1.0)),
            listOf(batch("m1", "milk", inDays = 1), batch("y1", "yogurt", inDays = -1)),
            fromCache = false,
        )
        val summary = HomeDashboard.summary(pantry, emptyList(), emptyList(), TODAY)
        val insights = Insights.summary(
            listOf(MonthStats(YearMonth.from(TODAY), 11, mapOf(DiscardReason.Expired to 2), mapOf(3 to 2))),
            TODAY,
        )
        var theme by mutableStateOf(ColorTheme.SpoilNot)
        compose.setContent {
            SpoilNotTheme(darkTheme = dark, colorTheme = theme) {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeCards(summary, TODAY, "me", HomeNavigation({}, {}, {}, {}), insights)
                    }
                }
            }
        }
        ColorTheme.entries.filter { it != ColorTheme.Wallpaper }.forEach {
            theme = it
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/theme_${it.id}_$suffix.png")
        }
    }
}
