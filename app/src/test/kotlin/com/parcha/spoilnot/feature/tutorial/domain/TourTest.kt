package com.parcha.spoilnot.feature.tutorial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TourTest {
    @Test
    fun `Next walks every step in order and ends after the last`() {
        var step: TourStep? = Tour.first
        val seen = mutableListOf<TourStep>()
        while (step != null) {
            seen += step
            step = Tour.next(step, TourEvent.Next)
        }
        assertEquals(TourStep.entries.toList(), seen)
    }

    @Test
    fun `opening the highlighted tab follows along, another tab does not`() {
        assertEquals(TourStep.AddItem, Tour.next(TourStep.OpenPantry, TourEvent.TabOpened(TourTab.Pantry)))
        assertEquals(TourStep.OpenPantry, Tour.next(TourStep.OpenPantry, TourEvent.TabOpened(TourTab.Groceries)))
        // A step without a tab to open ignores tab changes.
        assertEquals(TourStep.Home, Tour.next(TourStep.Home, TourEvent.TabOpened(TourTab.Pantry)))
    }

    @Test
    fun `tapping the highlighted button follows along, only where the step says so`() {
        assertEquals(TourStep.PantryRows, Tour.next(TourStep.AddItem, TourEvent.TargetTapped(TourTarget.AddItem)))
        assertEquals(TourStep.AddItem, Tour.next(TourStep.AddItem, TourEvent.TargetTapped(TourTarget.QuickAdd)))
        // Typing in Quick add is not "done with this step": Next is.
        assertEquals(TourStep.QuickAdd, Tour.next(TourStep.QuickAdd, TourEvent.TargetTapped(TourTarget.QuickAdd)))
    }

    @Test
    fun `skip ends the tour from any step`() {
        TourStep.entries.forEach { assertNull(Tour.next(it, TourEvent.Skip)) }
    }

    @Test
    fun `positions count the steps after the welcome card`() {
        assertNull(Tour.position(TourStep.Welcome))
        assertEquals(1, Tour.position(TourStep.Home))
        assertEquals(Tour.countedSteps, Tour.position(TourStep.entries.last()))
    }

    @Test
    fun `every step that points at a tab button starts on another tab`() {
        TourStep.entries.filter { it.advanceOnTab != null }.forEach { assertEquals(true, it.tab != it.advanceOnTab) }
    }
}
