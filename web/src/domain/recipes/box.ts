// The recipe box list, pantry matching and the editor's lines (DESIGN 5.5, 11.5), ported from RecipeBox.
import type { PantryData, Product } from '../pantry/models';
import { NameKey, naturalOrder } from '../pantry/rules';
import { sortedWith } from '../time';
import { RecipeLimits, type Ingredient, type Recipe, type Step } from './models';
import { IngredientParser } from './parser';
import { RecipeText } from './text';

export type RecipeFilter = { kind: 'all' } | { kind: 'favorites' } | { kind: 'tag'; tag: string };
export type RecipeSort = 'recent' | 'aToZ';
export type RecipeQuery = { search: string; filter: RecipeFilter; sort: RecipeSort };
export const DEFAULT_RECIPE_QUERY: RecipeQuery = { search: '', filter: { kind: 'all' }, sort: 'recent' };

function matches(recipe: Recipe, search: string): boolean {
  const c = recipe.content;
  return (
    NameKey.of(c.title).includes(search) ||
    c.tags.some((t) => NameKey.of(t).includes(search)) ||
    c.ingredients.some((i) => NameKey.of(i.raw).includes(search))
  );
}

/** The recipe box list: search by title, ingredient and tag; All, Favorites and tag chips; sort. */
export const RecipeBox = {
  list(recipes: Recipe[], query: RecipeQuery): Recipe[] {
    const search = NameKey.of(query.search);
    const f = query.filter;
    const filtered = recipes.filter((recipe) => {
      const chip =
        f.kind === 'all'
          ? true
          : f.kind === 'favorites'
            ? recipe.favorite
            : recipe.content.tags.some((t) => NameKey.of(t) === NameKey.of(f.tag));
      return chip && (search === '' || matches(recipe, search));
    });
    const byTitle = (a: Recipe, b: Recipe) => naturalOrder(a.content.title, b.content.title);
    if (query.sort === 'aToZ') return sortedWith(filtered, byTitle);
    // Newest first; a recipe not yet on the server (no time) is the newest of all.
    const time = (r: Recipe) => r.createdAt ?? Number.POSITIVE_INFINITY;
    return sortedWith(filtered, (a, b) => time(b) - time(a) || 0, byTitle);
  },

  /** User tags for the chips, the most used first. */
  tags(recipes: Recipe[]): string[] {
    const groups = new Map<string, string[]>();
    for (const tag of recipes.flatMap((r) => r.content.tags)) {
      const key = NameKey.of(tag);
      groups.set(key, [...(groups.get(key) ?? []), tag]);
    }
    return [...groups.entries()]
      .sort((a, b) => b[1].length - a[1].length || (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))
      .map(([, spellings]) => spellings[0]!);
  },
};

const WORD = /[^\p{L}\p{N}]+/u;

/**
 * "Probably in pantry" (DESIGN 11.5): a product matches an ingredient when every word of its nameKey, singularized,
 * appears in the ingredient line. Only a hint; the user decides.
 */
export const PantryMatch = {
  singular(word: string): string {
    if (word.length <= 3) return word;
    if (word.endsWith('ies')) return `${word.slice(0, -3)}y`;
    if (word.endsWith('oes')) return word.slice(0, -2);
    if (word.endsWith('ches') || word.endsWith('shes') || word.endsWith('sses') || word.endsWith('xes'))
      return word.slice(0, -2);
    if (word.endsWith('ss') || word.endsWith('us') || word.endsWith('is')) return word;
    if (word.endsWith('s')) return word.slice(0, -1);
    return word;
  },

  words: (text: string): Set<string> =>
    new Set(
      NameKey.of(text)
        .split(WORD)
        .filter((w) => w !== '')
        .map(PantryMatch.singular),
    ),

  matches(nameKey: string, line: string): boolean {
    const product = PantryMatch.words(nameKey);
    const words = PantryMatch.words(line);
    return product.size > 0 && [...product].every((w) => words.has(w));
  },

  /** The in-stock product that best matches [line] (the one with the most words), if any. */
  productFor(line: string, pantry: PantryData): Product | null {
    const inStock = new Set(pantry.batches.filter((b) => b.quantity > 0).map((b) => b.productId));
    const lineWords = PantryMatch.words(line);
    let best: Product | null = null;
    let bestSize = 0;
    for (const p of pantry.products) {
      if (!inStock.has(p.id)) continue;
      const own = PantryMatch.words(p.nameKey);
      if (own.size > 0 && [...own].every((w) => lineWords.has(w)) && own.size > bestSize) {
        best = p;
        bestSize = own.size;
      }
    }
    return best;
  },
};

/**
 * The editor's one-per-line text (DESIGN 5.5). A line ending in a colon, such as "For the sauce:", starts a group for
 * the lines after it.
 */
export const RecipeLines = {
  ingredientsToText: (ingredients: Ingredient[]): string => toText(ingredients.map((i) => [i.group, i.raw])),
  stepsToText: (steps: Step[]): string => toText(steps.map((s) => [s.group, s.text])),
  ingredients: (text: string): Ingredient[] =>
    grouped(text, RecipeLimits.INGREDIENT)
      .slice(0, RecipeLimits.MAX_INGREDIENTS)
      .map(([group, line]) => IngredientParser.parse(line, group)),
  steps: (text: string): Step[] =>
    grouped(text, RecipeLimits.STEP)
      .slice(0, RecipeLimits.MAX_STEPS)
      .map(([group, line]) => ({ text: line, group })),
  tags: (text: string): string[] => RecipeText.tags([text]),
};

const MAX_GROUP = 80;

function toText(lines: [string | null, string][]): string {
  const out: string[] = [];
  let group: string | null = null;
  for (const [g, line] of lines) {
    if (g !== group && g !== null) out.push(`${g}:`);
    group = g;
    out.push(line);
  }
  return out.join('\n');
}

function grouped(text: string, limit: number): [string | null, string][] {
  let group: string | null = null;
  const out: [string | null, string][] = [];
  for (const raw of text.split(/\r\n|\r|\n/)) {
    const line = RecipeText.clean(raw, limit);
    if (line === null) continue;
    if (line.endsWith(':') && line.length <= MAX_GROUP) {
      group = line.slice(0, -1).trim() || null;
    } else {
      out.push([group, line]);
    }
  }
  return out;
}
