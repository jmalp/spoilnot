import { describe, expect, it } from 'vitest';
import { addDays, addMonths, daysBetween, isLocalDate, plusMonths, today } from './time';

describe('time', () => {
  it('adds days and months by the calendar', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28');
    expect(addMonths('2028-01-31', 1)).toBe('2028-02-29');
    expect(addMonths('2026-11-15', 2)).toBe('2027-01-15');
    expect(daysBetween('2026-03-07', '2026-03-09')).toBe(2); // across US daylight saving start
    expect(plusMonths('2026-10', -5)).toBe('2026-05');
    expect(plusMonths('2026-11', 3)).toBe('2027-02');
  });

  it('checks stored dates', () => {
    expect(isLocalDate('2026-02-28')).toBe(true);
    expect(isLocalDate('2026-02-30')).toBe(false);
    expect(isLocalDate('20261012')).toBe(false);
  });

  it('today follows the phone calendar of the clock', () => {
    expect(today({ now: () => new Date(2026, 9, 12, 23, 30).getTime() })).toBe('2026-10-12');
  });
});
