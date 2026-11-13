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
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFab
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSheet
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

    /** Material's own sheet fails ATF on its 32 dp drag handle (A11Y-06), and the app's SpoilNotSheet passes. */
    @Test
    fun materialsDragHandleFailsAndSpoilNotSheetsPasses() {
        var ours by mutableStateOf(false)
        compose.setContent {
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            if (ours) {
                SpoilNotSheet(onDismissRequest = {}, sheetState = state) { Text("A sheet", Modifier.padding(24.dp)) }
            } else {
                ModalBottomSheet(onDismissRequest = {}, sheetState = state) { Text("A sheet", Modifier.padding(24.dp)) }
            }
        }
        compose.enableAccessibilityChecks(spoilNotAccessibilityValidator())
        compose.waitUntil(10_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes().size >= 2 }
        val material = runCatching { compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks() }.exceptionOrNull()
        assertTrue("expected Material's drag handle to fail, got $material", material != null)
        ours = true
        compose.waitUntil(10_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes().size >= 2 }
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks()
    }

    @Test
    fun spoilNotFabIsNamedByItsText() {
        compose.setContent { SpoilNotFab("Add item", Icons.Rounded.Add, onClick = {}) }
        compose.enableAccessibilityChecks()
        compose.assertEveryControlHasAName()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("Add item").assertHasClickAction()
    }
}
