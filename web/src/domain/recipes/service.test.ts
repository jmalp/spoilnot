// Ported from RecipeServiceTest (saving new recipes stays on Android).
import { EMPTY } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import type { GroceryChange, GroceryItem } from '../grocery/models';
import { batch, product } from '../pantry/fixtures';
import type { PantryChange, PantryData } from '../pantry/models';
import { NameKey } from '../pantry/rules';
import type { HouseholdChange, HouseholdWriter } from '../write';
import { recipe } from './fixtures';
import { content } from './models';
import { RecipeGrocery, RecipeService, selectedByDefault } from './service';

class RecordingWriter implements HouseholdWriter {
  applied: HouseholdChange[][] = [];
  private next = 0;
  readonly errors$ = EMPTY;
  newId = () => `id${this.next++}`;
  apply = async (_h: string, _a: unknown, changes: HouseholdChange[]) => {
    this.applied.push(changes);
  };
}

const actor = { uid: 'u1', name: 'Sam' };
const empty: PantryData = { products: [], batches: [], fromCache: false };
const items = (changes: HouseholdChange[]) =>
  (
    changes.filter((c) => c.type === 'grocery.createItem') as Extract<GroceryChange, { type: 'grocery.createItem' }>[]
  ).map((c) => c.item);
const groceryItem = (name: string, checked = false): GroceryItem => ({
  id: name,
  name,
  nameKey: NameKey.of(name),
  quantity: null,
  unit: null,
  note: null,
  category: null,
  checked,
  checkedBy: null,
  source: 'manual',
  productId: null,
  recipeId: null,
  createdBy: null,
  createdAt: null,
  pendingSync: false,
  aliasOf: null,
});

let writer: RecordingWriter;
let service: RecipeService;
beforeEach(() => {
  writer = new RecordingWriter();
  service = new RecipeService(writer);
});

describe('RecipeService', () => {
  it('validation drops bad links, caps tags and requires a title', () => {
    const c = service.validated(
      content('Pie', {
        imageUrl: 'javascript:alert(1)',
        sourceUrl: 'ftp://example.com/pie',
        servings: 0,
        tags: Array.from({ length: 10 }, (_, i) => `t${i + 1}`),
      }),
    );
    expect(c.imageUrl).toBeNull();
    expect(c.sourceUrl).toBeNull();
    expect(c.servings).toBeNull();
    expect(c.tags).toHaveLength(8);
    expect(() => service.validated(content('  '))).toThrow();
  });

  it('delete can be undone with the same recipe', async () => {
    const r = recipe('r1', { title: 'Pie', favorite: true });
    const undo = await service.delete('h1', actor, r);
    expect(writer.applied).toEqual([[{ type: 'recipe.delete', recipeId: 'r1' }]]);
    expect(undo.changes).toEqual([{ type: 'recipe.restore', recipe: r }]);
  });

  it('grocery candidates have shopping names, pantry hints, items already on the list and the recipe as a note', () => {
    const r = recipe('r1', {
      title: 'Pancakes',
      ingredients: ['3 large eggs', '2 tbsp olive oil', '500 g flour', '2 cans tomatoes', 'Salt to taste'],
    });
    const pantry: PantryData = {
      products: [product('egg', { name: 'Eggs' }), product('oil', { name: 'Olive oil', category: 'pantry_staples' })],
      batches: [batch('b1', 'egg', { quantity: 6 }), batch('b2', 'oil')],
      fromCache: false,
    };
    const c = RecipeGrocery.candidates(r, pantry, [groceryItem('Salt'), groceryItem('tomatoes', true)]);
    expect(c.map((x) => x.name)).toEqual(['Eggs', 'Olive oil', 'Flour', 'Tomatoes', 'Salt']);
    expect(c.map((x) => x.product?.id ?? null)).toEqual(['egg', 'oil', null, null, null]);
    expect(c[0]!.inPantry?.id).toBe('egg');
    expect(c[0]!.quantity).toBe(3);
    // Measures are not shopping amounts: they stay in the note.
    expect(c[1]!.quantity).toBeNull();
    expect(c[2]!.quantity).toBeNull();
    expect(c[2]!.unit).toBeNull();
    expect(c[1]!.note).toBe('Pancakes: 2 tbsp olive oil');
    expect(c[3]!.quantity).toBe(2);
    // Checked items are in the cart, not "on the list".
    expect(c.map(selectedByDefault)).toEqual([false, false, true, true, false]);
  });

  it('a linked product brings its name and usual amount', () => {
    const onions = { ...product('onion', { name: 'Onions', unit: 'lb' }), lastAddedQuantity: 3 };
    const soy = { ...product('soy', { name: 'Soy sauce' }), buyQuantity: 1, lastAddedQuantity: 2 };
    const r = recipe('r1', { title: 'Stir fry', ingredients: ['1 extra large onion, diced', '½ tbsp soy sauce'] });
    const c = RecipeGrocery.candidates(r, { products: [onions, soy], batches: [], fromCache: false }, []);
    expect(c.map((x) => x.name)).toEqual(['Onions', 'Soy sauce']);
    expect(c.map((x) => x.quantity)).toEqual([3, 1]); // Buy quantity wins over the last amount
    expect(c.map((x) => x.unit)).toEqual(['lb', 'pcs']);
    expect(c[0]!.note).toBe('Stir fry: 1 extra large onion, diced');
    // Not in stock, so not "probably in pantry", but still linked.
    expect(c.every((x) => x.inPantry === null && selectedByDefault(x))).toBe(true);
  });

  it('an item already on the list for the same product is not added twice', () => {
    const onion = product('onion');
    const listed = { ...groceryItem('onions'), productId: 'onion' };
    const c = RecipeGrocery.candidates(
      recipe('r1', { ingredients: ['1 large onion'] }),
      { products: [onion], batches: [], fromCache: false },
      [listed],
    );
    expect(c[0]!.onList).toBe(true);
  });

  it('added items carry the source Recipe and link pantry products', async () => {
    const r = recipe('r1', { ingredients: ['2 tbsp olive oil', '1 cup rice'] });
    const pantry: PantryData = {
      products: [product('oil', { name: 'Olive oil', category: 'pantry_staples' })],
      batches: [],
      fromCache: false,
    };
    const chosen = RecipeGrocery.candidates(r, pantry, []);
    expect(await service.addToGrocery('h1', actor, 'list1', r, chosen, pantry, 'Groceries')).toBe(2);
    const changes = writer.applied[0]!;
    const added = items(changes);
    // One activity event for the whole add: "added 2 ingredients from r1".
    expect(changes.at(-1)).toMatchObject({ type: 'grocery.logEvent', name: 'r1', listName: 'Groceries', count: 2 });
    expect(added.map((i) => i.name)).toEqual(['Olive oil', 'Rice']);
    expect(added.every((i) => i.source === 'recipe' && i.recipeId === 'r1')).toBe(true);
    expect(added[0]!.productId).toBe('oil');
    expect(added[0]!.category).toBe('pantry_staples');
    expect(added[1]!.productId).toBeNull();
  });

  it('lines from several recipes keep their own recipe and the given source', async () => {
    const tacos = recipe('tacos', { title: 'Tacos', ingredients: ['2 tortillas'] });
    const soup = recipe('soup', { title: 'Soup', ingredients: ['1 leek'] });
    const chosen = [...RecipeGrocery.candidates(tacos, empty, []), ...RecipeGrocery.candidates(soup, empty, [])];
    expect(await service.addItems('h1', actor, 'list1', chosen, empty, 'Groceries', 'meal_plan', 'the meal plan')).toBe(
      2,
    );
    const changes = writer.applied[0]!;
    expect(items(changes).map((i) => i.recipeId)).toEqual(['tacos', 'soup']);
    expect(items(changes).every((i) => i.source === 'meal_plan')).toBe(true);
    expect(changes.at(-1)).toMatchObject({ name: 'the meal plan' });
  });

  it("renaming a line to a pantry product uses it and teaches it the recipe's name", async () => {
    const greenOnions = { ...product('green', { name: 'Green onions' }), lastAddedQuantity: 2 };
    const pantry: PantryData = { products: [greenOnions], batches: [], fromCache: false };
    const r = recipe('r1', { title: 'Stir fry', ingredients: ['3 scallions, sliced'] });
    const original = RecipeGrocery.candidates(r, pantry, [])[0]!;
    expect(original.name).toBe('Scallions');
    expect(original.product).toBeNull();
    const renamed = RecipeGrocery.rename(original, 'green onions', pantry.products);
    expect(renamed).toMatchObject({ name: 'Green onions', product: greenOnions, quantity: 2, aliasOf: 'scallions' });
    await service.addToGrocery('h1', actor, 'list1', r, [renamed], pantry);
    const changes = writer.applied[0]!;
    expect(changes).toContainEqual({
      type: 'pantry.addAlias',
      productId: 'green',
      alias: 'scallions',
    } satisfies PantryChange);
    expect(items(changes)[0]!.aliasOf).toBeNull();
    // Next time the recipe line finds the product by itself.
    const taught = { ...greenOnions, aliases: ['scallions'] };
    const again = RecipeGrocery.candidates(r, { products: [taught], batches: [], fromCache: false }, [])[0]!;
    expect(again.name).toBe('Green onions');
  });

  it('renaming to a new name is remembered on the item until it becomes a product', async () => {
    const r = recipe('r1', { ingredients: ['2 scallions'] });
    const renamed = RecipeGrocery.rename(RecipeGrocery.candidates(r, empty, [])[0]!, 'Spring onions', []);
    expect(renamed.quantity).toBe(2); // the recipe's count still applies
    await service.addToGrocery('h1', actor, 'list1', r, [renamed], empty);
    expect(items(writer.applied[0]!)[0]).toMatchObject({ name: 'Spring onions', aliasOf: 'scallions' });
    // Renaming back to the recipe's own name teaches nothing.
    expect(RecipeGrocery.rename(renamed, 'scallions', []).aliasOf).toBeNull();
  });

  it('lines for the same thing become one item', async () => {
    const r = recipe('r1', { title: 'Salsa', ingredients: ['1 onion, diced', '2 large onions, sliced', '1 tsp salt'] });
    await service.addToGrocery('h1', actor, 'list1', r, RecipeGrocery.candidates(r, empty, []), empty);
    // "Onion" and "Onions" are different names; two onion lines with one name merge.
    expect(items(writer.applied[0]!).map((i) => i.name)).toEqual(['Onion', 'Onions', 'Salt']);
    const soup = recipe('r2', { title: 'Soup', ingredients: ['1 onion, diced', '1 onion, sliced'] });
    const merged = RecipeGrocery.merge(RecipeGrocery.candidates(soup, empty, []));
    expect(merged).toHaveLength(1);
    expect(merged[0]!.quantity).toBe(2);
    expect(merged[0]!.note).toBe('Soup: 1 onion, diced; Soup: 1 onion, sliced');
  });

  it('cooking logs an event that Undo removes', () => {
    const [log, remove] = service.cookedEvent(recipe('r1', { title: 'Curry' }));
    expect(log).toEqual({ type: 'recipe.logCooked', eventId: 'id0', recipeId: 'r1', title: 'Curry' });
    expect(remove).toEqual({ type: 'pantry.deleteEvent', eventId: 'id0' });
  });
});
