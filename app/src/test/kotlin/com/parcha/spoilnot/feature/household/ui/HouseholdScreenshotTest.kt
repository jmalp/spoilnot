package com.parcha.spoilnot.feature.household.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.household.domain.Household
import com.parcha.spoilnot.feature.household.domain.JoinRequest
import com.parcha.spoilnot.feature.household.domain.JoinRequestStatus
import com.parcha.spoilnot.feature.household.domain.JoinState
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The household gate and Household settings, light and dark at 1.0× and 2.0× text (after v1, review CODE-07). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HouseholdScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String) =
        compose.onRoot().captureRoboImage(Screenshots.screenshotName(name, dark, fontScale))

    private fun gate(join: JoinState?) {
        compose.setThemed(dark, fontScale) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GateCards(join, GateViewModel.UiState(), {}, {}, {})
            }
        }
    }

    @Test
    fun gateCreateOrJoin() {
        gate(null)
        capture("gate")
    }

    @Test
    fun gateWaiting() {
        gate(JoinState.Waiting(PendingJoin("h1", "The Riveras", "RVRSK7M4PQ")))
        capture("gate_waiting")
    }

    /** The owner's view with a join request: Approve and Decline name the person for TalkBack (review A11Y-08). */
    @Test
    fun householdSettings() {
        val content = HouseholdViewModel.Content(
            myUid = "me",
            myRole = Role.Owner,
            household = Household("h1", "The Riveras", "me", "RVRSK7M4PQ"),
            members = listOf(Member("me", "Sam", Role.Owner, null), Member("u2", "Alex", Role.Member, null)),
            requests = listOf(
                JoinRequest("u3", "Robin Lee", "RVRSK7M4PQ", JoinRequestStatus.Pending, null, "ro***@example.test"),
            ),
        )
        compose.setThemed(dark, fontScale) {
            HouseholdCards(
                content,
                content.household!!,
                HouseholdViewModel.Actions(),
                remember { SnackbarHostState() },
                onApprove = {},
                onDecline = {},
                onConfirm = {},
            )
        }
        capture("household_settings")
    }
}
