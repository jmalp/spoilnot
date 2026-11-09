package com.parcha.spoilnot.a11y

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement
import org.hamcrest.BaseMatcher
import org.hamcrest.Description

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
 * ATF's checks, as `enableAccessibilityChecks()` runs them, minus one known result: Material's bottom sheet drag
 * handle (the clickable wrapper around the node Material describes as "Drag handle") is 32 dp wide (A11Y-06). Every
 * other check still runs on the sheets, and every result on other controls, including touch targets, still fails the
 * test. Batch B19 pads the handle to 48 dp and removes this exception.
 */
fun spoilNotAccessibilityValidator(dragHandleDescription: String): AccessibilityValidator = AccessibilityValidator()
    .setRunChecksFromRootView(true)
    .setSuppressingResultMatcher(MaterialDragHandleTouchTarget(dragHandleDescription))

private class MaterialDragHandleTouchTarget(private val description: String) : BaseMatcher<Any>() {
    override fun matches(item: Any?): Boolean {
        val result = item as? AccessibilityViewCheckResult ?: return false
        val element = result.element ?: return false
        return result.accessibilityHierarchyCheck == TouchTargetSizeCheck::class.java && element.holdsHandle()
    }

    // Material puts the click on a wrapper and the description on the handle inside it. Only a small target gets this
    // result, so a large container holding a sheet can't be excused by it.
    private fun ViewHierarchyElement.holdsHandle(): Boolean = contentDescription?.toString() == description ||
        (0 until childViewCount).any { getChildView(it).holdsHandle() }

    override fun describeTo(description: Description) {
        description.appendText("TouchTargetSizeCheck on Material's drag handle \"${this.description}\"")
    }
}
