// Ported from PantryServiceTest.
import { EMPTY } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import type { StatsChange } from '../insights/stats';
import { addDays } from '../time';
import type { HouseholdChange, HouseholdWriter } from '../write';
import { batch, FIXED_CLOCK, product, TODAY } from './fixtures';
import type { PantryChange, PantryData, Product, QuantityUnit, StockBatch } from './models';
import { PantryService, type ItemEdit, type ProductEdit, type StockDraft } from './service';

class RecordingWriter implements HouseholdWriter {
  last: HouseholdChange[] = [];
  private next = 0;
  readonly errors$ = EMPTY;
  newId = () => `id${this.next++}`;
  apply = async (_h: string, _a: unknown, changes: HouseholdChange[]) => {
    this.last = changes;
  };
}

const actor = { uid: 'u1', name: 'Sam' };
const MONTH = '2026-10';
const data = (products: Product[] = [], batches: StockBatch[] = []): PantryData => ({
  products,
  batches,
  fromCache: false,
});
const draft = ({
  name = 'Milk',
  quantity = 1,
  inDays = 7 as number | null,
  location = 'fridge' as const as StockDraft['location'],
  unit = 'pcs' as QuantityUnit,
  min = null as number | null,
} = {}): StockDraft => ({
  name,
  quantity,
  unit,
  expiryDate: inDays === null ? null : addDays(TODAY, inDays),
  dateType: 'use_by',
  location,
  category: 'dairy',
  note: ' ',
  minQuantity: min,
  autoAddToGrocery: true,
  buyQuantity: null,
});
const ofType = <T extends PantryChange['type']>(changes: HouseholdChange[], type: T) =>
  changes.filter((c): c is Extract<PantryChange, { type: T }> => c.type === type);

describe('PantryService', () => {
  let repo: RecordingWriter;
  let service: PantryService;
  beforeEach(() => {
    repo = new RecordingWriter();
    service = new PantryService(repo, FIXED_CLOCK);
  });

  it('opening a whole batch shortens its date, remembers the days and undoes', async () => {
    const undo = await service.markOpened('h', actor, batch('m1', 'milk', { inDays: 20 }), 1, 5, product('milk'));
    expect(repo.last).toEqual([
      { type: 'pantry.markOpened', batchId: 'm1', openedOn: TODAY, expiryDate: addDays(TODAY, 5) },
      { type: 'pantry.setOpenedShelfLife', productId: 'milk', days: 5 },
    ]);
    expect(undo.changes).toEqual([
      { type: 'pantry.markOpened', batchId: 'm1', openedOn: null, expiryDate: addDays(TODAY, 20) },
    ]);
  });

  it('opening never moves the date later, and a remembered number of days is not written again', async () => {
    const milk = { ...product('milk'), openedShelfLifeDays: 7 };
    await service.markOpened('h', actor, batch('m1', 'milk', { inDays: 2 }), 1, 7, milk);
    expect(repo.last).toEqual([
      { type: 'pantry.markOpened', batchId: 'm1', openedOn: TODAY, expiryDate: addDays(TODAY, 2) },
    ]);
    await service.markOpened('h', actor, batch('m2', 'milk', { inDays: null }), 1, 7, milk);
    expect(repo.last).toEqual([
      { type: 'pantry.markOpened', batchId: 'm2', openedOn: TODAY, expiryDate: addDays(TODAY, 7) },
    ]);
  });

  it('opening some of a batch in pieces moves them to their own batch', async () => {
    const undo = await service.markOpened('h', actor, batch('y1', 'yogurt', { quantity: 6, inDays: 14 }), 1, 3, null);
    const part = ofType(repo.last, 'pantry.createBatch')[0]!.batch;
    expect(repo.last[0]).toEqual({ type: 'pantry.addToBatch', batchId: 'y1', delta: -1 });
    expect([part.quantity, part.openedOn, part.expiryDate]).toEqual([1, TODAY, addDays(TODAY, 3)]);
    expect(undo.changes).toEqual([
      { type: 'pantry.deleteBatch', batchId: part.id },
      { type: 'pantry.addToBatch', batchId: 'y1', delta: 1 },
    ]);
  });

  it('new stock is not added to an opened batch', async () => {
    const opened = { ...batch('m1', 'milk', { inDays: 7 }), openedOn: TODAY };
    await service.add('h', actor, draft({ inDays: 7 }), data([product('milk')], [opened]));
    expect(ofType(repo.last, 'pantry.createBatch')).toHaveLength(1);
    expect(ofType(repo.last, 'pantry.addToBatch')).toHaveLength(0);
  });

  it('using takes the batch that expires first, uses up whole batches and undoes everything', async () => {
    const soon = batch('e1', 'egg', { quantity: 2, inDays: 2 });
    const later = batch('e2', 'egg', { quantity: 6, inDays: 9 });
    const noDate = batch('e3', 'egg', { inDays: null });
    const extra: PantryChange = { type: 'pantry.deleteEvent', eventId: 'x' };
    const undo = await service.use(
      'h',
      actor,
      new Map([['egg', 5]]),
      data([product('egg')], [later, noDate, soon]),
      [extra],
      [extra],
    );
    expect(repo.last).toEqual([
      { type: 'pantry.deleteBatch', batchId: 'e1' },
      { type: 'stats.countUsedUp', month: MONTH, delta: 1 } satisfies StatsChange,
      { type: 'pantry.addToBatch', batchId: 'e2', delta: -3 },
      extra,
    ]);
    expect(undo!.changes).toEqual([
      { type: 'pantry.restoreBatch', batch: soon },
      { type: 'stats.countUsedUp', month: MONTH, delta: -1 },
      { type: 'pantry.addToBatch', batchId: 'e2', delta: 3 },
      extra,
    ]);
  });

  it('using more than there is takes it all, and nothing to take writes nothing', async () => {
    await service.use('h', actor, new Map([['milk', 3]]), data([product('milk')], [batch('m1', 'milk')]));
    expect(repo.last[0]).toEqual({ type: 'pantry.deleteBatch', batchId: 'm1' });
    const before = repo.last;
    expect(await service.use('h', actor, new Map([['milk', 1]]), data([product('milk')]))).toBeNull();
    expect(repo.last).toBe(before);
  });

  it("each add remembers its amount on the product, in the product's unit only", async () => {
    await service.add('h', actor, draft({ name: 'Onions', quantity: 3, unit: 'lb' }), data());
    expect(ofType(repo.last, 'pantry.createProduct')[0]!.product.lastAddedQuantity).toBe(3);
    const onions = { ...product('onions', { name: 'Onions', unit: 'lb' }), lastAddedQuantity: 3 };
    await service.add('h', actor, draft({ name: 'Onions', quantity: 2, unit: 'lb' }), data([onions]));
    expect(ofType(repo.last, 'pantry.updateProduct')[0]!.product.lastAddedQuantity).toBe(2);
    // With stock the unit stays pounds, so an amount in pieces is not remembered.
    await service.add('h', actor, draft({ name: 'Onions', quantity: 5 }), data([onions], [batch('o1', 'onions')]));
    const updated = ofType(repo.last, 'pantry.updateProduct')[0]?.product ?? onions;
    expect(updated.lastAddedQuantity).toBe(3);
  });

  it('a new name creates a product, a batch and an added event', async () => {
    const result = await service.add('h', actor, draft({ name: '  Greek   yogurt ' }), data());
    const created = ofType(repo.last, 'pantry.createProduct')[0]!.product;
    expect([
      created.name,
      created.nameKey,
      created.recentShelfLifeDays,
      created.typicalShelfLifeDays,
      created.restockArmed,
    ]).toEqual(['Greek yogurt', 'greek yogurt', [7], 7, true]);
    const newBatch = ofType(repo.last, 'pantry.createBatch')[0]!.batch;
    expect(newBatch.productId).toBe(created.id);
    expect(newBatch.note).toBeNull(); // a blank note is stored as null
    expect(repo.last[2]!.type).toBe('pantry.logEvent');
    expect(result.merged).toBe(false);
  });

  it('the same name in any case reuses the product', async () => {
    await service.add('h', actor, draft({ name: 'MILK', inDays: 9 }), data([product('milk', { recent: [5] })]));
    const update = ofType(repo.last, 'pantry.updateProduct')[0]!.product;
    expect([update.id, update.recentShelfLifeDays, update.typicalShelfLifeDays]).toEqual(['milk', [5, 9], 7]);
    expect(ofType(repo.last, 'pantry.createProduct')).toHaveLength(0);
  });

  it('same product, date and location increments instead of duplicating', async () => {
    const result = await service.add(
      'h',
      actor,
      draft({ quantity: 3 }),
      data([product('milk')], [batch('m1', 'milk', { quantity: 2, inDays: 7 })]),
    );
    expect(ofType(repo.last, 'pantry.addToBatch')).toEqual([{ type: 'pantry.addToBatch', batchId: 'm1', delta: 3 }]);
    expect([result.merged, result.batchId]).toEqual([true, 'm1']);
  });

  it('a different date or location is a new batch', async () => {
    const existing = batch('m1', 'milk', { inDays: 7 });
    await service.add('h', actor, draft({ inDays: 8 }), data([product('milk')], [existing]));
    expect(ofType(repo.last, 'pantry.createBatch')).toHaveLength(1);
    await service.add('h', actor, draft({ location: 'freezer' }), data([product('milk')], [existing]));
    expect(ofType(repo.last, 'pantry.createBatch')).toHaveLength(1);
  });

  it('the unit stays fixed while the product has stock', async () => {
    await service.add('h', actor, draft({ unit: 'l', inDays: 8 }), data([product('milk')], [batch('m1', 'milk')]));
    expect(ofType(repo.last, 'pantry.createBatch')[0]!.batch.unit).toBe('pcs');
    await service.add('h', actor, draft({ unit: 'l' }), data([product('milk')]));
    expect(ofType(repo.last, 'pantry.createBatch')[0]!.batch.unit).toBe('l');
  });

  it('minus one at one uses the batch up, counts it, and undo takes both back', async () => {
    const b = batch('m1', 'milk');
    const undo = await service.adjust('h', actor, b, -1);
    expect(repo.last).toEqual([
      { type: 'pantry.deleteBatch', batchId: 'm1' },
      { type: 'stats.countUsedUp', month: MONTH, delta: 1 },
    ]);
    await service.undo('h', actor, undo!);
    expect(repo.last).toEqual([
      { type: 'pantry.restoreBatch', batch: b },
      { type: 'stats.countUsedUp', month: MONTH, delta: -1 },
    ]);
  });

  it('plus and minus one use increments', async () => {
    expect(await service.adjust('h', actor, batch('m1', 'milk', { quantity: 2 }), -1)).toBeNull();
    expect(repo.last).toEqual([{ type: 'pantry.addToBatch', batchId: 'm1', delta: -1 }]);
    await service.adjust('h', actor, batch('m1', 'milk', { quantity: 2 }), 1);
    expect(repo.last).toEqual([{ type: 'pantry.addToBatch', batchId: 'm1', delta: 1 }]);
  });

  it('throw away deletes, logs the reason, and undo reverses both', async () => {
    const b = batch('m1', 'milk', { quantity: 2 });
    const undo = await service.throwAway('h', actor, b, 'spoiled');
    const event = ofType(repo.last, 'pantry.logEvent')[0]!;
    expect(repo.last[0]).toEqual({ type: 'pantry.deleteBatch', batchId: 'm1' });
    expect([event.eventType, event.reason, event.quantity]).toEqual(['discarded', 'spoiled', 2]);
    const counted: StatsChange = { type: 'stats.countThrownAway', month: MONTH, day: 12, reason: 'spoiled', delta: 1 };
    expect(repo.last[2]).toEqual(counted);
    expect(undo.changes).toEqual([
      { type: 'pantry.restoreBatch', batch: b },
      { type: 'pantry.deleteEvent', eventId: event.eventId },
      { ...counted, delta: -1 },
    ]);
  });

  it('throwing away several is one write, counted once, and one undo', async () => {
    const a = batch('m1', 'milk', { inDays: -1 });
    const b = batch('y1', 'yogurt', { inDays: -3 });
    const undo = await service.throwAwayAll('h', actor, [a, b], 'expired');
    const counted: StatsChange = { type: 'stats.countThrownAway', month: MONTH, day: 12, reason: 'expired', delta: 2 };
    expect(repo.last).toHaveLength(5);
    expect(ofType(repo.last, 'pantry.deleteBatch').map((c) => c.batchId)).toEqual(['m1', 'y1']);
    expect(ofType(repo.last, 'pantry.logEvent').filter((e) => e.reason === 'expired')).toHaveLength(2);
    expect(repo.last.at(-1)).toEqual(counted);
    expect(ofType(undo!.changes, 'pantry.restoreBatch').map((c) => c.batch)).toEqual([a, b]);
    expect(undo!.changes.at(-1)).toEqual({ ...counted, delta: -2 });
    expect(await service.throwAwayAll('h', actor, [], 'expired')).toBeNull();
  });

  it('editing to zero uses the batch up, moving keeps everything else', async () => {
    const b = batch('m1', 'milk', { quantity: 2 });
    const undo = await service.edit('h', actor, b, {
      quantity: 0,
      expiryDate: null,
      dateType: 'use_by',
      location: 'fridge',
      note: null,
    });
    expect(repo.last).toEqual([
      { type: 'pantry.deleteBatch', batchId: 'm1' },
      { type: 'stats.countUsedUp', month: MONTH, delta: 1 },
    ]);
    expect(undo!.changes).toEqual([
      { type: 'pantry.restoreBatch', batch: b },
      { type: 'stats.countUsedUp', month: MONTH, delta: -1 },
    ]);
    await service.move('h', actor, b, 'freezer');
    expect(ofType(repo.last, 'pantry.updateBatch')[0]!.edit).toEqual({
      quantity: 2,
      expiryDate: b.expiryDate,
      dateType: b.dateType,
      location: 'freezer',
      note: null,
    });
  });

  const productEdit = (p: Product, { name = p.name, unit = p.unit, min = p.minQuantity } = {}): ProductEdit => ({
    name,
    category: p.category,
    defaultLocation: p.defaultLocation,
    unit,
    minQuantity: min,
    autoAddToGrocery: true,
    buyQuantity: null,
    typicalShelfLifeDays: p.typicalShelfLifeDays,
  });

  it('renaming a product updates all its batches in the same write', async () => {
    const milk = product('milk');
    const batches = [1, 2, 3].map((n) => batch(`m${n}`, 'milk', { inDays: n })).concat(batch('o1', 'oat'));
    await service.updateProduct('h', actor, milk, productEdit(milk, { name: 'Whole milk' }), data([milk], batches));
    expect(ofType(repo.last, 'pantry.updateProduct')[0]!.product.nameKey).toBe('whole milk');
    expect(ofType(repo.last, 'pantry.syncBatchProduct').map((c) => c.batchId)).toEqual(['m1', 'm2', 'm3']);
  });

  it("a rename onto another product's name is a conflict", async () => {
    const milk = product('milk');
    const oat = product('oat', { name: 'Oat milk' });
    await expect(
      service.updateProduct('h', actor, milk, productEdit(milk, { name: 'oat MILK' }), data([milk, oat])),
    ).rejects.toMatchObject({
      kind: 'conflict',
    });
    expect(repo.last).toEqual([]);
  });

  it('the unit can only change without stock, and raising keep at least re-arms restock', async () => {
    const milk = { ...product('milk', { min: 1 }), restockArmed: false };
    await service.updateProduct(
      'h',
      actor,
      milk,
      productEdit(milk, { unit: 'l', min: 2 }),
      data([milk], [batch('m1', 'milk')]),
    );
    const updated = ofType(repo.last, 'pantry.updateProduct')[0]!.product;
    expect([updated.unit, updated.restockArmed]).toEqual(['pcs', true]);
    await service.updateProduct('h', actor, milk, productEdit(milk, { unit: 'l', min: 1 }), data([milk]));
    const again = ofType(repo.last, 'pantry.updateProduct')[0]!.product;
    expect([again.unit, again.restockArmed]).toEqual(['l', false]); // the same minimum does not re-arm
  });

  it('clearing keep at least turns automatic add off', async () => {
    const milk = product('milk', { min: 2 });
    await service.updateProduct('h', actor, milk, productEdit(milk, { min: null }), data([milk]));
    const updated = ofType(repo.last, 'pantry.updateProduct')[0]!.product;
    expect([updated.minQuantity, updated.autoAddToGrocery]).toEqual([null, false]);
  });

  it('empty batches are deleted unless a local write is pending', async () => {
    const batches = [
      batch('a', 'milk', { quantity: 0 }),
      { ...batch('b', 'milk', { quantity: 0 }), pendingSync: true },
      batch('c', 'milk'),
    ];
    await service.deleteEmptyBatches('h', actor, data([], batches));
    expect(repo.last).toEqual([{ type: 'pantry.deleteBatch', batchId: 'a' }]);
  });

  const itemEdit = ({
    name = 'Milk',
    quantity = 2,
    unit = 'pcs' as QuantityUnit,
    autoAdd = false,
    min = null as number | null,
    buy = null as number | null,
  } = {}): ItemEdit => ({
    name,
    quantity,
    unit,
    category: 'dairy',
    expiryDate: addDays(TODAY, 4),
    location: 'freezer',
    autoAddToGrocery: autoAdd,
    minQuantity: min,
    buyQuantity: buy,
  });

  it('the edit window changes the purchase and its product in one write, keeping date type and note', () => {
    const b1 = { ...batch('b1', 'milk'), dateType: 'best_before' as const, note: 'Lactose free' };
    const changes = service.planEdit(
      b1,
      itemEdit({ name: 'Oat milk', autoAdd: true, min: 2, buy: 4 }),
      data([product('milk')], [b1]),
    );
    expect(ofType(changes, 'pantry.updateBatch')[0]!.edit).toEqual({
      quantity: 2,
      expiryDate: addDays(TODAY, 4),
      dateType: 'best_before',
      location: 'freezer',
      note: 'Lactose free',
    });
    const p = ofType(changes, 'pantry.updateProduct')[0]!.product;
    expect([p.name, p.minQuantity, p.autoAddToGrocery, p.buyQuantity, p.restockArmed]).toEqual([
      'Oat milk',
      2,
      true,
      4,
      true,
    ]);
    expect(ofType(changes, 'pantry.syncBatchProduct').map((c) => c.batchId)).toEqual(['b1']);
  });

  it('turning auto-add off clears Keep at least and Buy quantity', () => {
    const rice = { ...product('rice', { min: 5 }), buyQuantity: 10 };
    const b1 = batch('b1', 'rice');
    const p = ofType(
      service.planEdit(b1, itemEdit({ name: 'Rice', min: 5, buy: 10 }), data([rice], [b1])),
      'pantry.updateProduct',
    )[0]!.product;
    expect([p.minQuantity, p.autoAddToGrocery, p.buyQuantity]).toEqual([null, false, null]);
  });

  it("the unit changes only when this is the product's only purchase", () => {
    const rice = product('rice');
    const only = data([rice], [batch('b1', 'rice')]);
    const two = data([rice], [batch('b1', 'rice'), batch('b2', 'rice')]);
    const kg = itemEdit({ name: 'Rice', unit: 'kg' });
    expect(ofType(service.planEdit(only.batches[0]!, kg, only), 'pantry.updateProduct')[0]!.product.unit).toBe('kg');
    expect(
      ofType(service.planEdit(two.batches[0]!, kg, two), 'pantry.updateProduct').every((c) => c.product.unit === 'pcs'),
    ).toBe(true);
  });

  it("a rename onto another product's name is refused", () => {
    const d = data([product('milk'), product('eggs')], [batch('b1', 'milk')]);
    expect(() => service.planEdit(d.batches[0]!, itemEdit({ name: 'eggs' }), d)).toThrow(
      expect.objectContaining({ kind: 'nameTaken' }),
    );
  });
});
