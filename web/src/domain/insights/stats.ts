// Insights counts (DESIGN 5.7): one document per month, written with increments, ported from StatsChange.
import { dayOfMonth, yearMonthOf, type LocalDate, type YearMonth } from '../time';
import type { DiscardReason } from '../pantry/models';

export type StatsChange =
  | { type: 'stats.countUsedUp'; month: YearMonth; delta: number }
  | { type: 'stats.countThrownAway'; month: YearMonth; day: number; reason: DiscardReason; delta: number };

export const StatsChanges = {
  usedUp: (today: LocalDate, delta = 1): StatsChange => ({
    type: 'stats.countUsedUp',
    month: yearMonthOf(today),
    delta,
  }),
  thrownAway: (today: LocalDate, reason: DiscardReason, delta = 1): StatsChange => ({
    type: 'stats.countThrownAway',
    month: yearMonthOf(today),
    day: dayOfMonth(today),
    reason,
    delta,
  }),
  /** The change that takes [change] back. */
  undo: (change: StatsChange): StatsChange => ({ ...change, delta: -change.delta }),
};
