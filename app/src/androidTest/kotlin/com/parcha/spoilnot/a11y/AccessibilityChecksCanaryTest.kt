package com.parcha.spoilnot.a11y

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the checks in AccessibilityChecksTest are active: a tiny unlabeled button must fail them. */
@OptIn(ExperimentalTestApi::class)
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
}
