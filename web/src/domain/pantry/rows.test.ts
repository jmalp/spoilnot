// Ported from PantryListTest (the rows Home uses).
import { describe, expect, it } from 'vitest';
import { batch, product, TODAY } from './fixtures';
import { PantryRows } from './rows';

const products = [
  product('milk'),
  product('yogurt'),
  product('rice', { location: 'pantry', category: 'pantry_staples' }),
  product('oil', { name: 'Olive oil', min: 1, location: 'pantry', category: 'pantry_staples' }),
];
const batches = [
  batch('m1', 'milk', { quantity: 2, inDays: 1 }),
  batch('y1', 'yogurt', { quantity: 1, inDays: 3, addedSecondsAgo: 10 }),
  batch('y2', 'yogurt', { quantity: 2, inDays: 9, addedSecondsAgo: 5 }),
  batch('y3', 'yogurt', { quantity: 1, inDays: 2, location: 'freezer', addedSecondsAgo: 50 }),
  batch('r1', 'rice', { quantity: 1, inDays: null, location: 'pantry', addedSecondsAgo: 100 }),
];

describe('PantryRows', () => {
  it('one row per product with stock plus staples at zero marked out', () => {
    const rows = PantryRows.rows(products, batches, TODAY);
    expect(new Set(rows.map((r) => r.name))).toEqual(new Set(['Milk', 'Yogurt', 'Rice', 'Olive oil']));
    const oil = rows.find((r) => r.name === 'Olive oil')!;
    expect(oil.isOut).toBe(true);
    expect(oil.isLow).toBe(true);
    expect(oil.total).toBe(0);
    expect(oil.status).toBe('noDate');
  });

  it('a row totals its batches and shows the soonest status', () => {
    const yogurt = PantryRows.rows(products, batches, TODAY).find((r) => r.name === 'Yogurt')!;
    expect(yogurt.total).toBe(4);
    expect(yogurt.status).toBe('soon');
    expect(yogurt.batches.map((b) => b.id)).toEqual(['y3', 'y1', 'y2']);
  });

  it('empty batches are hidden and a product with only empty batches disappears', () => {
    const withEmpty = [...batches, batch('m0', 'milk', { quantity: 0 }), batch('x0', 'ghost', { quantity: -1 })];
    const rows = PantryRows.rows(products, withEmpty, TODAY);
    expect(rows.some((r) => r.productId === 'ghost')).toBe(false);
    expect(rows.find((r) => r.name === 'Milk')!.batches.map((b) => b.id)).toEqual(['m1']);
  });

  it('a batch whose product is not loaded yet still shows with its own name', () => {
    const rows = PantryRows.rows([], [batch('b1', 'p9', { name: 'Eggs' })], TODAY);
    expect(rows.map((r) => r.name)).toEqual(['Eggs']);
    expect(rows[0]!.isOut).toBe(false);
  });

  it('pending writes mark the row', () => {
    const rows = PantryRows.rows(products, [{ ...batch('m1', 'milk'), pendingSync: true }], TODAY);
    expect(rows.find((r) => r.name === 'Milk')!.pendingSync).toBe(true);
  });
});
