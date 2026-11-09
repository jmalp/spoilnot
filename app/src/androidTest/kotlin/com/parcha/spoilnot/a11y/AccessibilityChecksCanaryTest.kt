package com.parcha.spoilnot.a11y

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.R as Material3R
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFab
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the checks in AccessibilityChecksTest are active: a tiny unlabeled button must fail ATF, and Material's extended
 * floating button with a decorative icon must fail the app's own name rule, which the app's [SpoilNotFab] passes.
 * `enableAccessibilityChecks()` needs Android 14 (API 34), so older phones skip this class (review 11, CODE-12).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
@SdkSuppress(minSdkVersion = 34)
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksCanaryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aTinyUnlabeledButtonFails() {
        compose.setContent { Box(Modifier.size(20.dp).background(Color.Gray).clickable {}) }
        compose.enableAccessibilityChecks()
        val failure = runCatching { compose.onRoot().tryPerformAccessibilityChecks() }.exceptionOrNull()
        assertTrue("expected an accessibility failure, got $failure", failure != null)
    }

    /** The floating buttons before B03 (review 07, A11Y-01): ATF passes them, the name rule must not. */
    @Test
    fun materialsExtendedFabWithADecorativeIconFailsTheNameRule() {
        compose.setContent {
            ExtendedFloatingActionButton(
                text = { Text("Add item") },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {},
            )
        }
        val failure = runCatching { compose.assertEveryControlHasAName() }.exceptionOrNull()
        assertTrue("expected the name rule to fail, got $failure", failure is AssertionError)
    }

    /** Material's 32 dp drag handle is the one known ATF result (A11Y-06, B19); anything else in a sheet still fails. */
    @Test
    fun inASheetOnlyMaterialsDragHandleIsExcused() {
        var tiny by mutableStateOf(false)
        compose.setContent {
            ModalBottomSheet(onDismissRequest = {
            }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Text("A sheet", Modifier.padding(24.dp))
                if (tiny) Box(Modifier.size(20.dp).background(Color.Gray).clickable {})
            }
        }
        compose.enableAccessibilityChecks(spoilNotAccessibilityValidator(dragHandleDescription()))
        compose.waitUntil(10_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes().size >= 2 }
        compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks()
        tiny = true
        compose.waitForIdle()
        val failure = runCatching { compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks() }.exceptionOrNull()
        assertTrue("expected the tiny button to fail, got $failure", failure != null)
    }

    private fun dragHandleDescription() = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(Material3R.string.m3c_bottom_sheet_drag_handle_description)

    @Test
    fun spoilNotFabIsNamedByItsText() {
        compose.setContent { SpoilNotFab("Add item", Icons.Rounded.Add, onClick = {}) }
        compose.enableAccessibilityChecks()
        compose.assertEveryControlHasAName()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("Add item").assertHasClickAction()
    }
}
