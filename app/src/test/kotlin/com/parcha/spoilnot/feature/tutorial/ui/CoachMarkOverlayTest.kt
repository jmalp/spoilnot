package com.parcha.spoilnot.feature.tutorial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.component.CoachMarks
import com.parcha.spoilnot.core.designsystem.component.LocalCoachMarks
import com.parcha.spoilnot.core.designsystem.component.coachMark
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.tutorial.domain.TourEvent
import com.parcha.spoilnot.feature.tutorial.domain.TourStep
import com.parcha.spoilnot.feature.tutorial.domain.TourTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tour overlay (DESIGN 5.8): taps in the hole reach the app, taps elsewhere do not. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CoachMarkOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<TourEvent>()
    private val clicks = mutableListOf<String>()
    private val tapped = mutableListOf<String>()

    private fun show(step: TourStep) {
        val marks = CoachMarks { tapped += it }
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                CompositionLocalProvider(LocalCoachMarks provides marks) {
                    Surface {
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.padding(top = 400.dp, start = 24.dp)) {
                                Button(
                                    onClick = { clicks += "add" },
                                    modifier = Modifier.testTag("add").coachMark(TourTarget.AddItem.key),
                                ) { Text("Add item") }
                                Button(onClick = { clicks += "other" }, modifier = Modifier.testTag("other")) {
                                    Text("Something else")
                                }
                            }
                            TourOverlay(step, marks, { events += it })
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theHighlightedButtonStillWorksAndReportsTheTap() {
        show(TourStep.AddItem)
        compose.onNodeWithTag("add").performTouchInput { click() }
        assertEquals(listOf("add"), clicks)
        assertEquals(listOf(TourTarget.AddItem.key), tapped)
    }

    @Test
    fun theDimmedPartTakesNoTaps() {
        show(TourStep.AddItem)
        compose.onNodeWithTag("other").performTouchInput { click() }
        assertEquals(emptyList<String>(), clicks)
    }

    @Test
    fun nextAndSkipAreReported() {
        show(TourStep.AddItem)
        compose.onNodeWithText("3 of 10").assertExists()
        compose.onNodeWithTag("coach_mark_next").performClick()
        compose.onNodeWithTag("coach_mark_skip").performClick()
        assertEquals(listOf(TourEvent.Next, TourEvent.Skip), events)
    }

    @Test
    fun theWelcomeCardIsCenteredWithStartAndTheLastStepHasNoSkip() {
        show(TourStep.Welcome)
        compose.onNodeWithText("Start the tour").assertExists()
        compose.onNodeWithText("Welcome to Spoil Not").assertExists()
        compose.onNodeWithTag("other").performTouchInput { click() }
        assertEquals(emptyList<String>(), clicks)
    }

    @Test
    fun theLastStepSaysDone() {
        show(TourStep.Invite)
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithTag("coach_mark_skip").assertDoesNotExist()
    }
}
