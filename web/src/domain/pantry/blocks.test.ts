// Ported from PantryBlocksTest: Use first and Everything else in the default view only (DESIGN 19.12).
import { describe, expect, it } from 'vitest';
import { isDefaultView, pantryBlocks, type PantryBlock } from './blocks';
import { batch, product, TODAY } from './fixtures';
import { DEFAULT_QUERY, PantryItems, type PantryQuery } from './items';
import type { StockBatch } from './models';

const products = [
  product('milk'),
  product('rice', { location: 'pantry', category: 'pantry_staples', min: 5 }),
  product('oil', { name: 'Olive oil', min: 1, location: 'pantry' }),
  product('soup', { category: 'cooked' }),
];
const batches = [
  batch('m1', 'milk', { inDays: 1 }),
  batch('m2', 'milk', { inDays: 8, location: 'freezer' }),
  batch('r1', 'rice', { quantity: 2, inDays: 200, location: 'pantry' }),
  batch('s1', 'soup', { inDays: -1 }),
  batch('y1', 'milk', { inDays: 3 }),
  batch('y2', 'milk', { inDays: 4 }),
];
const old = [2, 3, 4].map((n) => batch(`x${n}`, 'milk', { inDays: -n }));

const blocks = (list: StockBatch[] = batches, query: Partial<PantryQuery> = {}, showExpired = false) => {
  const q = { ...DEFAULT_QUERY, ...query };
  return pantryBlocks(PantryItems.build(products, list, q, TODAY), q, showExpired);
};
const keys = (b: PantryBlock) => b.lines.map((l) => l.key);
const headings = (list: PantryBlock[]) => list.map((b) => b.heading?.kind ?? null);

describe('pantryBlocks', () => {
  it("the default view splits into Use first and Everything else, keeping the list's order", () => {
    const list = blocks();
    expect(headings(list)).toEqual(['useFirst', 'everythingElse']);
    // Expired, tomorrow and in 3 days need attention; in 4 days does not (NeedsAttention.DAYS).
    expect(keys(list[0]!)).toEqual(['s1', 'm1', 'y1']);
    expect(keys(list[1]!)).toEqual(['y2', 'm2', 'r1', 'out-oil']);
    expect(list.map((b) => b.key)).toEqual(['use-first', 'everything-else']);
  });

  it('with nothing needing attention there are no headings', () => {
    const list = blocks(batches.filter((b) => ['m2', 'r1', 'y2'].includes(b.id)));
    expect(headings(list)).toEqual([null]);
    expect(keys(list[0]!)).toEqual(['y2', 'm2', 'r1', 'out-oil']);
  });

  it('when everything needs attention there is only Use first', () => {
    const plain = [product('milk'), product('soup')];
    const groups = PantryItems.build(
      plain,
      batches.filter((b) => ['m1', 's1'].includes(b.id)),
      DEFAULT_QUERY,
      TODAY,
    );
    const list = pantryBlocks(groups, DEFAULT_QUERY, false);
    expect(headings(list)).toEqual(['useFirst']);
    expect(keys(list[0]!)).toEqual(['s1', 'm1']);
  });

  it('the expired backlog folds into the first row of Use first and opens in place', () => {
    const folded = blocks([...batches, ...old]);
    expect(folded[0]!.heading?.kind).toBe('useFirst');
    expect(folded[0]!.lines[0]).toEqual({ kind: 'folded', key: 'expired-fold', count: 4 });
    expect(keys(folded[0]!)).toEqual(['expired-fold', 'm1', 'y1']);

    const shown = blocks([...batches, ...old], {}, true);
    expect(keys(shown[0]!)).toEqual(['expired-fold', 'x4', 'x3', 'x2', 's1', 'm1', 'y1']);
    expect(keys(shown[1]!)).toEqual(['y2', 'm2', 'r1', 'out-oil']);
  });

  it("a fold with nothing else to use first is Use first's only row", () => {
    const list = blocks([...old, ...batches.filter((b) => b.id === 'r1')]);
    expect(keys(list[0]!)).toEqual(['expired-fold']);
    expect(list[1]!.heading?.kind).toBe('everythingElse');
  });

  it('any other view keeps its order without the two groups', () => {
    const others: Partial<PantryQuery>[] = [
      { sort: 'name' },
      { sort: 'recentlyAdded' },
      { search: 'milk' },
      { filter: 'fridge' },
      { filter: 'needsAttention' },
    ];
    for (const query of others) {
      expect(isDefaultView({ ...DEFAULT_QUERY, ...query })).toBe(false);
      expect(headings(blocks(batches, query))).toEqual([null]);
    }
    expect(isDefaultView(DEFAULT_QUERY)).toBe(true);
    expect(isDefaultView({ ...DEFAULT_QUERY, search: '  ' })).toBe(true);
  });

  it('grouping keeps its headings, with the fold as a block of its own at the top', () => {
    const list = blocks([...batches, ...old], { grouping: 'location' });
    expect(list[0]!.key).toBe('expired');
    expect(list[0]!.heading).toBeNull();
    expect(keys(list[0]!)).toEqual(['expired-fold']);
    expect(list.slice(1).map((b) => b.heading)).toEqual([
      { kind: 'location', location: 'fridge' },
      { kind: 'location', location: 'freezer' },
      { kind: 'location', location: 'pantry' },
    ]);
    expect(keys(blocks([...batches, ...old], { grouping: 'location' }, true)[0]!)).toEqual([
      'expired-fold',
      'x4',
      'x3',
      'x2',
      's1',
    ]);
    const byCategory = blocks(batches, { grouping: 'category' });
    expect(
      byCategory.some((b) => b.heading?.kind === 'category' && b.heading.category === 'cooked' && keys(b)[0] === 's1'),
    ).toBe(true);
  });

  it('an empty list has no blocks', () => {
    expect(pantryBlocks([], DEFAULT_QUERY, false)).toEqual([]);
  });
});
