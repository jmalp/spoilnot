// Ported from HomeDashboardTest and ActivityFeedTest.
import { describe, expect, it } from 'vitest';
import { batch, product, TODAY } from '../pantry/fixtures';
import type { GroceryItem } from '../grocery/models';
import { NeedsAttention } from '../pantry/items';
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
  it('needs attention lists what can still be used first and counts the expired apart (UX-01)', () => {
    const s = HomeDashboard.summary(pantry, [], [], TODAY);
    expect(s.attention.usable.map((i) => i.name)).toEqual(['Milk', 'Cheese']);
    expect(s.attention.expired.map((i) => i.name)).toEqual(['Yogurt']);
    expect(NeedsAttention.size(s.attention)).toBe(3);
  });

  it('a backlog of expired food does not push out what can still be used', () => {
    const backlog: PantryData = {
      ...pantry,
      products: [...pantry.products, ...[1, 2, 3, 4, 5, 6, 7, 8].map((n) => product(`old${n}`))],
      batches: [...pantry.batches, ...[1, 2, 3, 4, 5, 6, 7, 8].map((n) => batch(`o${n}`, `old${n}`, { inDays: -n }))],
    };
    const s = HomeDashboard.summary(backlog, [], [], TODAY);
    expect(s.attention.usable.map((i) => i.name)).toEqual(['Milk', 'Cheese']);
    expect(s.attention.expired).toHaveLength(9);
  });

  it('a new household is told how to start, not that all is done (UX-07)', () => {
    const empty = HomeDashboard.summary({ products: [], batches: [], fromCache: false }, [], [], TODAY);
    expect(empty.pantryEmpty).toBe(true);
    expect(empty.noStaples).toBe(true);
    const s = HomeDashboard.summary(pantry, [], [], TODAY);
    expect(s.pantryEmpty).toBe(false);
    expect(s.noStaples).toBe(false);
  });

  it('an out-of-stock staple alone is not food in the pantry', () => {
    const s = HomeDashboard.summary(
      { products: [product('oil', { name: 'Olive oil', min: 1 })], batches: [], fromCache: false },
      [],
      [],
      TODAY,
    );
    expect(s.pantryEmpty).toBe(true);
    expect(s.noStaples).toBe(false);
    expect(s.lowStock.map((r) => r.name)).toEqual(['Olive oil']);
  });

  it('low stock lists staples below Keep at least, out of stock first', () => {
    const s = HomeDashboard.summary(pantry, [], [], TODAY);
    expect(s.lowStock.map((r) => r.name)).toEqual(['Olive oil', 'Eggs']);
    expect(s.lowStock.map((r) => r.isOut)).toEqual([true, false]);
  });
});

describe('HomeDashboard grocery count', () => {
  it('is the items still to buy', () => {
    const item = (id: string, checked: boolean): GroceryItem => ({
      id,
      name: id,
      nameKey: id,
      quantity: null,
      unit: null,
      note: null,
      category: null,
      checked,
      checkedBy: null,
      source: 'manual',
      productId: null,
      recipeId: null,
      createdBy: null,
      createdAt: null,
      pendingSync: false,
      aliasOf: null,
    });
    const items = [item('a', false), item('b', true), item('c', false)];
    expect(HomeDashboard.summary(pantry, items, [], TODAY).groceryCount).toBe(2);
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

  it('authors are named from the member list, and someone who left keeps the stored name', () => {
    const events = [
      { ...event('e2', 'discarded'), actorName: 'Alice' },
      { ...event('e1', 'stock_added'), actorUid: 'u9', actorName: 'Former' },
    ];
    const named = ActivityFeed.named(
      events,
      new Map([
        ['u1', 'Sam'],
        ['u2', 'Alice'],
      ]),
    );
    expect(named.map((e) => e.actorName)).toEqual(['Sam', 'Former']);
    expect(ActivityFeed.named(events, new Map())).toEqual(events);
  });

  it('without a clear every event shows', () => {
    const events = [event('e2', 'grocery_added'), event('e1', 'stock_added')];
    expect(ActivityFeed.sinceReset(events)).toEqual(events);
  });
});
