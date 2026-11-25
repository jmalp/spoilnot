// Ported from ItemEditWatchTest: the Edit window while someone else changes or removes its item (DESIGN 9.2, 9.4).
import { describe, expect, it } from 'vitest';
import { addDays } from '../time';
import { checkEdit, itemEditOf, rebaseEdit, type ItemEditBase } from './edit-watch';
import { batch, product, TODAY } from './fixtures';
import type { PantryData, Product, StockBatch } from './models';

const milk = product('milk', { min: 2 });
const opened: StockBatch = { ...batch('m1', 'milk', { quantity: 3 }), updatedBy: 'alice' };
const base: ItemEditBase = { batch: opened, product: milk };
const data = (batches: StockBatch[], products: Product[] = [milk]): PantryData => ({
  products,
  batches,
  fromCache: false,
});

describe('checkEdit', () => {
  it('nothing the window shows changed', () => {
    expect(checkEdit(base, data([opened]), 'alice')).toEqual({ kind: 'unchanged' });
    // A note or the pending-sync flag isn't in the window.
    const noted = { ...opened, note: 'top shelf', pendingSync: true, updatedBy: 'bob' };
    expect(checkEdit(base, data([noted]), 'alice')).toEqual({ kind: 'unchanged' });
  });

  it("someone else's use or product change is reported with who did it", () => {
    const used = { ...opened, quantity: 1, updatedBy: 'bob' };
    expect(checkEdit(base, data([used]), 'alice')).toEqual({
      kind: 'changed',
      byUid: 'bob',
      live: { batch: used, product: milk },
    });
    const renamed = { ...milk, name: 'Oat milk', updatedBy: 'sam' };
    expect(checkEdit(base, data([opened], [renamed]), 'alice')).toEqual({
      kind: 'changed',
      byUid: 'sam',
      live: { batch: opened, product: renamed },
    });
  });

  it("this person's own change from another device is not someone else's", () => {
    expect(checkEdit(base, data([{ ...opened, quantity: 2, updatedBy: 'alice' }]), 'alice')).toEqual({
      kind: 'unchanged',
    });
  });

  it('a deleted purchase or one used up to 0 is removed', () => {
    expect(checkEdit(base, data([]), 'alice')).toEqual({ kind: 'removed', byUid: null });
    expect(checkEdit(base, data([{ ...opened, quantity: 0, updatedBy: 'bob' }]), 'alice')).toEqual({
      kind: 'removed',
      byUid: 'bob',
    });
  });
});

describe('rebaseEdit', () => {
  it("keeps the person's changes and takes the rest from the item as it is now", () => {
    const then = itemEditOf(base);
    const now = { ...then, quantity: 1, location: 'freezer' as const, minQuantity: 4, name: 'Oat milk' };
    // Alice changed the date and took 1 off (3 to 2); Bob used 2, moved it and renamed the product.
    const draft = { ...then, expiryDate: addDays(TODAY, 1), quantity: 2 };
    expect(rebaseEdit(draft, then, now)).toEqual({ ...now, expiryDate: addDays(TODAY, 1), quantity: 0 });
    expect(rebaseEdit({ ...then, minQuantity: 6 }, then, now).minQuantity).toBe(6);
    expect(rebaseEdit({ ...then, name: 'Soy milk' }, then, now).name).toBe('Soy milk');
  });
});
