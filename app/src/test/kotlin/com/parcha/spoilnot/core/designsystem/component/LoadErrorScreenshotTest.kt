package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Couldn't load." with Retry (DESIGN 6.7, after v1) above a list; light and dark, text at 1.0 and 2.0. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LoadErrorScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theMessageSaysWhatHappenedAndRetries() {
        var retries = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                    Surface {
                        Column {
                            LoadError(onRetry = { retries++ })
                            Text("Milk")
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Couldn't load.").assertExists()
        compose.onNodeWithTag("load_error_retry").assertHasClickAction().performClick()
        assertEquals(1, retries)
        val suffix = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/load_error_$suffix.png")
    }
}
