// The restock engine's write of a Low stock item (GroceryChangeMapperTest in the app, DESIGN 9.6).
import { initializeApp } from 'firebase/app';
import { getFirestore, type WriteBatch } from 'firebase/firestore';
import { describe, expect, it, vi } from 'vitest';
import type { GroceryChange } from '../domain/grocery/models';
import { groceryMapper } from './grocery';

// No network: doc() only builds references.
const db = getFirestore(initializeApp({ projectId: 'demo-spoilnot', apiKey: 'test' }, 'grocery-mapper-test'));
const actor = { uid: 'u1', name: 'Sam' };

const upsert = (writeCreated: boolean): GroceryChange => ({
  type: 'grocery.upsertLowStock',
  listId: 'g1',
  productId: 'milk',
  name: 'Milk',
  nameKey: 'milk',
  unit: 'pcs',
  category: 'dairy',
  quantity: 1,
  writeCreated,
});

/** The fields and options of the one set() the mapper makes. */
function written(change: GroceryChange): { path: string; fields: Record<string, unknown>; options: unknown } {
  const set = vi.fn();
  expect(groceryMapper.write({ set } as unknown as WriteBatch, db, 'h1', actor, change)).toBe(true);
  expect(set).toHaveBeenCalledTimes(1);
  const [ref, fields, options] = set.mock.calls[0] as [{ path: string }, Record<string, unknown>, unknown];
  return { path: ref.path, fields, options };
}

describe('upsertLowStock', () => {
  it('a new item, or a tombstone coming back, starts unchecked with no one who checked it', () => {
    // LOGIC-01: Add to pantry leaves a checked tombstone; the next dip must not bring it back in the cart.
    const { path, fields, options } = written(upsert(true));
    expect(path).toBe('households/h1/groceryLists/g1/items/low_milk');
    expect(options).toEqual({ merge: true });
    expect(fields).toMatchObject({
      checked: false,
      checkedBy: null,
      checkedAt: null,
      aliasOf: null,
      note: null,
      recipeId: null,
      source: 'low_stock',
    });
    expect(fields).toHaveProperty('createdAt');
    // Never createdBy: a second phone's identical write must pass the rules too.
    expect(fields).not.toHaveProperty('createdBy');
    expect(fields).toHaveProperty('deletedAt');
  });

  it('an item already live in the cache keeps its checked state and creator', () => {
    const { fields } = written(upsert(false));
    expect(fields).not.toHaveProperty('checked');
    expect(fields).not.toHaveProperty('checkedBy');
    expect(fields).not.toHaveProperty('createdBy');
    expect(fields).toMatchObject({ quantity: 1, updatedBy: 'u1' });
  });
});
