package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Mark opened and Throw away in their own windows, light and dark at 1.0× and 2.0× text (after v1, review CODE-07);
 * the sheet shows its 48 dp drag handle (review A11Y-06).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PantrySheetsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(Screenshots.screenshotName(name, dark, fontScale))
    }

    @Test
    fun markOpened() {
        compose.setThemed(dark, fontScale) {
            OpenedSheet(batch("m1", "milk", quantity = 2.0), rememberedDays = 5, today = TODAY, { _, _ -> }, {})
        }
        capture("pantry_mark_opened")
    }

    @Test
    fun throwAway() {
        compose.setThemed(dark, fontScale) { ThrowAwayDialog("Milk", onReason = {}, onDismiss = {}) }
        capture("pantry_throw_away")
    }
}
