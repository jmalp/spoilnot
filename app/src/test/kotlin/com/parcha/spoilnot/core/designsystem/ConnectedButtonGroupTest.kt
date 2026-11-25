package com.parcha.spoilnot.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.parcha.spoilnot.core.designsystem.component.ConnectedButtonGroup
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A connected button group (DESIGN 19.9) stacks its options as radio buttons at large text sizes, so labels are never
 * cut off; it replaced the segmented buttons, which did the same (after v1), with Settings' step of the new look.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ConnectedButtonGroupTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                var picked by remember { mutableStateOf("Metric (g, kg, ml)") }
                ConnectedButtonGroup(
                    options = listOf("Metric (g, kg, ml)", "Imperial (oz, lb)"),
                    selected = picked,
                    optionLabel = { it },
                    onPick = { picked = it },
                )
            }
        }
    }

    @Test
    fun normalTextJoinsTheButtonsAndPicks() {
        show(1f)
        compose.onNodeWithTag("connected_row").assertExists()
        compose.onNodeWithTag("connected_list").assertDoesNotExist()
        compose.onNodeWithText("Imperial (oz, lb)").performClick().assertIsSelected()
        compose.onNodeWithText("Metric (g, kg, ml)").assertIsNotSelected()
    }

    @Test
    fun largeTextStacksTheOptionsAndStillPicks() {
        show(2f)
        compose.onNodeWithTag("connected_list").assertExists()
        compose.onNodeWithTag("connected_row").assertDoesNotExist()
        compose.onNodeWithText("Imperial (oz, lb)").performClick()
        compose.onNodeWithText("Imperial (oz, lb)").assertIsSelected()
    }
}
