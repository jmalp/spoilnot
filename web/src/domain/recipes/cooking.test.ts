// Ported from CookingTest and UseItUpTest.
import { describe, expect, it } from 'vitest';
import { batch, product, TODAY } from '../pantry/fixtures';
import type { PantryData } from '../pantry/models';
import { CookLines, Cooking, RecipeIngredients, UseItUp } from './cooking';
import { recipe } from './fixtures';
import { TEST_WORDS } from './fixtures';

describe('Cooking', () => {
  const eggs = product('egg', { name: 'Eggs' });
  const rice = product('rice', { name: 'Rice', unit: 'g' });
  const soy = product('soy', { name: 'Soy sauce' });
  const milk = product('milk', { name: 'Milk' });
  const pantry: PantryData = {
    products: [eggs, rice, soy, milk],
    batches: [
      batch('e1', 'egg', { quantity: 4 }),
      batch('e2', 'egg', { quantity: 2 }),
      batch('r1', 'rice', { quantity: 900 }),
      batch('s1', 'soy', { quantity: 1 }),
    ],
    fromCache: false,
  };

  it('counts and same-unit amounts are suggested, measures are not', () => {
    const r = recipe('r1', {
      ingredients: ['3 large eggs', '500 g rice', '2 tbsp soy sauce', '1 cup milk', '1 egg, for brushing'],
    });
    const lines = Cooking.lines(r, pantry, TEST_WORDS);
    expect(lines.map((l) => l.product.id)).toEqual(['egg', 'rice', 'soy']); // milk is out of stock
    expect(lines[0]!.suggested).toBe(4); // 3 + 1 eggs
    expect(lines[0]!.lines).toEqual(['3 large eggs', '1 egg, for brushing']);
    expect(lines[0]!.inStock).toBe(6);
    expect(lines[1]!.suggested).toBe(500);
    expect(lines[2]!.suggested).toBeNull();
    expect(CookLines.selectedByDefault(lines[0]!)).toBe(true);
    expect(CookLines.selectedByDefault(lines[2]!)).toBe(false);
    expect(CookLines.defaultAmount(lines[2]!)).toBe(1); // finishing it is the default amount
  });

  it('servings scale the amounts, capped at what is in stock', () => {
    const lines = Cooking.lines(recipe('r1', { ingredients: ['4 eggs'] }), pantry, TEST_WORDS, 2);
    expect(lines[0]!.suggested).toBe(8);
    expect(CookLines.defaultAmount(lines[0]!)).toBe(6);
    expect(lines[0]!.lines).toEqual(['8 eggs']);
  });

  it("scaled pieces are suggested whole, rounded up, and the recipe's own amount as written", () => {
    // After v1, LOGIC-13: 1.5 times "3 eggs" takes 5, not 4.5, like the grocery list.
    const omelette = recipe('r1', { ingredients: ['3 eggs'] });
    expect(Cooking.lines(omelette, pantry, TEST_WORDS, 1.5)[0]!.suggested).toBe(5);
    const half = recipe('r2', { ingredients: ['½ egg'] });
    expect(Cooking.lines(half, pantry, TEST_WORDS)[0]!.suggested).toBe(0.5);
    // Grams are not pieces: 1.5 times 333 g is 499.5 g.
    const grams = recipe('r3', { ingredients: ['333 g rice'] });
    expect(Cooking.lines(grams, pantry, TEST_WORDS, 1.5)[0]!.suggested).toBeCloseTo(499.5, 9);
  });

  it('a tiny scaled amount reads a pinch, not 0', () => {
    const salt = product('salt', { name: 'Salt', unit: 'g' });
    const withSalt: PantryData = {
      products: [salt],
      batches: [batch('s', 'salt', { quantity: 100 })],
      fromCache: false,
    };
    const line = Cooking.lines(recipe('r1', { ingredients: ['1/8 tsp salt'] }), withSalt, TEST_WORDS, 0.1)[0]!;
    expect(line.lines).toEqual(['a pinch of salt']);
  });
});

describe('UseItUp', () => {
  const spinach = product('spinach');
  const yogurt = product('yogurt');
  const rice = product('rice');
  const pantry: PantryData = {
    products: [spinach, yogurt, rice],
    batches: [
      batch('s1', 'spinach', { inDays: 1 }),
      batch('s2', 'spinach', { inDays: 6 }),
      batch('y1', 'yogurt', { inDays: 3 }),
      batch('r1', 'rice', { inDays: 90 }),
    ],
    fromCache: false,
  };

  it('recipes using the most expiring food come first, with the batch that expires first', () => {
    const curry = recipe('curry', {
      title: 'Green curry',
      ingredients: ['2 cups fresh spinach', '1 cup yogurt', '1 cup rice'],
    });
    const salad = recipe('salad', {
      title: 'Spinach salad',
      ingredients: ['4 cups baby spinach leaves', '1 cup spinach'],
    });
    const pilaf = recipe('pilaf', { title: 'Pilaf', ingredients: ['2 cups rice'] });
    const result = UseItUp.suggestions([pilaf, salad, curry], pantry, TODAY);
    expect(result.map((s) => s.recipe.id)).toEqual(['curry', 'salad']);
    expect(result[0]!.uses.map((u) => u.product.id)).toEqual(['spinach', 'yogurt']);
    expect(result[0]!.uses[0]!.batch.id).toBe('s1');
    expect(result[1]!.uses).toHaveLength(1); // "baby spinach leaves" is not Spinach; "spinach" is, once
  });

  it('recipes already planned from today on are left out (UX-05)', () => {
    const curry = recipe('curry', { title: 'Green curry', ingredients: ['2 cups fresh spinach', '1 cup yogurt'] });
    const salad = recipe('salad', { title: 'Spinach salad', ingredients: ['1 cup spinach'] });
    const dip = recipe('dip', { title: 'Yogurt dip', ingredients: ['1 cup yogurt'] });
    const all = [curry, salad, dip];
    expect(UseItUp.suggestions(all, pantry, TODAY).map((s) => s.recipe.id)).toEqual(['curry', 'salad', 'dip']);
    expect(UseItUp.suggestions(all, pantry, TODAY, new Set(['curry'])).map((s) => s.recipe.id)).toEqual([
      'salad',
      'dip',
    ]);
  });

  it('expired food and food with time left are not suggestions', () => {
    const old: PantryData = {
      products: [spinach],
      batches: [batch('s1', 'spinach', { inDays: -1 }), batch('s2', 'spinach', { inDays: 9 })],
      fromCache: false,
    };
    expect(UseItUp.suggestions([recipe('a', { ingredients: ['spinach'] })], old, TODAY)).toEqual([]);
  });

  it('a taught name still links, and the skipped lines change nothing', () => {
    const scallions = { ...product('scallions', { name: 'Scallions' }), aliases: ['green onion'] };
    const data: PantryData = {
      products: [...pantry.products, scallions],
      batches: [...pantry.batches, batch('g1', 'scallions', { inDays: 2 })],
      fromCache: false,
    };
    const stir = recipe('stir', { title: 'Stir fry', ingredients: ['3 green onions, sliced', '1 cup rice', 'salt'] });
    const result = UseItUp.suggestions([stir], data, TODAY);
    expect(result[0]!.uses.map((u) => u.product.id)).toEqual(['scallions']);
    // The same links as matching every line against every product.
    expect(RecipeIngredients.linked(stir, data.products).map((l) => l.product?.id ?? null)).toEqual([
      'scallions',
      'rice',
      null,
    ]);
  });

  it('at most three, favorites first among equals', () => {
    const recipes = [1, 2, 3, 4, 5].map((n) =>
      recipe(`r${n}`, { title: `Recipe ${n}`, favorite: n === 4, ingredients: ['1 cup yogurt'] }),
    );
    expect(UseItUp.suggestions(recipes, pantry, TODAY).map((s) => s.recipe.id)).toEqual(['r4', 'r1', 'r2']);
  });
});
