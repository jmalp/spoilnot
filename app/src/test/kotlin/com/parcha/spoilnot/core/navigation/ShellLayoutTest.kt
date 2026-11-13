package com.parcha.spoilnot.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A screen with two buttons under the shell's frame and its real tab bar. */
@androidx.compose.runtime.Composable
private fun Shell(hide: Boolean = false, overlay: Boolean = false) {
    ShellLayout(
        bar = { TabBar(TopLevelDestination.Pantry, it) {} },
        hideBehindOverlay = hide,
        overlay = { if (overlay) Text("Tour card", Modifier.align(Alignment.Center)) },
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Button(onClick = {}, modifier = Modifier.testTag("first")) { Text("First on the screen") }
            Button(onClick = {}, modifier = Modifier.testTag("second")) { Text("Second") }
        }
    }
}

/** The shell's order, ring and tour behavior (after v1, review A11Y-10, A11Y-11). */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ShellLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var focus: FocusManager
    private lateinit var inputMode: InputModeManager

    /** The shell, keyboard mode on, as a hardware keyboard's first key press does. */
    private fun keyboardShell() {
        compose.setThemed(dark = false, fontScale = 1f) {
            focus = LocalFocusManager.current
            inputMode = LocalInputModeManager.current
            Shell()
        }
        // Entering keyboard mode focuses the first control, as a phone does on a keyboard's first key press.
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
    }

    /** Tab moves focus Next, as here. */
    private fun tab() = compose.runOnIdle { focus.moveFocus(FocusDirection.Next) }

    @Test
    fun tabReachesTheScreenBeforeTheTabBar() {
        keyboardShell()
        compose.onNodeWithTag("first").assertIsFocused()
        tab()
        compose.onNodeWithTag("second").assertIsFocused()
        tab()
        compose.onNode(hasText("Home") and isSelectable()).assertIsFocused()
    }

    /** A 2 dp ring in the primary color around the control that has keyboard focus. */
    @Test
    fun keyboardFocusShowsARing() {
        keyboardShell()
        compose.onNodeWithTag("first").assertIsFocused()
        compose.onRoot().captureRoboImage("shell_focus_ring.png")
    }

    @Test
    fun theScreenIsHiddenFromScreenReadersBehindTheTour() {
        compose.setThemed(dark = false, fontScale = 1f) { Shell(hide = true, overlay = true) }
        compose.onNodeWithText("Tour card").assertExists()
        compose.onNodeWithTag("first").assertDoesNotExist()
        compose.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test
    fun withoutAScreenReaderTheTourLeavesTheTreeAlone() {
        compose.setThemed(dark = false, fontScale = 1f) { Shell(hide = false, overlay = true) }
        compose.onNodeWithTag("first").assertExists()
        compose.onNode(hasText("Pantry") and isSelectable()).assertExists()
    }
}

/** The tab bar in light and dark at 1.0× and 2.0× text: one line per label (after v1, review UX-14). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TabBarScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tabBar() {
        compose.setThemed(dark, fontScale) { Box { TabBar(TopLevelDestination.Groceries) {} } }
        compose.onRoot().captureRoboImage(Screenshots.screenshotName("shell_tab_bar", dark, fontScale))
    }
}
