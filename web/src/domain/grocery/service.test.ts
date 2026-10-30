// Ported from GroceryServiceTest.
import { EMPTY, of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { batch, FIXED_CLOCK, product, TODAY } from '../pantry/fixtures';
import type { Category, PantryChange, PantryData } from '../pantry/models';
import { PantryService } from '../pantry/service';
import { addDays } from '../time';
import type { HouseholdChange, HouseholdWriter } from '../write';
import type { GroceryChange, GroceryItem, GroceryRepository } from './models';
import { GroceryLayout, GroceryService, parseQuickAdd, type PantryEntry } from './service';

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
  observeItems: () => of([]),
  isCached: async () => false,
  itemIds: async () => [],
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
      { type: 'grocery.updateItem', listId: 'g1', itemId: 'i1', quantity: 2, unit: null, note: 'note', category: null },
      {
        type: 'grocery.renameItem',
        listId: 'g1',
        itemId: 'i1',
        name: 'Black beans',
        nameKey: 'black beans',
        productId: 'beans',
      },
    ]);
    // A name no product has keeps the typed spelling and unlinks; the same name writes no rename.
    const none = { quantity: null, unit: null, note: null, category: null };
    await service.edit('h', actor, 'g1', beansItem, { ...none, name: 'Pinto beans' }, [beans]);
    expect(writer.last[1]).toEqual({
      type: 'grocery.renameItem',
      listId: 'g1',
      itemId: 'i1',
      name: 'Pinto beans',
      nameKey: 'pinto beans',
      productId: null,
    });
    await service.edit('h', actor, 'g1', beansItem, { ...none, name: 'Beans' }, [beans]);
    expect(writer.last).toHaveLength(1);
  });

  it('increase adds to an existing quantity or starts from one', async () => {
    await service.increase('h', actor, 'g1', { ...item('i1', 'Eggs'), quantity: 6 }, parseQuickAdd('6 eggs'));
    expect(writer.last).toEqual([{ type: 'grocery.addToItem', listId: 'g1', itemId: 'i1', delta: 6 }]);
    await service.increase('h', actor, 'g1', item('i1', 'Eggs'), parseQuickAdd('eggs'));
    expect(ofType(writer.last, 'grocery.updateItem')[0]!.quantity).toBe(2);
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
