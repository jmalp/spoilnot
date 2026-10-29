// Insights (DESIGN 5.7), ported from feature/insights/domain: monthly counts, the mood line, the streak and the best
// month. Counts are of batches (one per purchase), not quantities, because units differ.
import type { Observable } from 'rxjs';
import { DISCARD_REASONS, type DiscardReason } from '../pantry/models';
import { daysBetween, plusMonths, yearMonthOf, type LocalDate, type YearMonth } from '../time';

export type MonthStats = {
  month: YearMonth;
  used: number;
  thrown: Partial<Record<DiscardReason, number>>;
  /** Day of the month to batches thrown away that day, for the streak. */
  thrownByDay: Record<number, number>;
};

export const monthStats = (
  month: YearMonth,
  used = 0,
  thrown: Partial<Record<DiscardReason, number>> = {},
  thrownByDay: Record<number, number> = {},
): MonthStats => ({ month, used, thrown, thrownByDay });

export const MonthTotals = {
  thrown: (m: MonthStats): number => Object.values(m.thrown).reduce((sum, n) => sum + (n ?? 0), 0),
  total: (m: MonthStats): number => m.used + MonthTotals.thrown(m),
  /** Share of finished batches that were used, 0 to 1; null when nothing was finished. */
  usedShare(m: MonthStats): number | null {
    const total = MonthTotals.total(m);
    return total === 0 ? null : m.used / total;
  },
};

export interface StatsRepository {
  /** The months from [from] on that have any counts, oldest first. Months without a document are left out. */
  observeMonths(householdId: string, from: YearMonth): Observable<MonthStats[]>;
}

/** How this month went, for the friendly line on the Home card and Insights. */
export type Mood = 'nothingYet' | 'great' | 'good' | 'okay' | 'low';

/** What the Insights screen and the Home card show. */
export type InsightsSummary = {
  current: MonthStats;
  mood: Mood;
  /** The last [Insights.MONTHS] months, oldest first, with empty months filled in. */
  months: MonthStats[];
  /** Thrown-away batches over those months by reason, most first; reasons with none are left out. */
  reasons: [DiscardReason, number][];
  /**
   * Days since the last batch was thrown away (0 when it was today); null when nothing was finished in the period.
   * When nothing was thrown away at all, it counts the days since the first month with counts began, today included.
   */
  daysWithoutWaste: number | null;
  /** True when [daysWithoutWaste] counts from the start of the tracked period (nothing thrown away yet). */
  neverThrown: boolean;
  /** The month with the highest used share (at least [Insights.BEST_MONTH_MIN] batches), if two months qualify. */
  bestMonth: MonthStats | null;
};

const dayOf = (month: YearMonth, day: number): LocalDate => `${month}-${String(day).padStart(2, '0')}`;

export const Insights = {
  /** The chart covers this many months, including the current one. */
  MONTHS: 6,
  /** A month needs this many finished batches to be the best month. */
  BEST_MONTH_MIN: 5,

  /** The first month the Insights screen reads. */
  firstMonth: (today: LocalDate): YearMonth => plusMonths(yearMonthOf(today), -(Insights.MONTHS - 1)),

  mood(stats: MonthStats): Mood {
    const share = MonthTotals.usedShare(stats);
    if (share === null) return 'nothingYet';
    if (share >= 0.9) return 'great';
    if (share >= 0.75) return 'good';
    if (share >= 0.5) return 'okay';
    return 'low';
  },

  summary(stored: MonthStats[], today: LocalDate): InsightsSummary {
    const thisMonth = yearMonthOf(today);
    const byMonth = new Map(stored.map((m) => [m.month, m]));
    const months = Array.from({ length: Insights.MONTHS }, (_, i) => {
      const month = plusMonths(thisMonth, i - (Insights.MONTHS - 1));
      return byMonth.get(month) ?? monthStats(month);
    });
    const current = months[months.length - 1]!;
    const reasons = DISCARD_REASONS.map((reason): [DiscardReason, number] => [
      reason,
      months.reduce((sum, m) => sum + (m.thrown[reason] ?? 0), 0),
    ])
      .filter(([, n]) => n > 0)
      .sort((a, b) => b[1] - a[1]);
    const [days, never] = daysWithoutWaste(months, today);
    const qualifying = months.filter((m) => MonthTotals.total(m) >= Insights.BEST_MONTH_MIN);
    const best =
      qualifying.length >= 2
        ? qualifying.reduce((a, b) => ((MonthTotals.usedShare(b) ?? 0) > (MonthTotals.usedShare(a) ?? 0) ? b : a))
        : null;
    return {
      current,
      mood: Insights.mood(current),
      months,
      reasons,
      daysWithoutWaste: days,
      neverThrown: never,
      bestMonth: best,
    };
  },
};

function daysWithoutWaste(months: MonthStats[], today: LocalDate): [number | null, boolean] {
  const lastThrown = months
    .flatMap((m) =>
      Object.entries(m.thrownByDay)
        .filter(([, n]) => n > 0)
        .map(([day]) => dayOf(m.month, Number(day))),
    )
    .filter((d) => d <= today)
    .sort()
    .pop();
  if (lastThrown !== undefined) return [daysBetween(lastThrown, today), false];
  const first = months.find((m) => MonthTotals.total(m) > 0);
  if (!first) return [null, false];
  return [daysBetween(dayOf(first.month, 1), today) + 1, true];
}
