package com.parcha.spoilnot.feature.plan.domain

import com.parcha.spoilnot.feature.pantry.domain.NameKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One day of the plan: its meals by slot, in order. */
data class PlanDay(
    val date: LocalDate,
    val isToday: Boolean,
    val isPast: Boolean,
    val slots: Map<MealSlot, List<PlannedMeal>>,
) {
    val isEmpty: Boolean get() = slots.values.all { it.isEmpty() }

    /** Every meal of the day, breakfast first (Home's Today card). */
    val meals: List<PlannedMeal> get() = MealSlot.entries.flatMap { slots[it].orEmpty() }
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

    /**
     * Copy last week (DESIGN 5.9): [previous] meals moved by whole weeks from [from] to [to], keeping recipe, title,
     * servings, cooks and note but not Cooked. A meal already in [existing] on the same day and slot with the same
     * name is skipped, so copying twice adds nothing. Copies go after the meals already in their slot, in their old
     * order. Ids are left empty for the service to fill.
     */
    fun copy(
        previous: List<PlannedMeal>,
        from: LocalDate,
        to: LocalDate,
        existing: List<PlannedMeal>,
    ): List<PlannedMeal> {
        val shift = ChronoUnit.DAYS.between(from, to)
        val taken = existing.map { Triple(it.date, it.slot, NameKey.of(it.title)) }.toMutableSet()
        val next = existing.groupBy { it.date to it.slot }
            .mapValues { (_, meals) -> nextPosition(meals) }
            .toMutableMap()
        return previous
            .sortedWith(compareBy<PlannedMeal>({ it.date }, { it.slot }, { it.position }))
            .mapNotNull { meal ->
                val date = meal.date.plusDays(shift)
                if (!taken.add(Triple(date, meal.slot, NameKey.of(meal.title)))) return@mapNotNull null
                val position = next[date to meal.slot] ?: 0L
                next[date to meal.slot] = position + 1
                meal.copy(
                    id = "",
                    date = date,
                    position = position,
                    cookedAt = null,
                    cookedBy = null,
                    createdAt = null,
                    createdBy = null,
                    pendingSync = false,
                )
            }
    }
}
