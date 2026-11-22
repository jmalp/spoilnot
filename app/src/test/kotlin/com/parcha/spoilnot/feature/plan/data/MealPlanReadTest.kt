package com.parcha.spoilnot.feature.plan.data

import com.google.firebase.Timestamp
import com.parcha.spoilnot.core.firebase.fakeSnapshot
import com.parcha.spoilnot.feature.plan.data.FirestoreMealPlanRepository.Companion.toMeal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the app takes from a stored meal (DESIGN 5.9, 8.1). */
class MealPlanReadTest {
    private val meal = mapOf("date" to "2026-11-09", "slot" to "dinner", "title" to "Leftovers")

    @Test
    fun `a Cooked waiting for the server counts as cooked at once`() {
        val estimate = Instant.parse("2026-11-09T18:30:00.123456Z")
        val read = fakeSnapshot("m1", meal, pending = mapOf("cookedAt" to Timestamp(estimate))).toMeal()
        assertEquals(estimate, read?.cookedAt)
    }

    @Test
    fun `a meal not cooked reads as not cooked`() {
        val read = fakeSnapshot("m1", meal + ("cookedAt" to null)).toMeal()
        assertNull(read?.cookedAt)
    }
}
