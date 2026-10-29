import { describe, expect, it } from 'vitest';
import { monthName, percent } from '../insights/labels';
import type { ActivityEvent } from '../../domain/home/models';
import { activityText, relativeTime } from './labels';

describe('relativeTime', () => {
  const now = new Date(2026, 9, 12, 15, 0).getTime();
  const at = (days: number, hours = 0, minutes = 0) => new Date(2026, 9, 12 - days, 15 - hours, -minutes).getTime();

  it('reads like Recent activity in the app', () => {
    expect(relativeTime(now - 30_000, now, 'en')).toBe('Just now');
    expect(relativeTime(at(0, 0, 1), now, 'en')).toBe('1 minute ago');
    expect(relativeTime(at(0, 0, 59), now, 'en')).toBe('59 minutes ago');
    expect(relativeTime(at(0, 2), now, 'en')).toBe('2 hours ago');
    expect(relativeTime(at(1), now, 'en')).toBe('Yesterday');
    expect(relativeTime(at(3), now, 'en')).toBe('3 days ago');
    expect(relativeTime(at(8), now, 'en')).toBe('Oct 4, 2026');
  });
});

describe('insights labels', () => {
  it('percent never rounds up to 100 while something was thrown away', () => {
    expect(percent(12 / 17)).toBe(71);
    expect(percent(0.996)).toBe(99);
    expect(percent(1)).toBe(100);
    expect(percent(null)).toBe(0);
  });

  it('month names add the year outside this year', () => {
    expect(monthName('2026-09', 'long', '2026-10')).toBe('September');
    expect(monthName('2025-12', 'long', '2026-01')).toBe(`${monthName('2025-12', 'long', '2025-12')} 2025`);
  });
});

describe('activityText', () => {
  const event = (over: Partial<ActivityEvent>): ActivityEvent => ({
    id: 'e',
    type: 'stock_added',
    actorUid: 'u2',
    actorName: 'Sam',
    name: 'Eggs',
    quantity: null,
    reason: null,
    listName: null,
    count: null,
    createdAt: 0,
    ...over,
  });

  it('names the member, or You for the signed-in one', () => {
    expect(activityText(event({}), 'u1')).toBe('Sam added Eggs to the pantry');
    expect(activityText(event({}), 'u2')).toBe('You added Eggs to the pantry');
  });

  it('says why something was thrown away, and which list got groceries', () => {
    expect(activityText(event({ type: 'discarded', reason: 'spoiled' }), 'u1')).toBe('Sam threw away Eggs (spoiled)');
    expect(activityText(event({ type: 'grocery_added' }), 'u1')).toBe('Sam added Eggs to the grocery list');
    expect(activityText(event({ type: 'grocery_added', name: 'Pancakes', count: 3, listName: 'Costco' }), 'u1')).toBe(
      'Sam added 3 ingredients from Pancakes to Costco',
    );
  });
});
