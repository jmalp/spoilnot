// Insights counts (DESIGN 5.7): one document per month, written with increments, ported from StatsChange.
import { dayOfMonth, yearMonthOf, type LocalDate, type YearMonth } from '../time';
import type { DiscardReason } from '../pantry/models';

/**
 * [delta] is the number of batches (negative to undo); the writer sends it one step per write, because the rules let a
 * count move by at most one (DESIGN 8.5). [counter] is the month: the document every count of that month writes to.
 */
export type StatsChange =
  | { type: 'stats.countUsedUp'; month: YearMonth; counter: YearMonth; delta: number }
  | {
      type: 'stats.countThrownAway';
      month: YearMonth;
      counter: YearMonth;
      day: number;
      reason: DiscardReason;
      delta: number;
    };

export const StatsChanges = {
  usedUp: (today: LocalDate, delta = 1): StatsChange => ({
    type: 'stats.countUsedUp',
    month: yearMonthOf(today),
    counter: yearMonthOf(today),
    delta,
  }),
  thrownAway: (today: LocalDate, reason: DiscardReason, delta = 1): StatsChange => ({
    type: 'stats.countThrownAway',
    month: yearMonthOf(today),
    counter: yearMonthOf(today),
    day: dayOfMonth(today),
    reason,
    delta,
  }),
  /** The change that takes [change] back. */
  undo: (change: StatsChange): StatsChange => ({ ...change, delta: -change.delta }),
};
