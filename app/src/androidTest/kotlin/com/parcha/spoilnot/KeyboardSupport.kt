package com.parcha.spoilnot

import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/**
 * Closes the soft keyboard after typing, then waits until the keyboard is gone and [next], the control the test taps
 * next, has stopped moving on screen.
 *
 * Typing opens the keyboard, and the screen moves while it slides in: the app's screens pad for it (`imePadding`) and
 * dialogs resize. A tap sent in that moment lands where the control was a moment before and does nothing. On a busy
 * emulator the keyboard opened half a second after the text was typed, just as `JoinAndSignOutUiTest` tapped Continue,
 * so the join dialog never came (review B28). Waiting longer for the dialog could not help; tapping a still screen does.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.closeKeyboard(next: SemanticsMatcher) {
    Espresso.closeSoftKeyboard()
    waitUntil("the keyboard is hidden", KEYBOARD_TIMEOUT_MS) { !keyboardShown() }
    var last: Rect? = null
    var still = 0
    waitUntil("${next.description} stops moving", KEYBOARD_TIMEOUT_MS) {
        val bounds = onAllNodes(next).fetchSemanticsNodes().singleOrNull()?.let {
            Rect(it.positionOnScreen, Size(it.size.width.toFloat(), it.size.height.toFloat()))
        }
        still = if (bounds != null && bounds == last) still + 1 else 0
        last = bounds
        if (still < STILL_CHECKS) Thread.sleep(CHECK_INTERVAL_MS)
        still >= STILL_CHECKS
    }
}

/** Whether the keyboard shows over the resumed activity's window, from that window's insets (a dialog's is its own). */
private fun keyboardShown(): Boolean {
    var shown = false
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        val decor: View? = activity?.window?.decorView
        shown = decor?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) } == true
    }
    return shown
}

private const val KEYBOARD_TIMEOUT_MS = 10_000L

/** Unchanged for three checks 100 ms apart: the keyboard's slide (about 300 ms) has ended. */
private const val STILL_CHECKS = 3
private const val CHECK_INTERVAL_MS = 100L
