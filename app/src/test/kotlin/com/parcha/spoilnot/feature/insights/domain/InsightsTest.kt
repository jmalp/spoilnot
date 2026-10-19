package com.parcha.spoilnot.feature.insights.domain

import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val october = YearMonth.of(2026, 10)

    private fun month(
        month: YearMonth,
        used: Int = 0,
        thrown: Map<DiscardReason, Int> = emptyMap(),
        byDay: Map<Int, Int> = emptyMap(),
    ) = MonthStats(month, used, thrown, byDay)

    @Test
    fun `the share used and the mood follow the month's counts`() {
        assertNull(MonthStats(october).usedShare)
        assertEquals(Mood.NothingYet, Insights.mood(MonthStats(october)))
        assertEquals(Mood.Great, Insights.mood(month(october, used = 9, thrown = mapOf(DiscardReason.Expired to 1))))
        assertEquals(Mood.Good, Insights.mood(month(october, used = 3, thrown = mapOf(DiscardReason.Expired to 1))))
        assertEquals(Mood.Okay, Insights.mood(month(october, used = 1, thrown = mapOf(DiscardReason.Expired to 1))))
        assertEquals(Mood.Low, Insights.mood(month(october, used = 1, thrown = mapOf(DiscardReason.Spoiled to 2))))
        assertEquals(1f, month(october, used = 4).usedShare!!, 0f)
    }

    @Test
    fun `six months oldest first, with empty months filled in`() {
        val summary = Insights.summary(listOf(month(YearMonth.of(2026, 7), used = 3), month(october, used = 2)), today)
        assertEquals((5 downTo 0).map { october.minusMonths(it.toLong()) }, summary.months.map { it.month })
        assertEquals(listOf(0, 0, 3, 0, 0, 2), summary.months.map { it.used })
        assertEquals(2, summary.current.used)
        assertEquals(YearMonth.of(2026, 5), Insights.firstMonth(today))
    }

    @Test
    fun `reasons add up over the months, most first, without empty ones`() {
        val summary = Insights.summary(
            listOf(
                month(YearMonth.of(2026, 9), thrown = mapOf(DiscardReason.Expired to 1, DiscardReason.Other to 1)),
                month(october, thrown = mapOf(DiscardReason.Expired to 2, DiscardReason.Spoiled to 1)),
            ),
            today,
        )
        assertEquals(
            listOf(DiscardReason.Expired to 3, DiscardReason.Spoiled to 1, DiscardReason.Other to 1),
            summary.reasons,
        )
    }

    @Test
    fun `the streak counts days since the last throw away, across months`() {
        val lastMonth = month(YearMonth.of(2026, 9), thrown = mapOf(DiscardReason.Expired to 1), byDay = mapOf(28 to 1))
        val summary = Insights.summary(listOf(lastMonth, month(october, used = 5)), today)
        assertEquals(20, summary.daysWithoutWaste) // September 28 to October 18
        assertFalse(summary.neverThrown)

        val today0 = month(october, thrown = mapOf(DiscardReason.Spoiled to 1), byDay = mapOf(18 to 1, 2 to 0))
        assertEquals(0, Insights.summary(listOf(today0), today).daysWithoutWaste)
    }

    @Test
    fun `with nothing thrown away the streak counts from the first month with counts`() {
        val summary = Insights.summary(listOf(month(YearMonth.of(2026, 9), used = 2)), today)
        assertEquals(48, summary.daysWithoutWaste) // September 1 to October 18, both included
        assertTrue(summary.neverThrown)
        assertNull(Insights.summary(emptyList(), today).daysWithoutWaste)
    }

    @Test
    fun `a throw away dated after today (another time zone) does not end the streak early`() {
        val tomorrow = month(october, thrown = mapOf(DiscardReason.Other to 1), byDay = mapOf(19 to 1, 10 to 1))
        assertEquals(8, Insights.summary(listOf(tomorrow), today).daysWithoutWaste)
    }

    @Test
    fun `the best month needs two months with enough batches`() {
        val sept = month(YearMonth.of(2026, 9), used = 9, thrown = mapOf(DiscardReason.Expired to 1))
        val aug = month(YearMonth.of(2026, 8), used = 4, thrown = mapOf(DiscardReason.Expired to 1))
        val small = month(october, used = 2)
        assertNull(Insights.summary(listOf(sept, small), today).bestMonth)
        assertEquals(sept, Insights.summary(listOf(aug, sept, small), today).bestMonth)
    }

    @Test
    fun `undo reverses a count in the same month`() {
        val counted = StatsChange.thrownAway(LocalDate.of(2026, 10, 31), DiscardReason.Expired)
        assertEquals(StatsChange.CountThrownAway(october, 31, DiscardReason.Expired, -1), StatsChange.undo(counted))
        assertEquals(StatsChange.CountUsedUp(october, -1), StatsChange.undo(StatsChange.usedUp(today)))
    }
}
