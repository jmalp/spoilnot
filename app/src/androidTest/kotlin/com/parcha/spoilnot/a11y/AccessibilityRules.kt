package com.parcha.spoilnot.a11y

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator

/**
 * The app's own accessibility rule, on top of the Accessibility Test Framework (DESIGN 6.8): every node a screen reader
 * can activate (it has a click action or a toggle state) has a name, as text or a content description, in the tree
 * TalkBack reads. ATF didn't flag Material's extended floating button, which had neither (review 07, A11Y-01 and
 * A11Y-05). Checks every window: the screen and any sheet, dialog or menu on top of it.
 */
fun SemanticsNodeInteractionsProvider.assertEveryControlHasAName() {
    val unnamed = onAllNodes(hasClickAction() or isToggleable())
        .fetchSemanticsNodes(atLeastOneRootRequired = true)
        .filterNot { it.isHidden() || it.hasName() }
    if (unnamed.isNotEmpty()) {
        val details = unnamed.joinToString("\n") { "- at ${it.boundsInWindow}: ${it.config}" }
        throw AssertionError("${unnamed.size} control(s) without a name for screen readers:\n$details")
    }
}

private fun SemanticsNode.hasName(): Boolean {
    val text = config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() }
    val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() }
    val editable = config.getOrNull(SemanticsProperties.EditableText)?.text?.isNotBlank() == true
    return text || description || editable
}

private fun SemanticsNode.isHidden(): Boolean =
    SemanticsProperties.HideFromAccessibility in config || SemanticsProperties.InvisibleToUser in config

/**
 * ATF's checks, as `enableAccessibilityChecks()` runs them, from each window's root. No result is excused: until B19,
 * Material's 32 dp bottom sheet drag handle was (A11Y-06); every sheet now uses `SpoilNotSheet`, whose handle is 48 dp.
 */
fun spoilNotAccessibilityValidator(): AccessibilityValidator = AccessibilityValidator().setRunChecksFromRootView(true)

/** Fails unless the node tagged [tag] holds at least one heading (review A11Y-09: list group headers). */
fun SemanticsNodeInteractionsProvider.assertHasHeadingIn(tag: String) {
    val headings = onAllNodes(isHeading() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = true)
    if (headings.isEmpty()) throw AssertionError("No heading in $tag")
}

/** Fails unless some node in any window is a heading whose text is [text] (review A11Y-09). */
fun SemanticsNodeInteractionsProvider.assertHeading(text: String) {
    val headings = onAllNodes(isHeading(), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = true)
    val found = headings.any { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
    }
    if (!found) throw AssertionError("\"$text\" is not a heading")
}
