// Recipe box rules (DESIGN 5.5), ported from RecipeService and RecipeGrocery. Saving new recipes stays on Android.
import { guessAisle } from '../grocery/aisles';
import { GroceryRules, type GroceryChange, type GroceryItem, type ItemSource } from '../grocery/models';
import { PantryLimits, type PantryChange, type PantryData, type Product, type QuantityUnit } from '../pantry/models';
import { NameKey } from '../pantry/rules';
import type { Actor, HouseholdChange, HouseholdWriter, Undo } from '../write';
import { PantryMatch } from './box';
import { RecipeLimits, type Ingredient, type Recipe, type RecipeChange, type RecipeContent } from './models';
import { IngredientParser, Scaling } from './parser';
import { ProductLink, ShoppingName } from './shopping';
import { RecipeText, SourceUrl } from './text';

/** One ingredient in the Add to grocery list sheet. */
export type GroceryCandidate = {
  ingredient: Ingredient;
  /** The shopping name: the linked product's name, or the cleaned ingredient name ("Onion"). */
  name: string;
  /** A shopping amount: the product's usual amount, or a count from the recipe ("3 eggs"); never a measure. */
  quantity: number | null;
  unit: QuantityUnit | null;
  /** Where it comes from, with the recipe's amount: "Pancakes: 1 extra large onion, diced". */
  note: string | null;
  /** The household product this ingredient means, in stock or not; null for a new one. */
  product: Product | null;
  /** "Probably in pantry": pre-unchecked, never decided silently (DESIGN 11.5). */
  inPantry: Product | null;
  /** Already on the list unchecked; also pre-unchecked so it is not added twice. */
  onList: boolean;
  /** The cleaned ingredient name as a name key, before any rename; what a rename teaches. */
  shoppingKey: string;
  /** The recipe's count ("3 eggs"), kept so a renamed item can still use it. */
  recipeCount: number | null;
  /** Set when someone renamed this item: the [shoppingKey] its product should remember. */
  aliasOf: string | null;
  /** The recipe this line comes from; the meal plan's sheet mixes recipes (DESIGN 5.9). */
  recipeId: string | null;
};

export const selectedByDefault = (c: GroceryCandidate) => c.inPantry === null && !c.onList;

/** Units counted as pieces: "2 cans tomatoes" is 2 of an item called tomatoes. */
const COUNTED = new Set<string | null>(['can', 'package', null]);
const MAX_NOTE_LENGTH = 200;

const scaledCount = (count: number, factor: number) =>
  Math.abs(factor - 1) < 1e-9 ? count : Math.ceil(count * factor - 1e-9);

function note(title: string, raw: string): string {
  const text = `${title.trim()}: ${raw.trim()}`.trim();
  return text.length <= MAX_NOTE_LENGTH ? text : `${text.slice(0, MAX_NOTE_LENGTH - 1).trimEnd()}…`;
}

const usualOf = (product: Product | null) => (product ? (product.buyQuantity ?? product.lastAddedQuantity) : null);

/**
 * Recipe ingredients as grocery items (after v1): shopping names, linked to the household's products, with shopping
 * amounts. Recipe measures (a tablespoon, a cup, 200 g) are what the recipe uses, not what is bought, so they stay in
 * the note.
 */
export const RecipeGrocery = {
  /** [factor] scales the recipe: counts are rounded up to whole items ("1½ eggs" is 2). */
  candidates(recipe: Recipe, pantry: PantryData, listItems: GroceryItem[], factor = 1): GroceryCandidate[] {
    const unchecked = listItems.filter((i) => !i.checked);
    const onListKeys = new Set(unchecked.map((i) => i.nameKey));
    const onListProducts = new Set(unchecked.flatMap((i) => (i.productId ? [i.productId] : [])));
    return recipe.content.ingredients.map((ingredient) => {
      const parsed =
        ingredient.qty === null && ingredient.name === null ? IngredientParser.parse(ingredient.raw) : ingredient;
      const cleaned = ShoppingName.of(parsed.name ?? parsed.raw);
      const product = ProductLink.find(cleaned, pantry.products);
      const name = (product?.name ?? cleaned).slice(0, GroceryRules.MAX_NAME_LENGTH).trim();
      const usual = usualOf(product);
      const recipeCount = parsed.qty !== null && COUNTED.has(parsed.unit) ? scaledCount(parsed.qty, factor) : null;
      const count = product === null || product.unit === 'pcs' ? recipeCount : null;
      return {
        ingredient,
        name,
        quantity: usual ?? count,
        unit: usual !== null ? product!.unit : null,
        note: note(recipe.content.title, Scaling.line(ingredient.raw, factor)),
        product,
        inPantry: PantryMatch.productFor(parsed.raw, pantry),
        onList: onListKeys.has(NameKey.of(name)) || (product !== null && onListProducts.has(product.id)),
        shoppingKey: NameKey.of(cleaned),
        recipeCount,
        aliasOf: null,
        recipeId: recipe.id,
      };
    });
  },

  /**
   * [candidate] renamed to [newName] in the sheet: it takes the product of that name, if there is one, with its usual
   * amount, and remembers the recipe's name so next time it is added that way by itself. Naming it back to the
   * recipe's own name forgets the rename.
   */
  rename(candidate: GroceryCandidate, newName: string, products: Product[]): GroceryCandidate {
    const name = NameKey.clean(newName).slice(0, GroceryRules.MAX_NAME_LENGTH).trim();
    if (name === '') return candidate;
    const key = NameKey.of(name);
    const product = products.find((p) => p.nameKey === key) ?? null;
    const usual = usualOf(product);
    const count = product === null || product.unit === 'pcs' ? candidate.recipeCount : null;
    return {
      ...candidate,
      name: product?.name ?? name,
      product,
      quantity: usual ?? count,
      unit: usual !== null ? product!.unit : null,
      aliasOf: candidate.shoppingKey !== key ? candidate.shoppingKey : null,
    };
  },

  /**
   * The items to add for [chosen]: one per shopping name, so "1 onion, diced" and "1 onion, sliced" become one Onion.
   * Quantities add up when both have one in the same unit; notes are joined.
   */
  merge(chosen: GroceryCandidate[]): GroceryCandidate[] {
    const groups = new Map<string, GroceryCandidate[]>();
    for (const c of chosen.filter((c) => c.name !== '')) {
      const key = NameKey.of(c.name);
      groups.set(key, [...(groups.get(key) ?? []), c]);
    }
    return [...groups.values()].map((same) =>
      same.reduce((a, b) => {
        const quantity =
          a.quantity === null
            ? b.quantity
            : b.quantity === null
              ? a.quantity
              : a.unit === b.unit && a.product === null
                ? a.quantity + b.quantity
                : a.quantity; // a product's usual amount covers both
        const notes = [a.note, b.note].filter((n): n is string => n !== null).join('; ');
        return {
          ...a,
          aliasOf: a.aliasOf ?? b.aliasOf,
          quantity,
          note: notes.length <= MAX_NOTE_LENGTH ? notes : `${notes.slice(0, MAX_NOTE_LENGTH - 1)}…`,
        };
      }),
    );
  },
};

type Change = RecipeChange | GroceryChange | PantryChange;

/** Recipe box rules. Plans go to the HouseholdWriter. */
export class RecipeService {
  constructor(private readonly writer: HouseholdWriter) {}

  private write(householdId: string, actor: Actor, changes: Change[]): Promise<void> {
    return this.writer.apply(householdId, actor, changes);
  }

  update(householdId: string, actor: Actor, recipeId: string, content: RecipeContent) {
    return this.write(householdId, actor, [{ type: 'recipe.update', recipeId, content: this.validated(content) }]);
  }

  setFavorite(householdId: string, actor: Actor, recipe: Recipe, favorite: boolean) {
    return this.write(householdId, actor, [{ type: 'recipe.setFavorite', recipeId: recipe.id, favorite }]);
  }

  async delete(householdId: string, actor: Actor, recipe: Recipe): Promise<Undo> {
    await this.write(householdId, actor, [{ type: 'recipe.delete', recipeId: recipe.id }]);
    const restore: Change = { type: 'recipe.restore', recipe };
    return { changes: [restore] };
  }

  /** Adds the chosen ingredients to a grocery list with the source Recipe. Returns how many items. */
  addToGrocery(
    householdId: string,
    actor: Actor,
    listId: string,
    recipe: Recipe,
    chosen: GroceryCandidate[],
    pantry: PantryData,
    listName: string | null = null,
  ): Promise<number> {
    return this.addItems(
      householdId,
      actor,
      listId,
      chosen,
      pantry,
      listName,
      'recipe',
      recipe.content.title,
      recipe.id,
    );
  }

  /**
   * Adds [chosen] lines, from one recipe or several (the meal plan), with [source]. The activity event says they came
   * from [from]. Returns how many items were added.
   */
  async addItems(
    householdId: string,
    actor: Actor,
    listId: string,
    chosen: GroceryCandidate[],
    pantry: PantryData,
    listName: string | null,
    source: ItemSource,
    from: string,
    recipeId: string | null = null,
  ): Promise<number> {
    const merged = RecipeGrocery.merge(chosen);
    // A rename to an existing product is remembered now; one to a new name when it is added to the pantry.
    const aliases = new Map<string, Change>();
    for (const c of merged) {
      const product = c.product;
      const alias = c.aliasOf;
      if (!product || !alias) continue;
      if (alias === product.nameKey || product.aliases.includes(alias)) continue;
      if (product.aliases.length >= PantryLimits.MAX_ALIASES) continue;
      aliases.set(`${product.id}/${alias}`, { type: 'pantry.addAlias', productId: product.id, alias });
    }
    const items = merged.map((c): GroceryItem => {
      const key = NameKey.of(c.name);
      const product = c.product ?? pantry.products.find((p) => p.nameKey === key) ?? null;
      return {
        id: this.writer.newId(),
        name: product?.name ?? c.name,
        nameKey: key,
        quantity: c.quantity,
        unit: c.unit,
        note: c.note,
        category: product?.category ?? c.inPantry?.category ?? guessAisle(c.name, pantry.products),
        checked: false,
        checkedBy: null,
        source,
        productId: product?.id ?? null,
        recipeId: recipeId ?? c.recipeId,
        createdBy: actor.uid,
        createdAt: null,
        pendingSync: false,
        aliasOf: product === null ? c.aliasOf : null,
      };
    });
    if (items.length > 0) {
      const changes: Change[] = [
        ...items.map((item): Change => ({ type: 'grocery.createItem', listId, item })),
        ...aliases.values(),
        {
          type: 'grocery.logEvent',
          eventId: this.writer.newId(),
          event: 'grocery_added',
          name: from,
          listName,
          count: items.length,
        },
      ];
      await this.write(householdId, actor, changes);
    }
    return items.length;
  }

  undo(householdId: string, actor: Actor, undo: Undo) {
    return this.writer.apply(householdId, actor, undo.changes);
  }

  /** The "cooked" activity event for [recipe] and the change that removes it again (Undo). */
  cookedEvent(recipe: Recipe): [HouseholdChange, HouseholdChange] {
    const eventId = this.writer.newId();
    const log: Change = { type: 'recipe.logCooked', eventId, recipeId: recipe.id, title: recipe.content.title };
    const remove: Change = { type: 'pantry.deleteEvent', eventId };
    return [log, remove];
  }

  /** DESIGN 11.6 limits; the title is required. */
  validated(c: RecipeContent): RecipeContent {
    const title = RecipeText.clean(c.title, RecipeLimits.TITLE);
    if (title === null) throw new Error('A recipe needs a title');
    const trimmedOrNull = (text: string | null, limit: number) => text?.trim().slice(0, limit) || null;
    return {
      ...c,
      title,
      sourceUrl: SourceUrl.canonical(c.sourceUrl),
      sourceName: RecipeText.clean(c.sourceName, RecipeLimits.SOURCE_NAME),
      imageUrl: c.imageUrl !== null && SourceUrl.isWebUrl(c.imageUrl.trim()) ? c.imageUrl.trim() : null,
      description: trimmedOrNull(c.description, RecipeLimits.DESCRIPTION),
      servings: c.servings !== null && c.servings >= 1 && c.servings <= 1000 ? c.servings : null,
      servingsText: RecipeText.clean(c.servingsText, RecipeLimits.SERVINGS_TEXT),
      prepMinutes: c.prepMinutes !== null && c.prepMinutes > 0 ? c.prepMinutes : null,
      cookMinutes: c.cookMinutes !== null && c.cookMinutes > 0 ? c.cookMinutes : null,
      totalMinutes: c.totalMinutes !== null && c.totalMinutes > 0 ? c.totalMinutes : null,
      ingredients: c.ingredients
        .filter((i) => i.raw.trim() !== '')
        .slice(0, RecipeLimits.MAX_INGREDIENTS)
        .map((i) => ({ ...i, raw: i.raw.trim().slice(0, RecipeLimits.INGREDIENT) })),
      steps: c.steps
        .filter((s) => s.text.trim() !== '')
        .slice(0, RecipeLimits.MAX_STEPS)
        .map((s) => ({ ...s, text: s.text.trim().slice(0, RecipeLimits.STEP) })),
      notes: trimmedOrNull(c.notes, RecipeLimits.NOTES),
      tags: RecipeText.tags(c.tags),
    };
  }
}
