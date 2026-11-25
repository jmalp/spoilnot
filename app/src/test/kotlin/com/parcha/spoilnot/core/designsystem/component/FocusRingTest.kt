package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parcha.spoilnot.testing.setThemed
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The keyboard focus ring (review A11Y-11) on Compose 1.13's focus API (after v1, 2026-11-24): it rings what has
 * focus in keyboard mode, follows a Tab and a move made by code, and hides for touch.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class FocusRingTest {
    @get:Rule
    val compose = createComposeRule()

    private var primary = Color.Unspecified
    private val inputModes = FakeInputModes()
    private val second = FocusRequester()

    /**
     * Robolectric's view never leaves touch mode for a key or enters it for a tap (Android decides that below the app),
     * so the test sets the mode where the ring and Material's buttons read it, as the platform would.
     */
    private class FakeInputModes : InputModeManager {
        override var inputMode: InputMode by mutableStateOf(InputMode.Touch)

        @ExperimentalComposeUiApi
        override fun requestInputMode(inputMode: InputMode): Boolean {
            this.inputMode = inputMode
            return true
        }
    }

    private fun show() = compose.setThemed(dark = false, fontScale = 1f) {
        primary = MaterialTheme.colorScheme.primary
        CompositionLocalProvider(LocalInputModeManager provides inputModes) { Content() }
    }

    @Composable
    private fun Content() {
        FocusRing(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(onClick = {}, modifier = Modifier.testTag("one")) { Text("One") }
                Button(onClick = {}, modifier = Modifier.testTag("two").focusRequester(second)) { Text("Two") }
                Button(onClick = {}, modifier = Modifier.testTag("three")) { Text("Three") }
            }
        }
    }

    /** Whether the ring's left side runs beside the control tagged [tag]: 3 dp out, in the middle of its 2 dp stroke. */
    private fun ringed(tag: String): Boolean {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val bounds = compose.onNodeWithTag(tag).getBoundsInRoot()
        val (x, y) = with(compose.density) {
            (bounds.left - FocusRingGap - FocusRingWidth / 2).toPx().roundToInt() to
                ((bounds.top + bounds.bottom) / 2).toPx().roundToInt()
        }
        val pixel = Color(image.getPixel(x, y))
        return abs(pixel.red - primary.red) < 0.08f &&
            abs(pixel.green - primary.green) < 0.08f &&
            abs(pixel.blue - primary.blue) < 0.08f
    }

    private fun keyboardMode() = compose.runOnIdle { inputModes.inputMode = InputMode.Keyboard }

    @Test
    fun touch_showsNoRing() {
        show()
        compose.waitForIdle()
        listOf("one", "two", "three").forEach { assertFalse("no ring around $it for touch", ringed(it)) }
    }

    @Test
    fun ringFollowsFocus_byCodeAndByTab_andHidesForTouch() {
        show()
        keyboardMode()
        compose.onNodeWithTag("one").requestFocus()
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue("ring around the focused button", ringed("one"))
        assertFalse(ringed("two"))

        // Moved by code, with no key: found by the checks that follow a focus change.
        compose.runOnIdle { second.requestFocus() }
        compose.mainClock.advanceTimeBy(300)
        assertTrue("ring follows focus moved by code", ringed("two"))
        assertFalse(ringed("one"))

        // Tab moves to the next button.
        compose.onNodeWithTag("two").performKeyInput { pressKey(Key.Tab) }
        compose.mainClock.advanceTimeBy(100)
        assertTrue("ring follows Tab", ringed("three"))
        assertFalse(ringed("two"))

        // Back in touch mode: gone at once, with no key and nothing else drawn.
        compose.runOnIdle { inputModes.inputMode = InputMode.Touch }
        compose.waitForIdle()
        assertFalse("no ring after touch", ringed("three"))
    }
}
