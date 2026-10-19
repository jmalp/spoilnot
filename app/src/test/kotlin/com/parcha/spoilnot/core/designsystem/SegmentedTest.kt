package com.parcha.spoilnot.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.parcha.spoilnot.core.designsystem.component.Segmented
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Segmented stacks its options at large text sizes, so labels are never cut off (after v1). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SegmentedTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                var picked by remember { mutableStateOf("Metric (g, kg, ml)") }
                Segmented(
                    label = "Units",
                    options = listOf("Metric (g, kg, ml)", "Imperial (oz, lb)"),
                    selected = picked,
                    optionLabel = { it },
                    onPick = { picked = it },
                )
            }
        }
    }

    @Test
    fun normalTextUsesSegmentedButtons() {
        show(1f)
        compose.onNodeWithTag("segmented_row").assertExists()
        compose.onNodeWithTag("segmented_list").assertDoesNotExist()
    }

    @Test
    fun largeTextStacksTheOptionsAndStillPicks() {
        show(2f)
        compose.onNodeWithTag("segmented_list").assertExists()
        compose.onNodeWithText("Imperial (oz, lb)").performClick()
        compose.onNodeWithText("Imperial (oz, lb)").assertIsSelected()
    }
}
