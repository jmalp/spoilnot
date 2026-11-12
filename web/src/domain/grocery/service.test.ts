// Ported from GroceryServiceTest.
import { EMPTY, of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { batch, FIXED_CLOCK, product, TODAY } from '../pantry/fixtures';
import type { Category, PantryChange, PantryData } from '../pantry/models';
import { PantryService } from '../pantry/service';
import { addDays } from '../time';
import type { HouseholdChange, HouseholdWriter } from '../write';
import type { GroceryChange, GroceryItem, GroceryRepository } from './models';
import {
  AddToPantryRules,
  canIncrease,
  GroceryLayout,
  GroceryService,
  parseQuickAdd,
  type PantryEntry,
} from './service';

class RecordingWriter implements HouseholdWriter {
  last: HouseholdChange[] = [];
  calls = 0;
  private next = 0;
  readonly errors$ = EMPTY;
  newId = () => `id${this.next++}`;
  apply = async (_h: string, _a: unknown, changes: HouseholdChange[]) => {
    this.calls++;
    this.last = changes;
  };
}

const noRepository: GroceryRepository = {
  observeLists: () => of([]),
  observeCachedLists: () => of([]),
  observeItems: () => of([]),
  isCached: async () => false,
  itemIds: async (_hid, listId) => (listId === 'g2' ? ['i1', 'i2'] : []),
};

const actor = { uid: 'u1', name: 'Sam' };
const empty: PantryData = { products: [], batches: [], fromCache: false };
const pantry = (products = empty.products, batches = empty.batches): PantryData => ({
  products,
  batches,
  fromCache: false,
});

function item(
  id: string,
  name: string,
  { checked = false, category = null as Category | null, minutesAgo = 0 } = {},
): GroceryItem {
  return {
    id,
    name,
    nameKey: name.toLowerCase(),
    quantity: null,
    unit: null,
    note: null,
    category,
    checked,
    checkedBy: null,
    source: 'manual',
    productId: null,
    recipeId: null,
    createdBy: 'u1',
    createdAt: Date.parse('2026-10-12T12:00:00Z') - minutesAgo * 60_000,
    pendingSync: false,
    aliasOf: null,
  };
}

const entry = (i: GroceryItem, quantity: number, inDays: number, location: PantryEntry['location']): PantryEntry => ({
  item: i,
  quantity,
  unit: 'pcs',
  expiryDate: addDays(TODAY, inDays),
  location,
  dateType: 'use_by',
});

const ofType = <T extends HouseholdChange['type']>(changes: HouseholdChange[], type: T) =>
  changes.filter((c) => c.type === type) as Extract<GroceryChange | PantryChange, { type: T }>[];

let writer: RecordingWriter;
let service: GroceryService;
beforeEach(() => {
  writer = new RecordingWriter();
  service = new GroceryService(writer, noRepository, new PantryService(writer, FIXED_CLOCK));
});

describe('GroceryService', () => {
  it('checking off is logged, unchecking is not', async () => {
    await service.setChecked('h', actor, 'g1', item('i1', 'Milk'), true, 'Groceries');
    expect(writer.last).toEqual([
      { type: 'grocery.setChecked', listId: 'g1', itemId: 'i1', checked: true },
      {
        type: 'grocery.logEvent',
        eventId: 'id0',
        event: 'grocery_checked',
        name: 'Milk',
        listName: 'Groceries',
        count: null,
      },
    ]);
    await service.setChecked('h', actor, 'g1', item('i1', 'Milk', { checked: true }), false, 'Groceries');
    expect(writer.last).toEqual([{ type: 'grocery.setChecked', listId: 'g1', itemId: 'i1', checked: false }]);
  });

  it('quick add parses quantity and unit', () => {
    expect(parseQuickAdd('12 eggs')).toEqual({ name: 'eggs', quantity: 12, unit: null });
    expect(parseQuickAdd('1,5 kg rice')).toEqual({ name: 'rice', quantity: 1.5, unit: 'kg' });
    expect(parseQuickAdd('2 fl oz milk')).toEqual({ name: 'milk', quantity: 2, unit: 'fl_oz' });
    // A leading number is read as a quantity; a number alone is a name.
    expect(parseQuickAdd('  7 Up ')).toEqual({ name: 'Up', quantity: 7, unit: null });
    expect(parseQuickAdd('12')).toEqual({ name: '12', quantity: null, unit: null });
    expect(parseQuickAdd('Greek   yogurt')).toEqual({ name: 'Greek yogurt', quantity: null, unit: null });
  });

  it('adding a name already on the list asks first', async () => {
    const eggs = item('i1', 'Eggs');
    const result = await service.quickAdd('h', actor, 'g1', 'EGGS', [eggs], empty);
    expect(result.kind).toBe('alreadyOnList');
    expect(writer.last).toEqual([]);
    // A checked one does not count; and "Add anyway" adds a second line.
    expect(await service.quickAdd('h', actor, 'g1', 'eggs', [{ ...eggs, checked: true }], empty)).toEqual({
      kind: 'added',
    });
    expect(await service.quickAdd('h', actor, 'g1', 'eggs', [eggs], empty, { addAnyway: true })).toEqual({
      kind: 'added',
    });
  });

  it('quick add links the pantry product for its category', async () => {
    const milk = product('milk', { category: 'dairy' });
    await service.quickAdd('h', actor, 'g1', '2 milk', [], pantry([milk]), { listName: 'Groceries' });
    const created = ofType(writer.last, 'grocery.createItem')[0]!.item;
    // Logged for Recent activity (DESIGN 5.6).
    const event = ofType(writer.last, 'grocery.logEvent')[0]!;
    expect([event.event, event.name, event.listName]).toEqual(['grocery_added', 'Milk', 'Groceries']);
    expect(created).toMatchObject({ name: 'Milk', productId: 'milk', category: 'dairy', quantity: 2 });
  });

  it('a typed name without a product gets a guessed aisle', async () => {
    await service.quickAdd('h', actor, 'g1', '3 bananas', [], empty);
    const created = ofType(writer.last, 'grocery.createItem')[0]!.item;
    expect(created.category).toBe('produce');
    expect(created.productId).toBeNull();
    await service.quickAdd('h', actor, 'g1', 'batteries', [], empty);
    expect(ofType(writer.last, 'grocery.createItem')[0]!.item.category).toBeNull();
  });

  it('renaming an item relinks it to the product of its new name', async () => {
    const beans = product('beans', { name: 'Black beans', category: 'pantry_staples' });
    const beansItem = { ...item('i1', 'Beans'), productId: 'old' };
    const edit = { quantity: 2, unit: null, note: ' note ', category: null };
    await service.edit('h', actor, 'g1', beansItem, { ...edit, name: 'black  BEANS ' }, [beans]);
    expect(writer.last).toEqual([
      {
        type: 'grocery.updateItem',
        listId: 'g1',
        itemId: 'i1',
        before: { quantity: null, unit: null, note: null, category: null },
        after: { quantity: 2, unit: null, note: 'note', category: null },
      },
      {
        type: 'grocery.renameItem',
        listId: 'g1',
        itemId: 'i1',
        name: 'Black beans',
        nameKey: 'black beans',
        productId: 'beans',
      },
    ]);
    // A name no product has keeps the typed spelling and unlinks; fields that didn't change aren't written.
    const none = { quantity: null, unit: null, note: null, category: null };
    await service.edit('h', actor, 'g1', beansItem, { ...none, name: 'Pinto beans' }, [beans]);
    expect(writer.last).toEqual([
      {
        type: 'grocery.renameItem',
        listId: 'g1',
        itemId: 'i1',
        name: 'Pinto beans',
        nameKey: 'pinto beans',
        productId: null,
      },
    ]);
    // Nothing changed: nothing is written.
    writer.last = [];
    await service.edit('h', actor, 'g1', beansItem, { ...none, name: 'Beans' }, [beans]);
    expect(writer.last).toEqual([]);
  });

  it('increase adds to an existing quantity or starts from one', async () => {
    await service.increase('h', actor, 'g1', { ...item('i1', 'Eggs'), quantity: 6 }, parseQuickAdd('6 eggs'));
    expect(writer.last).toEqual([{ type: 'grocery.addToItem', listId: 'g1', itemId: 'i1', delta: 6 }]);
    await service.increase('h', actor, 'g1', item('i1', 'Eggs'), parseQuickAdd('eggs'));
    expect(ofType(writer.last, 'grocery.updateItem')[0]!.after.quantity).toBe(2);
  });

  it('delete and clear checked can be undone', async () => {
    const eggs = item('i1', 'Eggs');
    const undo = await service.delete('h', actor, 'g1', eggs);
    expect(undo.changes).toEqual([{ type: 'grocery.restoreItem', listId: 'g1', item: eggs }]);

    const cart = [item('a', 'A', { checked: true }), item('b', 'B'), item('c', 'C', { checked: true })];
    const cleared = (await service.clearChecked('h', actor, 'g1', cart))!;
    expect(ofType(writer.last, 'grocery.deleteItem').map((c) => c.itemId)).toEqual(['a', 'c']);
    expect(cleared.changes).toHaveLength(2);
    expect(await service.clearChecked('h', actor, 'g1', [item('b', 'B')])).toBeNull();
  });

  it('a renamed recipe item teaches its product the recipe name when it reaches the pantry', async () => {
    const onions = product('onion', { name: 'Onions' });
    const renamed = { ...item('i1', 'Onions'), aliasOf: 'scallion' };
    const newName = { ...item('i2', 'Green onions'), aliasOf: 'spring onion' };
    await service.addToPantry(
      'h',
      actor,
      'g1',
      [entry(renamed, 1, 7, 'fridge'), entry(newName, 1, 7, 'fridge')],
      [],
      pantry([onions]),
    );
    expect(writer.last).toContainEqual({ type: 'pantry.addAlias', productId: 'onion', alias: 'scallion' });
    const created = ofType(writer.last, 'pantry.createProduct');
    expect(created).toHaveLength(1);
    expect(created[0]!.product).toMatchObject({ name: 'Green onions', aliases: ['spring onion'] });
    // Already known: nothing to add.
    const taught = { ...onions, aliases: ['scallion'] };
    await service.addToPantry('h', actor, 'g1', [entry(renamed, 1, 7, 'fridge')], [], pantry([taught]));
    expect(ofType(writer.last, 'pantry.addAlias')).toEqual([]);
  });

  it('add to pantry is one write with new and existing products, merged batches and items removed', async () => {
    const milk = product('milk', { recent: [7] });
    const existing = batch('m1', 'milk', { quantity: 1, inDays: 7 });
    const milkItem = { ...item('i1', 'Milk'), productId: 'milk' };
    const bread1 = item('i2', 'Bread', { category: 'bakery' });
    const bread2 = item('i3', 'bread');
    const skipped = item('i4', 'Batteries');

    await service.addToPantry(
      'h',
      actor,
      'g1',
      [entry(milkItem, 2, 7, 'fridge'), entry(bread1, 1, 3, 'pantry'), entry(bread2, 1, 3, 'pantry')],
      [skipped],
      pantry([milk], [existing]),
    );

    expect(writer.calls).toBe(1); // one batched write
    const changes = writer.last;
    expect(ofType(changes, 'pantry.addToBatch')[0]).toEqual({ type: 'pantry.addToBatch', batchId: 'm1', delta: 2 });
    const created = ofType(changes, 'pantry.createProduct');
    expect(created.map((c) => c.product.name)).toEqual(['Bread']); // the two breads share one new product
    expect(created[0]!.product.category).toBe('bakery');
    const breadBatches = ofType(changes, 'pantry.createBatch');
    expect(breadBatches).toHaveLength(1);
    const breadId = breadBatches[0]!.batch.id;
    const added = ofType(changes, 'pantry.addToBatch')
      .filter((c) => c.batchId === breadId)
      .reduce((sum, c) => sum + c.delta, 0);
    expect(breadBatches[0]!.batch.quantity + added).toBe(2);
    expect(new Set(ofType(changes, 'grocery.deleteItem').map((c) => c.itemId))).toEqual(
      new Set(['i1', 'i2', 'i3', 'i4']),
    );
  });

  it("quick add reads the recipe parser's unit words, units after the number, and no amount for zero", () => {
    // After v1, LOGIC-08.
    expect(parseQuickAdd('3 lbs apples')).toEqual({ name: 'apples', quantity: 3, unit: 'lb' });
    expect(parseQuickAdd('3 lbs. apples')).toEqual({ name: 'apples', quantity: 3, unit: 'lb' });
    expect(parseQuickAdd('2l milk')).toEqual({ name: 'milk', quantity: 2, unit: 'l' });
    expect(parseQuickAdd('500g rice')).toEqual({ name: 'rice', quantity: 500, unit: 'g' });
    expect(parseQuickAdd('2 Litres milk')).toEqual({ name: 'milk', quantity: 2, unit: 'l' });
    expect(parseQuickAdd('1 kilo flour')).toEqual({ name: 'flour', quantity: 1, unit: 'kg' });
    expect(parseQuickAdd('2 fl oz milk')).toEqual({ name: 'milk', quantity: 2, unit: 'fl_oz' });
    expect(parseQuickAdd('0 eggs')).toEqual({ name: 'eggs', quantity: null, unit: null });
    expect(parseQuickAdd('2 lemons')).toEqual({ name: 'lemons', quantity: 2, unit: null });
    expect(parseQuickAdd('2 gherkins')).toEqual({ name: 'gherkins', quantity: 2, unit: null });
    expect(parseQuickAdd('7up')).toEqual({ name: '7up', quantity: null, unit: null });
    expect(parseQuickAdd('2 kg')).toEqual({ name: 'kg', quantity: 2, unit: null });
    expect(parseQuickAdd('2 cups flour')).toEqual({ name: 'cups flour', quantity: 2, unit: null });
  });

  it('increase quantity only adds in the same unit or when one side has none', async () => {
    const rice = { ...item('i1', 'Rice'), quantity: 500, unit: 'g' as const };
    const kilos = parseQuickAdd('2 kg rice');
    expect(canIncrease(kilos, rice)).toBe(false);
    expect(canIncrease(parseQuickAdd('200 g rice'), rice)).toBe(true);
    expect(canIncrease(parseQuickAdd('2 rice'), rice)).toBe(true);
    expect(canIncrease(kilos, { ...rice, unit: null })).toBe(true);
    await expect(service.increase('h', actor, 'g1', rice, kilos)).rejects.toThrow();
    expect(writer.calls).toBe(0);
    await service.increase('h', actor, 'g1', rice, parseQuickAdd('200 g rice'));
    expect(writer.last).toEqual([{ type: 'grocery.addToItem', listId: 'g1', itemId: 'i1', delta: 200 }]);
  });

  it("add to pantry adds to the item's linked product after it was renamed", async () => {
    // After v1, LOGIC-03: "Milk" on the list, the product renamed "Whole milk" since.
    const data = pantry([product('p1', { name: 'Whole milk' })]);
    const low: GroceryItem = { ...item('low_p1', 'Milk'), productId: 'p1', source: 'low_stock' };
    await service.addToPantry('h', actor, 'g1', [entry(low, 2, 7, 'fridge')], [], data);
    expect(ofType(writer.last, 'pantry.createProduct')).toEqual([]);
    const [created] = ofType(writer.last, 'pantry.createBatch');
    expect(created!.batch.productId).toBe('p1');
    expect(created!.batch.productName).toBe('Whole milk');
    // A link to a product that is gone falls back to the name.
    const gone: GroceryItem = { ...item('i2', 'Whole milk'), productId: 'deleted' };
    await service.addToPantry('h', actor, 'g1', [entry(gone, 1, 7, 'fridge')], [], data);
    expect(ofType(writer.last, 'pantry.createBatch')[0]!.batch.productId).toBe('p1');
  });

  it("a product with stock fixes the line's unit, and an amount in another unit isn't used", () => {
    // After v1, LOGIC-04: "2 kg rice" on the list, Rice kept in grams with 500 g in stock.
    const rice = { ...product('rice', { unit: 'g' }), lastAddedQuantity: 1000 };
    const data = pantry([rice], [batch('r1', 'rice', { quantity: 500 })]);
    const kilos: GroceryItem = { ...item('i1', 'Rice'), productId: 'rice', quantity: 2, unit: 'kg' };
    const locked = AddToPantryRules.defaults(kilos, data, TODAY);
    expect(locked).toMatchObject({ unit: 'g', unitLocked: true, quantity: 1000 });
    const noLast = pantry([{ ...rice, lastAddedQuantity: null }], data.batches);
    expect(AddToPantryRules.defaults(kilos, noLast, TODAY).quantity).toBeNull();
    expect(AddToPantryRules.defaults({ ...kilos, quantity: 250, unit: 'g' }, data, TODAY).quantity).toBe(250);
    expect(AddToPantryRules.defaults({ ...kilos, quantity: 3, unit: null }, data, TODAY).quantity).toBe(3);
    const free = AddToPantryRules.defaults(kilos, pantry([rice]), TODAY);
    expect(free).toMatchObject({ unit: 'kg', unitLocked: false, quantity: 2 });
  });

  it('undo of add to pantry removes what it added and puts the items back', async () => {
    // After v1, LOGIC-10.
    const milk = product('milk', { recent: [7] });
    const existing = batch('m1', 'milk', { quantity: 1, inDays: 7 });
    const data = pantry([milk], [existing]);
    const milkItem: GroceryItem = { ...item('i1', 'Milk', { checked: true }), productId: 'milk' };
    const bread1 = item('i2', 'Bread', { checked: true });
    const bread2 = item('i3', 'bread', { checked: true });
    const skipped = item('i4', 'Batteries', { checked: true });
    const undo = await service.addToPantry(
      'h',
      actor,
      'g1',
      [entry(milkItem, 2, 7, 'fridge'), entry(bread1, 1, 3, 'pantry'), entry(bread2, 1, 3, 'pantry')],
      [skipped],
      data,
    );
    const written = writer.last;
    const bread = ofType(written, 'pantry.createProduct')[0]!.product;
    const breadBatch = ofType(written, 'pantry.createBatch')[0]!.batch;
    const updated = ofType(written, 'pantry.updateProduct')[0]!;
    const events = ofType(written, 'pantry.logEvent').map((e) => e.eventId);
    const changes = undo!.changes;
    expect(ofType(changes, 'pantry.deleteBatch').map((c) => c.batchId)).toEqual([breadBatch.id]);
    expect(ofType(changes, 'pantry.addToBatch')).toEqual([{ type: 'pantry.addToBatch', batchId: 'm1', delta: -2 }]);
    expect(ofType(changes, 'pantry.deleteProduct')).toEqual([{ type: 'pantry.deleteProduct', productId: bread.id }]);
    expect(ofType(changes, 'pantry.updateProduct')).toEqual([
      { type: 'pantry.updateProduct', product: milk, before: updated.product },
    ]);
    expect(ofType(changes, 'pantry.deleteEvent').map((c) => c.eventId)).toEqual(events);
    expect(ofType(changes, 'grocery.restoreItem')).toEqual(
      [milkItem, bread1, bread2, skipped].map((i) => ({ type: 'grocery.restoreItem', listId: 'g1', item: i })),
    );
    expect(changes).toHaveLength(4 + events.length + 4);

    const removed = await service.addToPantry('h', actor, 'g1', [], [skipped], data);
    expect(removed!.changes).toEqual([{ type: 'grocery.restoreItem', listId: 'g1', item: skipped }]);
    const calls = writer.calls;
    expect(await service.addToPantry('h', actor, 'g1', [], [], data)).toBeNull();
    expect(writer.calls).toBe(calls);
  });

  it('deleting a list deletes the list first, then its items for real', async () => {
    await service.deleteList('h', actor, { id: 'g2', name: 'Party', isDefault: false, aisleOrder: [] });
    expect(writer.last).toEqual([
      { type: 'grocery.deleteList', listId: 'g2' },
      { type: 'grocery.deleteItem', listId: 'g2', itemId: 'i1', tombstone: false },
      { type: 'grocery.deleteItem', listId: 'g2', itemId: 'i2', tombstone: false },
    ]);
  });

  it('the default list cannot be deleted', async () => {
    await expect(
      service.deleteList('h', actor, { id: 'g1', name: 'Groceries', isDefault: true, aisleOrder: [] }),
    ).rejects.toThrow();
    expect(writer.last).toEqual([]);
  });
});

describe('GroceryLayout', () => {
  it('groups unchecked items by aisle in time order and puts checked ones in the cart', () => {
    const view = GroceryLayout.of([
      item('1', 'Spinach', { category: 'produce', minutesAgo: 1 }),
      item('2', 'Milk', { category: 'dairy', minutesAgo: 5 }),
      item('3', 'Eggs', { category: 'dairy', minutesAgo: 9 }),
      item('4', 'Batteries'),
      item('5', 'Bread', { checked: true }),
    ]);
    expect(view.aisles.map(([c]) => c)).toEqual(['produce', 'dairy', 'other']);
    expect(view.aisles[1]![1].map((i) => i.name)).toEqual(['Eggs', 'Milk']);
    expect(view.inCart.map((i) => i.name)).toEqual(['Bread']);
  });

  it('suggestions come from pantry products and names on the list', () => {
    const data = pantry([product('milk'), product('oat', { name: 'Oat milk' })]);
    // Names that start with the text come first.
    expect(GroceryLayout.suggestions('mil', [item('1', 'Millet')], data)).toEqual(['Milk', 'Millet', 'Oat milk']);
    expect(GroceryLayout.suggestions('', [], data)).toEqual([]);
  });
});
