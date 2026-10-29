// Ported from HomeDashboardTest and ActivityFeedTest.
import { describe, expect, it } from 'vitest';
import { batch, product, TODAY } from '../pantry/fixtures';
import type { PantryData } from '../pantry/models';
import { ActivityFeed, HomeDashboard, type ActivityEvent, type ActivityType } from './models';

const pantry: PantryData = {
  products: [
    product('milk'),
    product('yogurt'),
    product('rice'),
    product('eggs', { min: 6 }),
    product('oil', { name: 'Olive oil', min: 1 }),
    product('cheese'),
  ],
  batches: [
    batch('b1', 'milk', { inDays: 1 }),
    batch('b2', 'yogurt', { inDays: -2 }),
    batch('b3', 'rice', { inDays: 90 }),
    batch('b4', 'eggs', { quantity: 2, inDays: 20 }),
    batch('b5', 'cheese', { inDays: 3 }),
    batch('b6', 'cheese', { inDays: 10 }),
  ],
  fromCache: false,
};

describe('HomeDashboard', () => {
  it('needs attention is expired and expiring within 3 days, soonest first', () => {
    const s = HomeDashboard.summary(pantry, [], TODAY);
    expect(s.attention.map((r) => r.name)).toEqual(['Yogurt', 'Milk', 'Cheese']);
    expect(s.expiredCount).toBe(1);
  });

  it('low stock lists staples below Keep at least, out of stock first', () => {
    const s = HomeDashboard.summary(pantry, [], TODAY);
    expect(s.lowStock.map((r) => r.name)).toEqual(['Olive oil', 'Eggs']);
    expect(s.lowStock.map((r) => r.isOut)).toEqual([true, false]);
  });
});

describe('ActivityFeed', () => {
  const event = (id: string, type: ActivityType): ActivityEvent => ({
    id,
    type,
    actorUid: 'u1',
    actorName: 'Sam',
    name: null,
    quantity: null,
    reason: null,
    listName: null,
    count: null,
    createdAt: 0,
  });

  it('events from before the newest clear are hidden', () => {
    const events = [
      event('e5', 'stock_added'),
      event('e4', 'household_reset'),
      event('e3', 'discarded'),
      event('e2', 'household_reset'),
      event('e1', 'stock_added'),
    ];
    expect(ActivityFeed.sinceReset(events).map((e) => e.id)).toEqual(['e5', 'e4']);
  });

  it('without a clear every event shows', () => {
    const events = [event('e2', 'grocery_added'), event('e1', 'stock_added')];
    expect(ActivityFeed.sinceReset(events)).toEqual(events);
  });
});
