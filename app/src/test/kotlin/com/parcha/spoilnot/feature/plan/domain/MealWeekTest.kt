package com.parcha.spoilnot.feature.plan.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealWeekTest {
    private val thursday = LocalDate.of(2026, 10, 29)

    internal fun meal(
        id: String,
        date: LocalDate = thursday,
        slot: MealSlot = MealSlot.Dinner,
        position: Long = 0,
        createdSecond: Long = 0,
    ) = PlannedMeal(
        id = id, date = date, slot = slot, position = position, recipeId = null, title = id, servings = null,
        cooks = emptyList(), note = null, createdAt = Instant.ofEpochSecond(createdSecond),
    )

    @Test
    fun `the week starts on the phone's first day`() {
        assertEquals(LocalDate.of(2026, 10, 26), MealWeek.start(thursday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 10, 25), MealWeek.start(thursday, DayOfWeek.SUNDAY))
        // A week start day is its own week's start.
        assertEquals(thursday, MealWeek.start(thursday, DayOfWeek.THURSDAY))
        assertEquals(LocalDate.of(2026, 11, 1), MealWeek.end(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `seven days, each with three slots, meals ordered by position then creation`() {
        val start = LocalDate.of(2026, 10, 26)
        val meals = listOf(
            meal("late", position = 1),
            meal("first", position = 0, createdSecond = 5),
            meal("tie", position = 0, createdSecond = 9),
            meal("lunch", slot = MealSlot.Lunch),
            meal("next week", date = LocalDate.of(2026, 11, 2)),
        )
        val days = MealWeek.build(start, meals, today = thursday)
        assertEquals(7, days.size)
        assertEquals((26..31).map { LocalDate.of(2026, 10, it) } + LocalDate.of(2026, 11, 1), days.map { it.date })
        val day = days.single { it.date == thursday }
        assertEquals(listOf("first", "tie", "late"), day.slots.getValue(MealSlot.Dinner).map { it.id })
        assertEquals(listOf("lunch"), day.slots.getValue(MealSlot.Lunch).map { it.id })
        assertTrue(day.slots.getValue(MealSlot.Breakfast).isEmpty())
        assertTrue(day.isToday)
        assertTrue(days.first().isPast)
        assertFalse(days.last().isPast)
        assertTrue(days.last().isEmpty)
        assertEquals(MealSlot.entries.toList(), day.slots.keys.toList())
    }

    @Test
    fun `recipes planned from today on, cooked or not, are the ones Use it up leaves out`() {
        val meals = listOf(
            meal("yesterday", date = thursday.minusDays(1)).copy(recipeId = "r-old"),
            meal("tonight").copy(recipeId = "r-tacos", cookedAt = Instant.EPOCH),
            meal("saturday", date = thursday.plusDays(2)).copy(recipeId = "r-curry"),
            meal("leftovers", date = thursday.plusDays(1)),
        )
        assertEquals(setOf("r-tacos", "r-curry"), MealWeek.plannedFrom(meals, thursday))
    }

    @Test
    fun `a new meal goes after the last one in its slot`() {
        assertEquals(0, MealWeek.nextPosition(emptyList()))
        assertEquals(4, MealWeek.nextPosition(listOf(meal("a", position = 3), meal("b", position = 1))))
    }

    @Test
    fun `a day's meals run from breakfast to dinner`() {
        val day = MealWeek.build(
            thursday,
            listOf(
                meal("d", slot = MealSlot.Dinner),
                meal("b", slot = MealSlot.Breakfast),
                meal("l", slot = MealSlot.Lunch),
            ),
            thursday,
        ).first()
        assertEquals(listOf("b", "l", "d"), day.meals.map { it.id })
    }

    @Test
    fun `copying a week shifts the days, keeps the plan, forgets Cooked and skips what is already there`() {
        val lastStart = LocalDate.of(2026, 10, 19)
        val thisStart = LocalDate.of(2026, 10, 26)
        val previous = listOf(
            meal("a", date = lastStart.plusDays(3), position = 1).copy(
                title = "Tacos",
                recipeId = "r1",
                servings = 4,
                cooks = listOf("u1"),
                note = "Spicy",
                cookedAt = Instant.EPOCH,
                cookedBy = "u1",
            ),
            meal("b", date = lastStart.plusDays(3), position = 0).copy(title = "Soup"),
            meal("c", date = lastStart, slot = MealSlot.Lunch).copy(title = "Leftovers"),
        )
        // This week already has Soup on Thursday dinner and something else in that slot.
        val existing = listOf(
            meal("x", date = thursday, position = 0).copy(title = "soup"),
            meal("y", date = thursday, position = 5).copy(title = "Pizza"),
        )
        val copies = MealWeek.copy(previous, lastStart, thisStart, existing)
        assertEquals(listOf("Leftovers", "Tacos"), copies.map { it.title })
        val tacos = copies.last()
        assertEquals(thursday to 6L, tacos.date to tacos.position)
        assertEquals(
            listOf("r1", 4, listOf("u1"), "Spicy"),
            listOf(tacos.recipeId, tacos.servings, tacos.cooks, tacos.note),
        )
        assertEquals(null to null, tacos.cookedAt to tacos.cookedBy)
        assertEquals(thisStart, copies.first().date)
        // Copying again adds nothing.
        assertTrue(MealWeek.copy(previous, lastStart, thisStart, existing + copies).isEmpty())
    }

    @Test
    fun `copying counts meals of the same name in a slot`() {
        // LOGIC-14: two "Leftovers" at Monday lunch last week (for two people) stay two.
        val lastStart = LocalDate.of(2026, 10, 19)
        val thisStart = LocalDate.of(2026, 10, 26)
        val previous = listOf(
            meal("a", date = lastStart, slot = MealSlot.Lunch, position = 0).copy(title = "Leftovers"),
            meal("b", date = lastStart, slot = MealSlot.Lunch, position = 1).copy(title = "leftovers "),
            meal("c", date = lastStart, slot = MealSlot.Lunch, position = 2).copy(title = "Soup"),
        )
        val copies = MealWeek.copy(previous, lastStart, thisStart, emptyList())
        assertEquals(listOf("Leftovers", "leftovers ", "Soup"), copies.map { it.title })
        assertEquals(listOf(0L, 1L, 2L), copies.map { it.position })
        // One is already there: only the second is copied, after it.
        val one = meal("x", date = thisStart, slot = MealSlot.Lunch).copy(title = "Leftovers")
        val again = MealWeek.copy(previous, lastStart, thisStart, listOf(one))
        assertEquals(listOf("leftovers " to 1L, "Soup" to 2L), again.map { it.title to it.position })
        // Copying twice adds nothing.
        assertTrue(MealWeek.copy(previous, lastStart, thisStart, copies).isEmpty())
    }
}
