package com.parcha.spoilnot.feature.tutorial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.component.CoachMarks
import com.parcha.spoilnot.core.designsystem.component.LocalCoachMarks
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.home.domain.HomeDashboard
import com.parcha.spoilnot.feature.home.ui.HomeCards
import com.parcha.spoilnot.feature.home.ui.HomeNavigation
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.tutorial.domain.TourStep
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tour over Home (DESIGN 5.8) in light and dark at font scale 1.0 and 2.0. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TourScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, step: TourStep) {
        val pantry = PantryData(listOf(product("milk")), listOf(batch("m1", "milk", inDays = 1)), fromCache = false)
        val summary = HomeDashboard.summary(pantry, emptyList(), emptyList(), TODAY)
        compose.setContent {
            val density = LocalDensity.current
            val marks = remember { CoachMarks() }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalCoachMarks provides marks,
            ) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                    Surface {
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) { HomeCards(summary, TODAY, "me", HomeNavigation({}, {}, {}, {})) }
                            TourOverlay(step, marks, {})
                        }
                    }
                }
            }
        }
        val suffix = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_$suffix.png")
    }

    @Test
    fun welcome() = capture("tour_welcome", TourStep.Welcome)

    @Test
    fun homeStep() = capture("tour_home", TourStep.Home)
}
