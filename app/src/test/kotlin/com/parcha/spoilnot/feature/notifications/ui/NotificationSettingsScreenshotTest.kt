package com.parcha.spoilnot.feature.notifications.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.PushedScaffold
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.settings.ui.AboutHeader
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings' Notifications and About pages under their pushed header (DESIGN 19.7), light and dark at 1.0× and 2.0× text
 * (after v1, review CODE-07).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NotificationSettingsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String) =
        compose.onRoot().captureRoboImage(Screenshots.screenshotName(name, dark, fontScale))

    /**
     * Blocked on this phone, so the banner shows above the settings. The time's helper says the reminder arrives within a
     * few minutes of it (after v1, 2026-11-24: the job starts every quarter hour).
     */
    @Test
    fun notifications() {
        compose.setThemed(dark, fontScale) {
            PushedScaffold(title = stringResource(R.string.settings_notifications), onBack = {}) { padding ->
                NotificationSettingsContent(
                    NotificationPrefs(),
                    allowed = false,
                    canAsk = true,
                    onUpdate = {},
                    onTurnOn = {},
                    onTest = {},
                    onBattery = {},
                    modifier = Modifier.padding(padding),
                )
            }
        }
        assertEquals(
            1,
            compose.onAllNodesWithText("It arrives within a few minutes of this time.").fetchSemanticsNodes().size,
        )
        capture("settings_notifications")
    }

    /** A fixed version, the one the references were recorded with, so a release doesn't change the images. */
    @Test
    fun about() {
        compose.setThemed(dark, fontScale) {
            PushedScaffold(title = stringResource(R.string.settings_about), onBack = {}) { padding ->
                Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                    AboutHeader(onVersionTap = {}, versionName = "1.0.0-beta.9-dev", versionCode = 10)
                }
            }
        }
        capture("settings_about")
    }
}
