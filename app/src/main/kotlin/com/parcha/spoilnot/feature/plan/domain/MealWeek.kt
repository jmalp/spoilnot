package com.parcha.spoilnot.feature.plan.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One day of the plan: its meals by slot, in order. */
data class PlanDay(
    val date: LocalDate,
    val isToday: Boolean,
    val isPast: Boolean,
    val slots: Map<MealSlot, List<PlannedMeal>>,
) {
    val isEmpty: Boolean get() = slots.values.all { it.isEmpty() }
}

/** Week math and the week view (DESIGN 5.9). Pure, so it is tested with a fixed today and week start. */
object MealWeek {
    const val DAYS = 7L

    /** The first day of the week containing [date], for weeks starting on [firstDay] (the phone's region). */
    fun start(date: LocalDate, firstDay: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDay))

    fun end(start: LocalDate): LocalDate = start.plusDays(DAYS - 1)

    fun days(start: LocalDate): List<LocalDate> = (0 until DAYS).map { start.plusDays(it) }

    /** The week starting [start]: every day with its three slots, meals ordered by position, then creation. */
    fun build(start: LocalDate, meals: List<PlannedMeal>, today: LocalDate): List<PlanDay> {
        val byDay = meals.groupBy { it.date }
        return days(start).map { date ->
            val dayMeals = byDay[date].orEmpty()
            PlanDay(
                date = date,
                isToday = date == today,
                isPast = date.isBefore(today),
                slots = MealSlot.entries.associateWith { slot ->
                    dayMeals.filter { it.slot == slot }
                        .sortedWith(compareBy<PlannedMeal> { it.position }.thenBy(nullsLast()) { it.createdAt })
                },
            )
        }
    }

    /** Where a new meal goes in a slot: after the last one. */
    fun nextPosition(slotMeals: List<PlannedMeal>): Long = (slotMeals.maxOfOrNull { it.position } ?: -1L) + 1
}
