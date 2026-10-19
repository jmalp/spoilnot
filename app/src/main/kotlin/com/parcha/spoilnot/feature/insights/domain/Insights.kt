package com.parcha.spoilnot.feature.insights.domain

import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow

/**
 * One month of a household's kitchen (DESIGN 5.7): batches used up and thrown away. Counts are of batches (one row
 * per purchase), not quantities, because units differ.
 */
data class MonthStats(
    val month: YearMonth,
    val used: Int = 0,
    val thrown: Map<DiscardReason, Int> = emptyMap(),
    /** Day of the month to batches thrown away that day, for the streak. */
    val thrownByDay: Map<Int, Int> = emptyMap(),
) {
    val thrownTotal: Int get() = thrown.values.sum()
    val total: Int get() = used + thrownTotal

    /** Share of finished batches that were used, 0 to 1; null when nothing was finished. */
    val usedShare: Float? get() = if (total == 0) null else used.toFloat() / total
}

/** Writes to households/{hid}/stats/{yyyy-MM}. [delta] is +1, or -1 to undo. */
sealed interface StatsChange : HouseholdChange {
    val month: YearMonth

    data class CountUsedUp(override val month: YearMonth, val delta: Int) : StatsChange

    data class CountThrownAway(
        override val month: YearMonth,
        val day: Int,
        val reason: DiscardReason,
        val delta: Int,
    ) : StatsChange

    companion object {
        fun usedUp(today: LocalDate, delta: Int = 1) = CountUsedUp(YearMonth.from(today), delta)

        fun thrownAway(today: LocalDate, reason: DiscardReason, delta: Int = 1) =
            CountThrownAway(YearMonth.from(today), today.dayOfMonth, reason, delta)

        /** The change that takes [change] back. */
        fun undo(change: StatsChange): StatsChange = when (change) {
            is CountUsedUp -> change.copy(delta = -change.delta)
            is CountThrownAway -> change.copy(delta = -change.delta)
        }
    }
}

interface StatsRepository {
    /** The months from [from] on that have any counts, oldest first. Months without a document are left out. */
    fun observeMonths(householdId: String, from: YearMonth): Flow<List<MonthStats>>
}

/** How this month went, for the friendly line on the Home card and Insights. */
enum class Mood { NothingYet, Great, Good, Okay, Low }

/** What the Insights screen and the Home card show. */
data class InsightsSummary(
    val current: MonthStats,
    val mood: Mood,
    /** The last [Insights.MONTHS] months, oldest first, with empty months filled in. */
    val months: List<MonthStats>,
    /** Thrown-away batches over those months by reason, most first; reasons with none are left out. */
    val reasons: List<Pair<DiscardReason, Int>>,
    /**
     * Days since the last batch was thrown away (0 when it was today); null when nothing was finished in the period.
     * When nothing was thrown away at all, it counts the days since the first month with counts began, today
     * included.
     */
    val daysWithoutWaste: Int?,
    /** True when [daysWithoutWaste] counts from the start of the tracked period (nothing thrown away yet). */
    val neverThrown: Boolean,
    /** The month with the highest used share (at least [Insights.BEST_MONTH_MIN] batches), if two months qualify. */
    val bestMonth: MonthStats?,
)

/** Pure calculations for Insights (DESIGN 5.7). */
object Insights {
    /** The chart covers this many months, including the current one. */
    const val MONTHS = 6

    /** A month needs this many finished batches to be the best month. */
    const val BEST_MONTH_MIN = 5

    /** The first month the Insights screen reads. */
    fun firstMonth(today: LocalDate): YearMonth = YearMonth.from(today).minusMonths((MONTHS - 1).toLong())

    fun mood(stats: MonthStats): Mood {
        val share = stats.usedShare ?: return Mood.NothingYet
        return when {
            share >= 0.9f -> Mood.Great
            share >= 0.75f -> Mood.Good
            share >= 0.5f -> Mood.Okay
            else -> Mood.Low
        }
    }

    fun summary(stored: List<MonthStats>, today: LocalDate): InsightsSummary {
        val thisMonth = YearMonth.from(today)
        val byMonth = stored.associateBy { it.month }
        val months = (MONTHS - 1 downTo 0).map { back ->
            val month = thisMonth.minusMonths(back.toLong())
            byMonth[month] ?: MonthStats(month)
        }
        val current = months.last()
        val reasons = DiscardReason.entries
            .map { reason -> reason to months.sumOf { it.thrown[reason] ?: 0 } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        val (days, never) = daysWithoutWaste(months, today)
        val qualifying = months.filter { it.total >= BEST_MONTH_MIN }
        val best = if (qualifying.size >= 2) qualifying.maxBy { it.usedShare ?: 0f } else null
        return InsightsSummary(current, mood(current), months, reasons, days, never, best)
    }

    private fun daysWithoutWaste(months: List<MonthStats>, today: LocalDate): Pair<Int?, Boolean> {
        val lastThrown = months
            .flatMap { m -> m.thrownByDay.filterValues { it > 0 }.keys.map { day -> m.month.atDay(day) } }
            .filter { !it.isAfter(today) }
            .maxOrNull()
        if (lastThrown != null) return ChronoUnit.DAYS.between(lastThrown, today).toInt() to false
        val start = months.firstOrNull { it.total > 0 }?.month?.atDay(1) ?: return null to false
        return ChronoUnit.DAYS.between(start, today).toInt() + 1 to true
    }
}
