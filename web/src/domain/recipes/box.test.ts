// Ported from RecipeBoxTest, PantryMatchTest, RecipeLinesTest and RecipeFormTest.
import { describe, expect, it } from 'vitest';
import { batch, product } from '../pantry/fixtures';
import { DEFAULT_RECIPE_QUERY, PantryMatch, RecipeBox, RecipeLines, type RecipeQuery } from './box';
import { recipe } from './fixtures';
import { RecipeForms } from './form';
import { content } from './models';

const recipes = [
  recipe('a', { title: 'Banana Bread', tags: ['Baking'], createdSecondsAgo: 300 }),
  recipe('b', { title: 'apple pie', tags: ['Baking', 'Dessert'], favorite: true, createdSecondsAgo: 200 }),
  recipe('c', { title: 'Chili', tags: ['Dinner'], ingredients: ['1 can kidney beans'], createdSecondsAgo: 100 }),
  recipe('d', { title: 'Unsynced soup', createdSecondsAgo: null }),
];
const ids = (query: Partial<RecipeQuery>) =>
  RecipeBox.list(recipes, { ...DEFAULT_RECIPE_QUERY, ...query }).map((r) => r.id);

describe('RecipeBox', () => {
  it('recent first, with recipes not yet on the server at the top', () => {
    expect(ids({})).toEqual(['d', 'c', 'b', 'a']);
  });

  it('A to Z ignores case', () => {
    expect(ids({ sort: 'aToZ' })).toEqual(['b', 'a', 'c', 'd']);
  });

  it('favorites and tag chips', () => {
    expect(ids({ filter: { kind: 'favorites' } })).toEqual(['b']);
    expect(ids({ filter: { kind: 'tag', tag: 'baking' } })).toEqual(['b', 'a']);
  });

  it('search by title, ingredient and tag', () => {
    expect(ids({ search: 'banana' })).toEqual(['a']);
    expect(ids({ search: 'kidney' })).toEqual(['c']);
    expect(ids({ search: 'dessert' })).toEqual(['b']);
    expect(ids({ search: 'pizza' })).toEqual([]);
  });

  it('tags for chips, most used first', () => {
    expect(RecipeBox.tags(recipes)).toEqual(['Baking', 'Dessert', 'Dinner']);
  });
});

describe('PantryMatch', () => {
  it('singular forms', () => {
    expect(PantryMatch.singular('tomatoes')).toBe('tomato');
    expect(PantryMatch.singular('berries')).toBe('berry');
    expect(PantryMatch.singular('eggs')).toBe('egg');
    expect(PantryMatch.singular('peaches')).toBe('peach');
    expect(PantryMatch.singular('hummus')).toBe('hummus');
    expect(PantryMatch.singular('glass')).toBe('glass');
  });

  it('every product word, singularized, must be in the line', () => {
    expect(PantryMatch.matches('egg', '3 large eggs, beaten')).toBe(true);
    expect(PantryMatch.matches('olive oil', '2 tbsp extra-virgin olive oil')).toBe(true);
    expect(PantryMatch.matches('tomatoes', '1 tomato')).toBe(true);
    expect(PantryMatch.matches('olive oil', '2 tbsp vegetable oil')).toBe(false);
    expect(PantryMatch.matches('milk', '1 cup buttermilk')).toBe(false);
  });

  it('only products in stock, the most specific first', () => {
    const pantry = {
      products: [product('oil', { name: 'Oil' }), product('olive', { name: 'Olive oil' }), product('milk')],
      batches: [batch('b1', 'oil'), batch('b2', 'olive'), batch('b3', 'milk', { quantity: 0 })],
      fromCache: false,
    };
    expect(PantryMatch.productFor('3 tbsp olive oil', pantry)?.id).toBe('olive');
    expect(PantryMatch.productFor('1 cup milk', pantry)).toBeNull();
  });
});

describe('RecipeLines', () => {
  it('lines ending in a colon start a group, and round trip', () => {
    const text = 'For the dough:\n2 cups flour\n1 tsp salt\n\nFor the filling:\n3 apples';
    const ingredients = RecipeLines.ingredients(text);
    expect(ingredients.map((i) => i.group)).toEqual(['For the dough', 'For the dough', 'For the filling']);
    expect(ingredients[0]!.qty).toBe(2);
    expect(RecipeLines.ingredientsToText(ingredients)).toBe(text.replace('\n\n', '\n'));
    expect(RecipeLines.steps('Mix.\n  \nBake.')).toEqual([
      { text: 'Mix.', group: null },
      { text: 'Bake.', group: null },
    ]);
  });

  it('tags from a comma-separated field', () => {
    expect(RecipeLines.tags(' Quick, vegan ,, Vegan')).toEqual(['Quick', 'vegan']);
  });
});

describe('RecipeForm', () => {
  const form = (rest: Partial<ReturnType<typeof RecipeForms.empty>>) => ({ ...RecipeForms.empty(), ...rest });

  it('problems with the title, image link and numbers', () => {
    expect(RecipeForms.problems(RecipeForms.empty())).toEqual(new Set(['titleMissing']));
    expect(RecipeForms.problems(form({ title: 'Pie', imageUrl: 'file:///x.jpg', servings: 'four' }))).toEqual(
      new Set(['imageUrl', 'number']),
    );
    expect(RecipeForms.problems(form({ title: 'Pie', servings: '4' }))).toEqual(new Set());
  });

  it('total is prep plus cook when left blank, and hidden fields are kept', () => {
    const base = content('Old', { sourceUrl: 'https://example.com/pie', importMethod: 'web' });
    const c = RecipeForms.toContent(
      form({ title: ' Pie ', prepMinutes: '10', cookMinutes: '35', ingredients: '1 cup sugar' }),
      base,
    );
    expect(c.title).toBe('Pie');
    expect(c.totalMinutes).toBe(45);
    expect(c.sourceUrl).toBe('https://example.com/pie');
    expect(c.importMethod).toBe('web');
    expect(c.ingredients[0]!.unit).toBe('cup');
  });

  it('a form made from content gives the same content back', () => {
    const c = content('Curry', {
      servings: 4,
      servingsText: '4 servings',
      totalMinutes: 40,
      ingredients: RecipeLines.ingredients('Sauce:\n1 cup coconut milk'),
      steps: RecipeLines.steps('Cook:\nSimmer.'),
      notes: 'Spicy',
      tags: ['Dinner', 'Thai'],
    });
    expect(RecipeForms.toContent(RecipeForms.from(c), c)).toEqual(c);
  });
});
