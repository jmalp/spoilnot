package com.parcha.spoilnot.core.designsystem.motion

import androidx.activity.ComponentActivity
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The stretch overscroll warm-up (DESIGN 13.1, after v1, 2026-11-23): it waits for the start-up screen to report
 * itself drawn and then a moment more, shows for a few frames without anything for screen readers, and leaves. The
 * shaders it compiles are measured with `JankBenchmark` on the benchmark emulator (RUNBOOK), since Robolectric draws
 * without the GPU.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StretchWarmUpTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(overscroll: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                val warmUp = @Composable {
                    Box { StretchOverscrollWarmUp(Modifier.size(411.dp, 80.dp).testTag(TAG)) }
                }
                if (overscroll) {
                    warmUp()
                } else {
                    CompositionLocalProvider(LocalOverscrollFactory provides null) {
                        warmUp()
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun shown() = compose.onAllNodes(hasTestTag(TAG)).fetchSemanticsNodes().size == 1

    @Test
    fun waitsForTheStartUpScreenThenStretchesOnceAndLeaves() {
        show()
        advance(5_000)
        assertFalse("The warm-up ran before the start-up screen reported itself drawn", shown())

        compose.runOnUiThread { compose.activity.reportFullyDrawn() }
        advance(WARM_UP_DELAY_MS - 100)
        assertFalse("The warm-up ran before its delay", shown())

        advance(150)
        assertTrue("The warm-up didn't run after the start-up screen reported itself drawn", shown())
        // Nothing in it reaches screen readers: no row, no icon.
        compose.onNodeWithTag(TAG).onChildren().assertCountEquals(0)

        advance(200)
        assertFalse("The warm-up stayed after its frames", shown())
        advance(5_000)
        assertFalse("The warm-up came back", shown())
    }

    @Test
    fun startsAtOnceWhenTheScreenHasAlreadyReported() {
        compose.runOnUiThread { compose.activity.reportFullyDrawn() }
        show()
        advance(WARM_UP_DELAY_MS + 50)
        assertTrue("The warm-up didn't run after an earlier report", shown())
        advance(200)
        assertFalse("The warm-up stayed after its frames", shown())
    }

    @Test
    fun skipsWhenOverscrollIsOff() {
        compose.runOnUiThread { compose.activity.reportFullyDrawn() }
        show(overscroll = false)
        advance(WARM_UP_DELAY_MS + 50)
        assertFalse("The warm-up ran with overscroll off", shown())
        advance(5_000)
        assertFalse("The warm-up ran with overscroll off", shown())
    }

    private companion object {
        const val TAG = "stretch_warm_up"
    }
}
