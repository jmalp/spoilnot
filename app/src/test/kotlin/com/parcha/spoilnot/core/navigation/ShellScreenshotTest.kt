package com.parcha.spoilnot.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.LoadError
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFab
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The signed-in shell in the 2026-11 look (DESIGN 19.7, 19.9, 19.11): the header with its line above the title and
 * the Settings button with a join-request badge, both banners first in the content, the floating button, an Undo
 * snackbar and the tab bar; light and dark, text at 1.0 and 2.0.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ShellScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theShellInTheNewLook() {
        compose.setThemed(dark, fontScale) {
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                snackbar.showSnackbar("Cheese used up", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
            }
            CompositionLocalProvider(LocalJoinRequestCount provides 2) {
                ShellLayout(bar = { TabBar(TopLevelDestination.Pantry, it) {} }) {
                    TabScaffold(
                        title = "Pantry",
                        eyebrow = "3 items",
                        onOpenSettings = {},
                        actions = {
                            IconButton(onClick = {}) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort and group")
                            }
                        },
                        floatingActionButton = { SpoilNotFab("Add item", Icons.Rounded.Add, onClick = {}) },
                        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
                    ) { padding ->
                        Column(Modifier.padding(padding).fillMaxSize()) {
                            LoadError(onRetry = {})
                            OfflineBanner()
                            val names = listOf("Milk", "Greek yogurt", "Eggs")
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(ListGroupGap),
                            ) {
                                names.forEachIndexed { index, name ->
                                    ListSegment(
                                        segmentPosition(index, names.size),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    ) { Text(name, style = SpoilNotText.RowTitle) }
                                }
                            }
                        }
                    }
                }
            }
        }

        compose.onNode(isHeading() and hasText("Pantry")).assertExists()
        compose.onNodeWithText("3 items").assertExists()
        // The badge's number is in the button's description, not read again (review A11Y-08).
        compose.onNode(hasContentDescription("2", substring = true)).assertHasClickAction()
        compose.onNodeWithText("Add item").assertHasClickAction()
        compose.onNodeWithText("Undo").assertHasClickAction()
        compose.onRoot().captureRoboImage(Screenshots.screenshotName("shell_look", dark, fontScale))
    }
}
