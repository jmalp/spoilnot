package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The floating button's name (DESIGN 6.8, review 07 A11Y-01) and its screenshots in light and dark at font scale 1.0
 * and 2.0. ./gradlew recordRoborazziDevDebug writes the images to app/build/outputs/roborazzi for review.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SpoilNotFabTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    private val suffix get() = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                Surface { Box(Modifier.padding(16.dp)) { content() } }
            }
        }
    }

    @Test
    fun theButtonIsNamedByItsTextOnce() {
        show { SpoilNotFab("Add item", Icons.Rounded.Add, onClick = {}) }
        compose.onNodeWithText("Add item")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString("Add item"))))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/spoilnot_fab_$suffix.png")
    }

    /** What the four buttons were before (A11Y-01): Material hides the text, so nothing names the button. */
    @Test
    fun materialsExtendedFabWithADecorativeIconHasNoName() {
        show {
            ExtendedFloatingActionButton(
                text = { Text("Add item") },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {},
            )
        }
        compose.onAllNodes(hasText("Add item") and hasClickAction()).assertCountEquals(0)
    }
}
