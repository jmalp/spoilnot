package com.parcha.spoilnot.feature.notifications.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.settings.ui.AboutHeader
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings' Notifications and About pages, light and dark at 1.0× and 2.0× text (after v1, review CODE-07). */
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

    /** Blocked on this phone, so the banner shows above the settings. */
    @Test
    fun notifications() {
        compose.setThemed(dark, fontScale) {
            NotificationSettingsContent(
                NotificationPrefs(),
                allowed = false,
                canAsk = true,
                onUpdate = {},
                onTurnOn = {},
                onTest = {},
                onBattery = {},
            )
        }
        capture("settings_notifications")
    }

    @Test
    fun about() {
        compose.setThemed(dark, fontScale) {
            Column(Modifier.verticalScroll(rememberScrollState())) { AboutHeader(onVersionTap = {}) }
        }
        capture("settings_about")
    }
}
