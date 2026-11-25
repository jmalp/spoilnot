// Ported from PantryItemsTest: one row per purchase.
import { describe, expect, it } from 'vitest';
import { addDays } from '../time';
import { batch, product, TODAY } from './fixtures';
import { DEFAULT_QUERY, ExpiredFold, NeedsAttention, PantryItems, type PantryQuery } from './items';

const products = [
  product('milk'),
  product('rice', { location: 'pantry', category: 'pantry_staples', min: 5 }),
  product('oil', { name: 'Olive oil', min: 1, location: 'pantry' }),
  product('soup', { category: 'cooked' }),
];
const batches = [
  batch('m1', 'milk', { inDays: 1 }),
  batch('m2', 'milk', { inDays: 8, location: 'freezer', addedSecondsAgo: 10 }),
  batch('r1', 'rice', { quantity: 2, inDays: 200, location: 'pantry', addedSecondsAgo: 30 }),
  batch('s1', 'soup', { inDays: -1 }),
  batch('gone', 'milk', { quantity: 0, inDays: 2 }),
];
const keys = (query: Partial<PantryQuery> = {}) =>
  PantryItems.build(products, batches, { ...DEFAULT_QUERY, ...query }, TODAY)
    .flatMap((g) => g.items)
    .map((i) => i.key);

describe('PantryItems', () => {
  it('each purchase is its own row, soonest first, out staples last, empty batches hidden', () => {
    expect(keys()).toEqual(['s1', 'm1', 'm2', 'r1', 'out-oil']);
  });

  it('filters work per purchase', () => {
    expect(keys({ filter: 'freezer' })).toEqual(['m2']);
    // Needs attention includes the expired (after v1, R8).
    expect(keys({ filter: 'needsAttention' })).toEqual(['s1', 'm1']);
    expect(keys({ filter: 'expired' })).toEqual(['s1']);
    expect(keys({ filter: 'lowStock' })).toEqual(['r1', 'out-oil']);
    expect(keys({ filter: 'pantry' })).toEqual(['r1', 'out-oil']);
  });

  it('search, sort and grouping', () => {
    expect(keys({ search: 'MILK' })).toEqual(['m1', 'm2']);
    expect(keys({ sort: 'name' })).toEqual(['m1', 'm2', 'out-oil', 'r1', 's1']);
    expect(keys({ sort: 'recentlyAdded' })).toEqual(['m1', 's1', 'm2', 'r1', 'out-oil']);
    const groups = PantryItems.build(products, batches, { ...DEFAULT_QUERY, grouping: 'category' }, TODAY);
    expect(groups.some((g) => g.category === 'cooked' && g.items.length === 1 && g.items[0]!.key === 's1')).toBe(true);
  });

  it('three or more expired fold into a header while browsing, not when searching or filtering to Expired', () => {
    const old = [1, 2, 3].map((n) => batch(`x${n}`, 'milk', { inDays: -n }));
    const groups = PantryItems.build(products, [...batches, ...old], DEFAULT_QUERY, TODAY);
    const fold = ExpiredFold.of(groups, DEFAULT_QUERY)!;
    expect(fold.expired.map((i) => i.key)).toEqual(['x3', 'x2', 'x1', 's1']);
    expect(fold.rest.flatMap((g) => g.items).map((i) => i.key)).toEqual(['m1', 'm2', 'r1', 'out-oil']);
    expect(ExpiredFold.of(groups, { ...DEFAULT_QUERY, search: 'milk' })).toBeNull();
    expect(ExpiredFold.of(groups, { ...DEFAULT_QUERY, filter: 'expired' })).toBeNull();
    // Two are not a backlog: they stay in the list.
    expect(
      ExpiredFold.of(PantryItems.build(products, [...batches, old[0]!], DEFAULT_QUERY, TODAY), DEFAULT_QUERY),
    ).toBeNull();
  });
});

// Ported from NeedsAttentionTest: one set for Home, the Pantry filter and the digest (digest.test.ts), after v1 (R8).
describe('NeedsAttention', () => {
  const products = [
    product('milk'),
    product('yogurt'),
    product('chips', { name: 'Tortilla chips' }),
    product('dill', { name: 'Fresh dill' }),
    product('rice'),
    product('oil', { name: 'Olive oil', min: 1 }),
  ];
  const batches = [
    batch('m1', 'milk', { inDays: 0 }),
    batch('m2', 'milk', { inDays: 3 }),
    batch('y1', 'yogurt', { inDays: 1 }),
    batch('c1', 'chips', { inDays: -40 }),
    batch('d1', 'dill', { inDays: -1 }),
    batch('r1', 'rice', { inDays: 4 }),
    batch('u1', 'rice', { inDays: null }),
    batch('gone', 'yogurt', { quantity: 0, inDays: 1 }),
  ];

  it('usable first, soonest first, expired apart, and nothing past 3 days, undated, used up or out', () => {
    const set = NeedsAttention.fromPantry(products, batches, TODAY);
    expect(set.usable.map((i) => i.key)).toEqual(['m1', 'y1', 'm2']);
    expect(set.expired.map((i) => i.key)).toEqual(['c1', 'd1']);
    expect(NeedsAttention.size(set)).toBe(5);
  });

  it('the 3-day edge follows the injected today', () => {
    const set = NeedsAttention.fromPantry(products, batches, addDays(TODAY, 1));
    expect(set.usable.map((i) => i.key)).toEqual(['y1', 'm2', 'r1']);
    expect(set.expired.map((i) => i.key)).toEqual(['c1', 'd1', 'm1']);
  });

  it("Pantry's Needs attention filter lists exactly the set, so Home's See all count matches", () => {
    const set = NeedsAttention.fromPantry(products, batches, TODAY);
    const filtered = PantryItems.build(products, batches, { ...DEFAULT_QUERY, filter: 'needsAttention' }, TODAY)
      .flatMap((g) => g.items)
      .map((i) => i.key);
    expect(new Set(filtered)).toEqual(new Set([...set.usable, ...set.expired].map((i) => i.key)));
    expect(filtered).toHaveLength(NeedsAttention.size(set));
  });
});
