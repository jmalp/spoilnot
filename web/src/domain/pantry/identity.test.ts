import { describe, expect, it } from 'vitest';
import { batch, product, TODAY } from './fixtures';
import { keepUnchanged, sameValue } from './identity';
import { PantryItems } from './items';

describe('sameValue', () => {
  it('compares plain data all the way down', () => {
    expect(sameValue({ a: 1, b: [1, { c: null }] }, { a: 1, b: [1, { c: null }] })).toBe(true);
    expect(sameValue({ a: 1 }, { a: 2 })).toBe(false);
    expect(sameValue({ a: 1 }, { a: 1, b: undefined })).toBe(false);
    expect(sameValue([1, 2], [1, 2, 3])).toBe(false);
    expect(sameValue([1], { 0: 1 })).toBe(false);
    expect(sameValue(Number.NaN, Number.NaN)).toBe(true);
  });
});

describe('keepUnchanged', () => {
  const products = [product('milk'), product('rice', { location: 'pantry', min: 5 })];
  const before = PantryItems.all(products, [batch('m1', 'milk'), batch('r1', 'rice', { quantity: 2 })], TODAY);

  it("keeps the previous item when a new snapshot brings the same data, and takes the new one when it doesn't", () => {
    // Every snapshot maps every document into new objects.
    const next = PantryItems.all(products, [batch('m1', 'milk'), batch('r1', 'rice', { quantity: 3 })], TODAY);
    expect(next[0]).not.toBe(before[0]);
    const kept = keepUnchanged(before, next);
    expect(kept[0]).toBe(before[0]);
    expect(kept[1]).toBe(next[1]);
    expect(kept[1]).not.toBe(before[1]);
  });

  it('takes new items as they are, in the new order', () => {
    const next = PantryItems.all(products, [batch('m2', 'milk'), batch('m1', 'milk')], TODAY);
    const kept = keepUnchanged(before, next);
    expect(kept.map((i) => i.key)).toEqual(['m2', 'm1', 'out-rice']);
    expect(kept[1]).toBe(before[0]);
  });
});
