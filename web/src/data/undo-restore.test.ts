// Edits and Undo as the server would store them (PantryChangeMapperTest and UndoRestoreTest in the app; after v1,
// DESIGN 8.6 and 9.4): an edit writes only what changed, and Undo of a deletion removes the tombstone and nothing
// else, so another member's change, fields a newer app added and the creator stay.
import { initializeApp } from 'firebase/app';
import {
  deleteField,
  getFirestore,
  serverTimestamp,
  type DocumentSnapshot,
  type FieldValue,
  type WriteBatch,
} from 'firebase/firestore';
import { describe, expect, it } from 'vitest';
import type { GroceryChange, GroceryItem } from '../domain/grocery/models';
import { batch as stockBatch, product as productOf, TODAY } from '../domain/pantry/fixtures';
import { batchEditOf, type PantryChange } from '../domain/pantry/models';
import type { Recipe, RecipeChange } from '../domain/recipes/models';
import type { MealPlanChange, PlannedMeal } from '../domain/plan/models';
import { addDays } from '../domain/time';
import type { Actor, HouseholdChange } from '../domain/write';
import { groceryMapper, toItem } from './grocery';
import { pantryMapper, toBatch, toProduct } from './pantry';
import { recipeMapper } from './recipes';
import { mealPlanMapper } from './plan';
import type { ChangeMapper } from './writer';

// No network: doc() only builds references.
const db = getFirestore(initializeApp({ projectId: 'demo-spoilnot', apiKey: 'test' }, 'undo-restore-test'));
const alice: Actor = { uid: 'alice', name: 'Alice' };
const bob: Actor = { uid: 'bob', name: 'Bob' };
const SERVER_TIME = '(server time)';
const isSentinel = (v: unknown, s: FieldValue) =>
  typeof v === 'object' && v !== null && 'isEqual' in v && (v as FieldValue).isEqual(s);

/** The documents the mappers' writes leave on the server: set replaces, merge and update change named fields. */
class FakeServer {
  docs = new Map<string, Record<string, unknown>>();
  writes: { path: string; kind: 'set' | 'merge' | 'update'; fields: Record<string, unknown> }[] = [];

  private put(path: string, kind: 'set' | 'merge' | 'update', fields: Record<string, unknown>) {
    this.writes.push({ path, kind, fields });
    if (kind === 'update' && !this.docs.has(path)) throw new Error(`update of a missing document: ${path}`);
    const doc = new Map(Object.entries(kind === 'set' ? {} : (this.docs.get(path) ?? {})));
    for (const [key, value] of Object.entries(fields)) {
      if (isSentinel(value, deleteField())) doc.delete(key);
      else doc.set(key, isSentinel(value, serverTimestamp()) ? SERVER_TIME : value);
    }
    this.docs.set(path, Object.fromEntries(doc));
  }

  readonly batch = {
    set: (ref: { path: string }, fields: Record<string, unknown>, options?: { merge?: boolean }) =>
      this.put(ref.path, options?.merge ? 'merge' : 'set', fields),
    update: (ref: { path: string }, fields: Record<string, unknown>) => this.put(ref.path, 'update', fields),
  } as unknown as WriteBatch;

  run(
    mapper: ChangeMapper,
    actor: Actor,
    ...changes: (PantryChange | GroceryChange | RecipeChange | MealPlanChange)[]
  ) {
    for (const c of changes) expect(mapper.write(this.batch, db, 'h1', actor, c as HouseholdChange)).toBe(true);
  }

  /** The stored document read through the getters the readers use. */
  snapshot(path: string): DocumentSnapshot {
    const data = this.docs.get(path)!;
    return {
      id: path.split('/').at(-1),
      get: (field: string) => (data[field] === SERVER_TIME ? null : data[field]),
      metadata: { hasPendingWrites: false },
    } as unknown as DocumentSnapshot;
  }

  get last() {
    return this.writes.at(-1)!;
  }
}

const restoreKeys = ['deletedAt', 'updatedAt', 'updatedBy'];

describe('pantry', () => {
  const path = 'households/h1/stock/m1';

  it('a batch comes back from Undo as the server had it, unknown fields and creator included', () => {
    const server = new FakeServer();
    const milk = stockBatch('m1', 'milk', { quantity: 3 });
    server.run(pantryMapper, bob, { type: 'pantry.createBatch', batch: milk } satisfies PantryChange);
    server.docs.get(path)!.futureField = 'from a newer app';
    server.run(pantryMapper, alice, { type: 'pantry.deleteBatch', batchId: 'm1' } satisfies PantryChange);
    expect(server.docs.get(path)).toHaveProperty('deletedAt');
    // Bob's phone hadn't seen the deletion yet and used one.
    server.docs.get(path)!.quantity = 2;
    server.run(pantryMapper, alice, { type: 'pantry.restoreBatch', batch: milk } satisfies PantryChange);

    expect(server.last.kind).toBe('merge');
    expect(Object.keys(server.last.fields).sort()).toEqual(restoreKeys);
    const after = server.docs.get(path)!;
    expect(after).not.toHaveProperty('deletedAt');
    expect(after).toMatchObject({ futureField: 'from a newer app', createdBy: 'bob' });
    expect(toBatch(server.snapshot(path))).toEqual({ ...milk, quantity: 2, createdAt: null, updatedBy: 'alice' });
  });

  it("an edit writes the changed fields only, over another member's change", () => {
    const server = new FakeServer();
    const milk = stockBatch('m1', 'milk', { quantity: 3 });
    server.run(pantryMapper, bob, { type: 'pantry.createBatch', batch: milk } satisfies PantryChange);
    Object.assign(server.docs.get(path)!, { quantity: 1, location: 'freezer' });
    const before = batchEditOf(milk);
    server.run(pantryMapper, alice, {
      type: 'pantry.updateBatch',
      batchId: 'm1',
      before,
      after: { ...before, expiryDate: addDays(TODAY, 9) },
    } satisfies PantryChange);
    expect(server.last.kind).toBe('update');
    expect(Object.keys(server.last.fields).sort()).toEqual(['expiryDate', 'updatedAt', 'updatedBy']);
    expect(toBatch(server.snapshot(path))).toEqual({
      ...milk,
      quantity: 1,
      location: 'freezer',
      expiryDate: addDays(TODAY, 9),
      createdAt: null,
      updatedBy: 'alice',
    });
  });

  it('a product edit writes the changed fields only, and nothing when nothing changed', () => {
    const server = new FakeServer();
    const milk = productOf('milk', { min: 2 });
    server.run(pantryMapper, bob, { type: 'pantry.createProduct', product: milk } satisfies PantryChange);
    const renamed = { ...milk, name: 'Whole milk', nameKey: 'whole milk' };
    server.run(pantryMapper, alice, {
      type: 'pantry.updateProduct',
      product: renamed,
      before: milk,
    } satisfies PantryChange);
    expect(Object.keys(server.last.fields).sort()).toEqual(['name', 'nameKey', 'updatedAt', 'updatedBy']);
    expect(toProduct(server.snapshot('households/h1/products/milk'))).toEqual({ ...renamed, updatedBy: 'alice' });
    const count = server.writes.length;
    server.run(pantryMapper, alice, {
      type: 'pantry.updateProduct',
      product: renamed,
      before: renamed,
    } satisfies PantryChange);
    expect(server.writes).toHaveLength(count);
  });

  it('undo of a minus one that used the batch up adds it back and removes a tombstone', () => {
    const server = new FakeServer();
    server.run(pantryMapper, bob, {
      type: 'pantry.createBatch',
      batch: stockBatch('m1', 'milk'),
    } satisfies PantryChange);
    server.run(pantryMapper, alice, {
      type: 'pantry.addToBatch',
      batchId: 'm1',
      delta: 1,
      revive: true,
    } satisfies PantryChange);
    expect(Object.keys(server.last.fields).sort()).toEqual(['deletedAt', 'quantity', 'updatedAt', 'updatedBy']);
    server.run(pantryMapper, alice, { type: 'pantry.addToBatch', batchId: 'm1', delta: -1 } satisfies PantryChange);
    expect(server.last.fields).not.toHaveProperty('deletedAt');
  });
});

describe('grocery items, recipes and meals', () => {
  const eggs: GroceryItem = {
    id: 'i1',
    name: 'Eggs',
    nameKey: 'eggs',
    quantity: 12,
    unit: 'pcs',
    note: null,
    category: 'dairy',
    checked: false,
    checkedBy: null,
    source: 'manual',
    productId: null,
    recipeId: null,
    createdBy: 'bob',
    createdAt: null,
    pendingSync: false,
    aliasOf: null,
  } as GroceryItem;
  const itemPath = 'households/h1/groceryLists/g1/items/i1';

  it('a grocery item comes back checked, by whom, with its creator and unknown fields', () => {
    const server = new FakeServer();
    server.run(groceryMapper, bob, { type: 'grocery.createItem', listId: 'g1', item: eggs } satisfies GroceryChange);
    server.docs.get(itemPath)!.futureField = 1;
    server.run(groceryMapper, alice, {
      type: 'grocery.deleteItem',
      listId: 'g1',
      itemId: 'i1',
      tombstone: true,
    } as GroceryChange);
    // Bob's phone hadn't seen the deletion yet and checked it off.
    server.run(groceryMapper, bob, {
      type: 'grocery.setChecked',
      listId: 'g1',
      itemId: 'i1',
      checked: true,
    } as GroceryChange);
    server.run(groceryMapper, alice, { type: 'grocery.restoreItem', listId: 'g1', item: eggs } satisfies GroceryChange);
    expect(server.last.kind).toBe('merge');
    expect(Object.keys(server.last.fields).sort()).toEqual(restoreKeys);
    const doc = server.docs.get(itemPath)!;
    expect(doc).not.toHaveProperty('deletedAt');
    expect(doc).toMatchObject({ futureField: 1, checked: true, checkedBy: 'bob', createdBy: 'bob' });
    expect(toItem(server.snapshot(itemPath))).toMatchObject({ ...eggs, checked: true, checkedBy: 'bob' });
  });

  it('a grocery edit writes only what changed', () => {
    const server = new FakeServer();
    server.run(groceryMapper, bob, { type: 'grocery.createItem', listId: 'g1', item: eggs } satisfies GroceryChange);
    const before = { quantity: 12, unit: 'pcs' as const, note: null, category: 'dairy' as const };
    server.run(groceryMapper, alice, {
      type: 'grocery.updateItem',
      listId: 'g1',
      itemId: 'i1',
      before,
      after: { ...before, note: 'free range' },
    } satisfies GroceryChange);
    expect(Object.keys(server.last.fields).sort()).toEqual(['note', 'updatedAt', 'updatedBy']);
  });

  it('a recipe and a meal come back by removing the tombstone only', () => {
    const server = new FakeServer();
    const recipe = { id: 'r1', createdBy: 'bob' } as Recipe;
    server.docs.set('households/h1/recipes/r1', { title: 'Soup', createdBy: 'bob', deletedAt: 't' });
    server.run(recipeMapper, alice, { type: 'recipe.restore', recipe } satisfies RecipeChange);
    expect(Object.keys(server.last.fields).sort()).toEqual(restoreKeys);
    expect(server.docs.get('households/h1/recipes/r1')).toEqual({
      title: 'Soup',
      createdBy: 'bob',
      updatedAt: SERVER_TIME,
      updatedBy: 'alice',
    });
    const meal = { id: 'p1', createdBy: 'bob' } as PlannedMeal;
    server.run(mealPlanMapper, alice, { type: 'meal.restore', meal } satisfies MealPlanChange);
    expect(server.last.kind).toBe('merge');
    expect(Object.keys(server.last.fields).sort()).toEqual(restoreKeys);
  });
});
