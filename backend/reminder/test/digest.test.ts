import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { afterSend, buildDigest, buildDigestWithMeals, cookingFor, DEFAULT_PREFS, type Meal, dueAt, readPrefs, selectItems, stockWindow, type Batch, type UserDoc } from '../src/digest.ts';

const user = (over: Partial<UserDoc> = {}, prefs: Partial<UserDoc['prefs']> = {}): UserDoc => ({
  uid: 'u1',
  householdId: 'h1',
  timezone: 'America/New_York',
  locale: 'en',
  prefs: { ...DEFAULT_PREFS, ...prefs },
  lastDigestDate: null,
  lastExpiredThrough: null,
  ...over,
});

const batch = (productName: string, expiryDate: string, dateType: Batch['dateType'] = 'use_by', quantity = 1): Batch =>
  ({ productName, expiryDate, dateType, quantity });

describe('who is due', () => {
  // 2026-10-12 is EDT (UTC-4): 13:00 UTC is 9:00 local.
  it('is due at or after the chosen time, before 22:00, once per local day', () => {
    assert.equal(dueAt(user(), new Date('2026-10-12T12:59:00Z')), null);
    assert.deepEqual(dueAt(user(), new Date('2026-10-12T13:07:00Z')), { date: '2026-10-12', minutesOfDay: 547 });
    assert.equal(dueAt(user({ lastDigestDate: '2026-10-12' }), new Date('2026-10-12T15:00:00Z')), null);
    assert.equal(dueAt(user(), new Date('2026-10-13T02:00:00Z')), null, '22:00 local is too late');
  });

  it('catches up after missed runs, until 22:00', () => {
    // The 9 AM run never happened; the next one at 4 PM still sends.
    assert.ok(dueAt(user({ lastDigestDate: '2026-10-11' }), new Date('2026-10-12T20:07:00Z')));
  });

  it('respects users on both sides of UTC independently', () => {
    const tokyo = user({ timezone: 'Asia/Tokyo' });
    assert.deepEqual(dueAt(tokyo, new Date('2026-10-12T00:30:00Z')), { date: '2026-10-12', minutesOfDay: 570 });
    // Same instant: 20:30 on 10-11 in New York, where that day's digest already went out.
    assert.equal(dueAt(user({ lastDigestDate: '2026-10-11' }), new Date('2026-10-12T00:30:00Z')), null);
  });

  it('is never due when switched off or without a household', () => {
    assert.equal(dueAt(user({}, { enabled: false }), new Date('2026-10-12T15:00:00Z')), null);
    assert.equal(dueAt(user({ householdId: null }), new Date('2026-10-12T15:00:00Z')), null);
  });

  it('works on the DST change days', () => {
    // 2026-11-01: 9:00 EST is 14:00 UTC; 13:30 UTC is 8:30 EST, not yet due.
    assert.equal(dueAt(user(), new Date('2026-11-01T13:30:00Z')), null);
    assert.ok(dueAt(user(), new Date('2026-11-01T14:07:00Z')));
    // 2026-03-08: 9:00 EDT is 13:00 UTC.
    assert.ok(dueAt(user(), new Date('2026-03-08T13:07:00Z')));
  });

  it('reads prefs with defaults', () => {
    assert.deepEqual(readPrefs(undefined), DEFAULT_PREFS);
    assert.deepEqual(readPrefs({ stages: [3, 9, 0], expiredMode: 'bogus' }), { ...DEFAULT_PREFS, stages: [3, 0] });
  });
});

describe('what is in the digest', () => {
  const today = '2026-10-12';
  const stock = [
    batch('Milk', '2026-10-12'),
    batch('Yogurt', '2026-10-13'),
    batch('Eggs', '2026-10-14'),
    batch('Cheese', '2026-10-15'),
    batch('Ham', '2026-10-16'),
    batch('Soup', '2026-10-10'),
    batch('Bread', '2026-10-11', 'best_before'),
    batch('Gone', '2026-10-13', 'use_by', 0),
  ];

  it('picks stages 3, 2, 1, 0 and expired, skipping empty batches and later dates', () => {
    const items = selectItems(user(), today, stock);
    assert.deepEqual(items.map((i) => [i.name, i.stage]).sort(), [
      ['Bread', 'expired'], ['Cheese', 3], ['Eggs', 2], ['Milk', 0], ['Soup', 'expired'], ['Yogurt', 1],
    ].sort());
  });

  it('respects a subset of stages', () => {
    const items = selectItems(user({}, { stages: [1] }), today, stock);
    assert.deepEqual(items.map((i) => i.name).sort(), ['Bread', 'Soup', 'Yogurt']);
  });

  it('reports expired items once by default, every day in daily mode, never in never mode', () => {
    const reported = user({ lastExpiredThrough: '2026-10-11' });
    assert.deepEqual(selectItems(reported, today, stock).filter((i) => i.stage === 'expired'), []);
    const daily = user({ lastExpiredThrough: '2026-10-11' }, { expiredMode: 'daily' });
    assert.equal(selectItems(daily, today, stock).filter((i) => i.stage === 'expired').length, 2);
    assert.equal(selectItems(user({}, { expiredMode: 'never' }), today, stock).filter((i) => i.stage === 'expired').length, 0);
    // Something that expired after the last report is still reported once.
    const partly = user({ lastExpiredThrough: '2026-10-10' });
    assert.deepEqual(selectItems(partly, today, stock).filter((i) => i.stage === 'expired').map((i) => i.name), ['Bread']);
  });

  it('records the day and the expired items reported up to yesterday', () => {
    assert.deepEqual(afterSend('2027-01-01'), { lastDigestDate: '2027-01-01', lastExpiredThrough: '2026-12-31' });
  });

  it('loads 14 days back and 3 days ahead of every due member', () => {
    assert.deepEqual(stockWindow(['2026-10-13', '2026-10-12']), { lo: '2026-09-28', hi: '2026-10-16' });
  });
});

describe('digest text', () => {
  const today = '2026-10-12';

  it('one item reads as a sentence', () => {
    assert.equal(buildDigest(selectItems(user(), today, [batch('Milk', '2026-10-13')]))?.title, 'Milk expires tomorrow');
    assert.equal(buildDigest(selectItems(user(), today, [batch('Milk', '2026-10-12')]))?.title, 'Milk expires today');
    assert.equal(buildDigest(selectItems(user(), today, [batch('Milk', '2026-10-15')]))?.title, 'Milk expires in 3 days');
    assert.equal(buildDigest(selectItems(user(), today, [batch('Milk', '2026-10-10')]))?.title, 'Milk has expired. Throw it away');
  });

  it('best-before items use softer wording', () => {
    assert.equal(buildDigest(selectItems(user(), today, [batch('Bread', '2026-10-11', 'best_before')]))?.title, 'Bread is past its best before date');
    assert.equal(buildDigest(selectItems(user(), today, [batch('Rice', '2026-10-13', 'best_before')]))?.title, 'Rice is best before tomorrow');
    const both = buildDigest(selectItems(user(), today, [batch('Bread', '2026-10-11', 'best_before'), batch('Milk', '2026-10-11')]));
    assert.equal(both?.body, 'Throw away: Milk. Past best before: Bread.');
  });

  it('several items: a count, then lines in order with up to three names', () => {
    const digest = buildDigest(selectItems(user(), today, [
      batch('Milk', '2026-10-10'),
      batch('Yogurt', '2026-10-13'),
      batch('Eggs', '2026-10-13'),
      batch('Kale', '2026-10-14'),
      batch('Ham', '2026-10-15'),
      batch('Peas', '2026-10-14'),
      batch('Corn', '2026-10-15'),
    ]));
    assert.equal(digest?.title, '7 items need attention');
    assert.equal(digest?.body, 'Throw away: Milk. Tomorrow: Yogurt, Eggs. In 2 to 3 days: Kale, Ham, Peas +1 more.');
  });

  it('the example from DESIGN 5.4', () => {
    const digest = buildDigest(selectItems(user(), today, [
      batch('Milk', '2026-10-11'), batch('Yogurt', '2026-10-13'), batch('Eggs', '2026-10-13'),
    ]));
    assert.deepEqual(digest, { title: '3 items need attention', body: 'Throw away: Milk. Tomorrow: Yogurt, Eggs.' });
  });

  it('two batches of one product count once', () => {
    const digest = buildDigest(selectItems(user(), today, [batch('Milk', '2026-10-13'), batch('Milk', '2026-10-13')]));
    assert.equal(digest?.title, 'Milk expires tomorrow');
  });

  it('nothing to report means no digest', () => {
    assert.equal(buildDigest([]), null);
    assert.equal(buildDigest(selectItems(user(), today, [batch('Rice', '2027-01-01')])), null);
  });
});

describe('meals in the digest (after v1, DESIGN 5.9)', () => {
  const today = '2026-10-12';
  const meal = (title: string, slot: Meal['slot'], over: Partial<Meal> = {}): Meal =>
    ({ date: today, slot, position: 0, title, cooks: ['u1'], cooked: false, ...over });

  it('picks today\'s uncooked meals the user cooks, breakfast first', () => {
    const meals = [
      meal('Tacos', 'dinner'),
      meal('Pancakes', 'breakfast'),
      meal('Soup', 'lunch', { cooked: true }),
      meal('Salad', 'lunch', { cooks: ['u2'] }),
      meal('Curry', 'dinner', { date: '2026-10-13' }),
    ];
    assert.deepEqual(cookingFor(user(), today, meals).map((m) => m.title), ['Pancakes', 'Tacos']);
    assert.deepEqual(cookingFor(user({}, { cooking: false }), today, meals), []);
  });

  it('adds a line after the food, or makes its own digest', () => {
    const items = selectItems(user(), today, [batch('Milk', '2026-10-13')]);
    assert.deepEqual(buildDigestWithMeals(items, [meal('Tacos', 'dinner')]), {
      title: 'Milk expires tomorrow',
      body: 'Tomorrow: Milk. You\'re cooking: Tacos (dinner).',
      mealsOnly: false,
    });
    assert.deepEqual(buildDigestWithMeals([], [meal('Tacos', 'dinner')]), {
      title: 'You\'re cooking Tacos for dinner', body: 'You\'re cooking: Tacos (dinner).', mealsOnly: true,
    });
    assert.equal(buildDigestWithMeals([], [meal('A', 'lunch'), meal('B', 'dinner')])?.title, 'You\'re cooking 2 meals today');
    assert.equal(buildDigestWithMeals([], []), null);
  });

  it('reads the cooking pref with a default of on', () => {
    assert.equal(readPrefs({}).cooking, true);
    assert.equal(readPrefs({ cooking: false }).cooking, false);
  });
});
