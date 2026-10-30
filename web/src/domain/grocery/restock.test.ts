// Ported from RestockEngineTest. DESIGN 9.6 requires tests for: crossing down, recovery then dropping again, the
// user deleting the item, two concurrent evaluations, a product without a minimum, a minimum raised while already
// low, and batches with quantity 0. The engine is applied to a small simulated database so sequences can be checked.
import { defer, EMPTY, of, Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { batch, FIXED_CLOCK, product } from '../pantry/fixtures';
import type { PantryChange, PantryData, Product, StockBatch } from '../pantry/models';
import type { PantryRepository } from '../pantry/repository';
import type { SessionManager } from '../session';
import type { HouseholdChange, HouseholdWriter } from '../write';
import { GroceryRules, type GroceryChange, type GroceryItem, type GroceryRepository } from './models';
import { RestockEngine, RestockRunner } from './restock';

const LIST = 'g1';

class World {
  items = new Map<string, Record<string, unknown>>();
  writes = 0;
  constructor(
    public products: Product[],
    public batches: StockBatch[],
  ) {}
  get data(): PantryData {
    return { products: this.products, batches: this.batches, fromCache: false };
  }
  apply(plan: (GroceryChange | PantryChange)[]) {
    for (const change of plan) {
      this.writes++;
      if (change.type === 'pantry.setRestockArmed') {
        this.products = this.products.map((p) =>
          p.id === change.productId ? { ...p, restockArmed: change.armed } : p,
        );
      } else if (change.type === 'grocery.upsertLowStock') {
        const id = GroceryRules.lowStockItemId(change.productId);
        this.items.set(id, { ...this.items.get(id), quantity: change.quantity, name: change.name });
      } else {
        throw new Error(`unexpected ${change.type}`);
      }
    }
  }
  stock(id: string, quantity: number) {
    this.batches = [...this.batches.filter((b) => b.id !== id), batch(id, 'milk', { quantity })];
  }
  run() {
    this.apply(RestockEngine.plan(this.data, LIST, new Set(this.items.keys())));
  }
}

const milk = ({ min = 2 as number | null, armed = true, buy = null as number | null } = {}): Product => ({
  ...product('milk', { min }),
  restockArmed: armed,
  buyQuantity: buy,
});
const data = (products: Product[], batches: StockBatch[]): PantryData => ({ products, batches, fromCache: false });

describe('RestockEngine', () => {
  it('crossing below the minimum adds the item once and disarms', () => {
    const world = new World([milk()], [batch('b1', 'milk', { quantity: 3 })]);
    world.run();
    expect(world.items.size).toBe(0);

    world.stock('b1', 1);
    world.run();
    expect(world.items.get('low_milk')).toEqual({ quantity: 1, name: 'Milk' });
    expect(world.products[0]!.restockArmed).toBe(false);

    const writes = world.writes;
    world.run();
    world.stock('b1', 0.5);
    world.run();
    expect(world.writes).toBe(writes); // no nagging while disarmed
  });

  it('buy quantity wins, otherwise minimum minus stock with at least 1', () => {
    expect(RestockEngine.buyQuantity(milk({ min: 4, buy: 5 }), 1)).toBe(5);
    expect(RestockEngine.buyQuantity(milk({ min: 4 }), 1)).toBe(3);
    expect(RestockEngine.buyQuantity(milk({ min: 1.5 }), 1)).toBe(1);
  });

  it('recovery re-arms and the next drop adds again', () => {
    const world = new World([milk()], [batch('b1', 'milk', { quantity: 1 })]);
    world.run();
    world.items.clear(); // bought and moved to the pantry
    world.stock('b1', 4);
    world.run();
    expect(world.products[0]!.restockArmed).toBe(true);
    expect(world.items.size).toBe(0);

    world.stock('b1', 1);
    world.run();
    expect(world.items.has('low_milk')).toBe(true);
  });

  it('a deleted item is not recreated until stock recovers', () => {
    const world = new World([milk()], [batch('b1', 'milk', { quantity: 1 })]);
    world.run();
    world.items.delete('low_milk'); // the user deleted it
    for (let i = 0; i < 3; i++) world.run();
    expect(world.items.size).toBe(0);
    world.stock('b1', 0.5);
    world.run();
    expect(world.items.size).toBe(0); // still below, still disarmed
  });

  it('two phones evaluating the same snapshot write identical data', () => {
    const products = [milk()];
    const batches = [batch('b1', 'milk', { quantity: 1 })];
    const phoneA = RestockEngine.plan(data(products, batches), LIST, new Set());
    const phoneB = RestockEngine.plan(data(products, batches), LIST, new Set());
    expect(phoneA).toEqual(phoneB);

    const world = new World(products, batches);
    world.apply(phoneA);
    world.apply(phoneB);
    expect(world.items.size).toBe(1);
    expect(world.products[0]!.restockArmed).toBe(false);
  });

  it('a product without a minimum or with automatic add off is ignored', () => {
    const none = new World([milk({ min: null })], []);
    none.run();
    const off = new World([{ ...milk(), autoAddToGrocery: false }], []);
    off.run();
    expect(none.writes + off.writes).toBe(0);
  });

  it('raising the minimum while already low adds the item', () => {
    // PantryService re-arms the product when Keep at least goes up (DESIGN 9.6).
    const world = new World([milk({ min: 1, armed: false })], [batch('b1', 'milk', { quantity: 1 })]);
    world.run();
    expect(world.items.size).toBe(0);
    world.products = [milk({ min: 3, armed: true })];
    world.run();
    expect(world.items.get('low_milk')!.quantity).toBe(2);
  });

  it('batches at zero or below do not count', () => {
    const world = new World(
      [milk({ min: 1 })],
      [batch('b1', 'milk', { quantity: 0 }), batch('b2', 'milk', { quantity: -2 })],
    );
    world.run();
    expect(world.items.get('low_milk')!.quantity).toBe(1);
  });

  it('created fields are written only for items missing from the cache', () => {
    const d = data([milk()], [batch('b1', 'milk', { quantity: 1 })]);
    const upsert = (cached: Set<string>) =>
      RestockEngine.plan(d, LIST, cached).find((c) => c.type === 'grocery.upsertLowStock') as Extract<
        GroceryChange,
        { type: 'grocery.upsertLowStock' }
      >;
    expect(upsert(new Set()).writeCreated).toBe(true);
    expect(upsert(new Set(['low_milk'])).writeCreated).toBe(false);
  });

  it('without a default list nothing is disarmed', () => {
    expect(RestockEngine.plan(data([milk()], [batch('b1', 'milk', { quantity: 1 })]), null, new Set())).toEqual([]);
  });

  it('back in stock shows for an unchecked low-stock item once stock recovers', () => {
    const item: GroceryItem = {
      id: 'low_milk',
      name: 'Milk',
      nameKey: 'milk',
      quantity: 1,
      unit: null,
      note: null,
      category: null,
      checked: false,
      checkedBy: null,
      source: 'low_stock',
      productId: 'milk',
      recipeId: null,
      createdBy: null,
      createdAt: null,
      pendingSync: false,
      aliasOf: null,
    };
    const low = data([milk()], [batch('b1', 'milk', { quantity: 1 })]);
    const back = data([milk()], [batch('b1', 'milk', { quantity: 2 })]);
    expect(RestockEngine.isBackInStock(item, low)).toBe(false);
    expect(RestockEngine.isBackInStock(item, back)).toBe(true);
    expect(RestockEngine.isBackInStock({ ...item, checked: true }, back)).toBe(false);
    expect(RestockEngine.isBackInStock({ ...item, source: 'manual' }, back)).toBe(false);
  });
});

describe('RestockRunner', () => {
  const session = {
    session$: of({
      kind: 'inHousehold',
      user: { uid: 'u1' },
      displayName: 'Sam',
      householdId: 'h',
      role: 'owner',
      units: 'metric',
    }),
  } as unknown as SessionManager;
  const lists: GroceryRepository = {
    observeLists: () => of([{ id: LIST, name: 'Groceries', isDefault: true, aisleOrder: [] }]),
    observeItems: () => of([]),
    isCached: async () => false,
    itemIds: async () => [],
  };
  const low = data([milk()], [batch('b1', 'milk', { quantity: 1 })]);

  class Writes implements HouseholdWriter {
    plans: HouseholdChange[][] = [];
    readonly errors$ = EMPTY;
    newId = () => 'id';
    apply = async (_h: string, _a: unknown, changes: HouseholdChange[]) => {
      this.plans.push(changes);
    };
  }

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('listens again after a refused listener instead of stopping for the session', async () => {
    let attempts = 0;
    // Each subscription starts its own listeners, as the Firestore repository's does.
    const pantry: PantryRepository = {
      observe: () => defer(() => (attempts++ === 0 ? throwError(() => new Error('permission denied')) : of(low))),
    };
    const writer = new Writes();
    const runner = new RestockRunner(session, pantry, lists, writer, FIXED_CLOCK);
    runner.start();
    await vi.advanceTimersByTimeAsync(1_000);
    expect(writer.plans).toEqual([]);
    await vi.advanceTimersByTimeAsync(5_000);
    expect(writer.plans).toHaveLength(1);
    expect(writer.plans[0]!.map((c) => c.type)).toEqual(['grocery.upsertLowStock', 'pantry.setRestockArmed']);
    runner.stop();
  });

  it('does not resend the same plan while the server is rejecting it', async () => {
    const snapshots = new Subject<PantryData>();
    const writer = new Writes();
    const runner = new RestockRunner(session, { observe: () => snapshots }, lists, writer, FIXED_CLOCK);
    runner.start();
    snapshots.next(low);
    await vi.advanceTimersByTimeAsync(400);
    // The rejected write rolls back locally, so the same snapshot comes again.
    snapshots.next({ ...low });
    await vi.advanceTimersByTimeAsync(400);
    expect(writer.plans).toHaveLength(1);
    runner.stop();
  });
});
