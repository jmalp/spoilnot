package com.parcha.spoilnot.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.notifications.ui.formatHour
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings' Notifications row (after v1, review UX-02): on, "Off on this phone" while this phone blocks the reminder the
 * member wants, and Off. Light and dark at font 1.0 and 2.0.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NotificationsRowScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun notificationsRow() {
        val on = NotificationPrefs()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                    Surface {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            NotificationsRow(on, allowed = true, onOpen = {})
                            NotificationsRow(on, allowed = false, onOpen = {})
                            NotificationsRow(on.copy(enabled = false), allowed = false, onOpen = {})
                        }
                    }
                }
            }
        }
        assertEquals(1, compose.onAllNodesWithText("Daily, shortly after ${formatHour(9)}").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("Off on this phone").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("Off").fetchSemanticsNodes().size)
        val suffix = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"
        compose.onRoot().captureRoboImage("settings_notifications_row_$suffix.png")
    }
}
