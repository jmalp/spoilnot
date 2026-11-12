package com.parcha.spoilnot.core.ui

import androidx.compose.ui.platform.AccessibilityManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** DESIGN 5.2: Undo stays 8 s, longer when the phone's accessibility timeout asks for more (review A11Y-02). */
class UndoWindowTest {
    private class FakeAccessibility(private val recommended: (Long) -> Long) : AccessibilityManager {
        var asked: List<Any>? = null

        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long {
            asked = listOf(originalTimeoutMillis, containsIcons, containsText, containsControls)
            return recommended(originalTimeoutMillis)
        }
    }

    @Test
    fun `8 seconds when nothing asks for more`() {
        assertEquals(8_000L, undoWindowMillis(FakeAccessibility { it }))
    }

    @Test
    fun `8 seconds without an accessibility manager`() {
        assertEquals(8_000L, undoWindowMillis(null))
    }

    @Test
    fun `follows a longer time to take action`() {
        assertEquals(120_000L, undoWindowMillis(FakeAccessibility { 120_000L }))
    }

    @Test
    fun `never shorter than 8 seconds`() {
        assertEquals(8_000L, undoWindowMillis(FakeAccessibility { 3_000L }))
    }

    @Test
    fun `asks as a snackbar with text and a control`() {
        val fake = FakeAccessibility { it }
        undoWindowMillis(fake)
        assertEquals(listOf<Any>(8_000L, false, true, true), fake.asked)
    }
}
