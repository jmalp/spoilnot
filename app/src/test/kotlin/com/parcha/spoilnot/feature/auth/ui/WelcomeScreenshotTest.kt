package com.parcha.spoilnot.feature.auth.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The welcome screen: the Fredoka title in the theme color, readable in dark mode (beta feedback). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WelcomeScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun welcome() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) { WelcomeScreen({}, {}) }
            }
        }
        val suffix = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/welcome_$suffix.png")
    }
}
