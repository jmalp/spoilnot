package com.parcha.spoilnot.feature.settings.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.prefs.ThemeMode
import com.parcha.spoilnot.feature.auth.domain.Units
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings in the 2026-11 look (DESIGN 5.6, 19.12): the owner's page with an email that isn't verified yet, at the top
 * and scrolled to its end, light and dark at 1.0× and 2.0× text. Also how screen readers get its rows: one button per
 * row that opens or does something, read with both lines, and the crash reports row as one switch.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String) =
        compose.onRoot().captureRoboImage(Screenshots.screenshotName(name, dark, fontScale))

    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun settings() {
        compose.setThemed(dark, fontScale) {
            SettingsPage(
                content = SettingsViewModel.Content(
                    displayName = "Sam",
                    email = "sam@example.test",
                    householdName = "The Riveras",
                    memberCount = 2,
                    isOwner = true,
                ),
                state = SettingsViewModel.UiState(emailVerified = false),
                device = SettingsViewModel.Device(ThemeMode.System, ColorTheme.SpoilNot, crashReports = true),
                units = Units.Metric,
                notifications = NotificationPrefs(),
                notificationsAllowed = false,
                error = null,
                actions = SettingsActions(),
            )
        }
        compose.onNodeWithTag("row_household")
            .assertHasClickAction()
            .assert(role(Role.Button))
            .assert(hasText("The Riveras · 2 members", substring = true))
        compose.onNodeWithTag("row_notifications").assert(hasText("Off on this phone", substring = true))
        // The page's headings: its title and its groups (the account card has no title of its own).
        listOf("Settings", "Appearance", "Data").forEach { compose.onNode(hasText(it) and isHeading()).assertExists() }
        capture("settings")

        compose.onNodeWithTag("button_export").performScrollTo().assertHasClickAction().assert(role(Role.Button))
        compose.onNodeWithTag("row_crash_reports")
            .assert(role(Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        // Plain buttons: toggleable made TalkBack say "not checked" on them (review A11Y-08).
        compose.onNodeWithTag("row_take_tour").assert(role(Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        compose.onNodeWithTag("button_clear_household").performScrollTo()
        capture("settings_end")
    }
}
