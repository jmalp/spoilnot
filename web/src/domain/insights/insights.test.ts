// Ported from InsightsTest.
import { describe, expect, it } from 'vitest';
import { plusMonths } from '../time';
import { Insights, monthStats, MonthTotals } from './insights';
import { StatsChanges } from './stats';

const today = '2026-10-18';
const october = '2026-10';

describe('Insights', () => {
  it("the share used and the mood follow the month's counts", () => {
    expect(MonthTotals.usedShare(monthStats(october))).toBeNull();
    expect(Insights.mood(monthStats(october))).toBe('nothingYet');
    expect(Insights.mood(monthStats(october, 9, { expired: 1 }))).toBe('great');
    expect(Insights.mood(monthStats(october, 3, { expired: 1 }))).toBe('good');
    expect(Insights.mood(monthStats(october, 1, { expired: 1 }))).toBe('okay');
    expect(Insights.mood(monthStats(october, 1, { spoiled: 2 }))).toBe('low');
    expect(MonthTotals.usedShare(monthStats(october, 4))).toBe(1);
  });

  it('six months oldest first, with empty months filled in', () => {
    const summary = Insights.summary([monthStats('2026-07', 3), monthStats(october, 2)], today);
    expect(summary.months.map((m) => m.month)).toEqual([5, 4, 3, 2, 1, 0].map((back) => plusMonths(october, -back)));
    expect(summary.months.map((m) => m.used)).toEqual([0, 0, 3, 0, 0, 2]);
    expect(summary.current.used).toBe(2);
    expect(Insights.firstMonth(today)).toBe('2026-05');
  });

  it('reasons add up over the months, most first, without empty ones', () => {
    const summary = Insights.summary(
      [monthStats('2026-09', 0, { expired: 1, other: 1 }), monthStats(october, 0, { expired: 2, spoiled: 1 })],
      today,
    );
    expect(summary.reasons).toEqual([
      ['expired', 3],
      ['spoiled', 1],
      ['other', 1],
    ]);
  });

  it('the streak counts days since the last throw away, across months', () => {
    const lastMonth = monthStats('2026-09', 0, { expired: 1 }, { 28: 1 });
    const summary = Insights.summary([lastMonth, monthStats(october, 5)], today);
    expect(summary.daysWithoutWaste).toBe(20); // September 28 to October 18
    expect(summary.neverThrown).toBe(false);

    const today0 = monthStats(october, 0, { spoiled: 1 }, { 18: 1, 2: 0 });
    expect(Insights.summary([today0], today).daysWithoutWaste).toBe(0);
  });

  it('with nothing thrown away the streak counts from the first month with counts', () => {
    const summary = Insights.summary([monthStats('2026-09', 2)], today);
    expect(summary.daysWithoutWaste).toBe(48); // September 1 to October 18, both included
    expect(summary.neverThrown).toBe(true);
    expect(Insights.summary([], today).daysWithoutWaste).toBeNull();
  });

  it('a throw away dated after today (another time zone) does not end the streak early', () => {
    const tomorrow = monthStats(october, 0, { other: 1 }, { 19: 1, 10: 1 });
    expect(Insights.summary([tomorrow], today).daysWithoutWaste).toBe(8);
  });

  it('the best month needs two months with enough batches', () => {
    const sept = monthStats('2026-09', 9, { expired: 1 });
    const aug = monthStats('2026-08', 4, { expired: 1 });
    const small = monthStats(october, 2);
    expect(Insights.summary([sept, small], today).bestMonth).toBeNull();
    expect(Insights.summary([aug, sept, small], today).bestMonth).toEqual(sept);
  });

  it('undo reverses a count in the same month', () => {
    const counted = StatsChanges.thrownAway('2026-10-31', 'expired');
    expect(StatsChanges.undo(counted)).toEqual({
      type: 'stats.countThrownAway',
      month: october,
      day: 31,
      reason: 'expired',
      delta: -1,
    });
    expect(StatsChanges.undo(StatsChanges.usedUp(today))).toEqual({
      type: 'stats.countUsedUp',
      month: october,
      delta: -1,
    });
  });
});
