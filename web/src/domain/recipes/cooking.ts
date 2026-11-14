// "I cooked this" and "Use it up" (DESIGN 5.5, 5.6, after v1), ported from Cooking and UseItUp.
import type { PantryData, Product, QuantityUnit, StockBatch } from '../pantry/models';
import { NeedsAttention } from '../pantry/items';
import { Expiry } from '../pantry/rules';
import { compareStrings, sortedWith, type LocalDate } from '../time';
import { PantryMatch } from './box';
import type { Ingredient, Recipe, RecipeWords } from './models';
import { IngredientParser, Scaling } from './parser';
import { ProductIndex, ProductLink, ShoppingName } from './shopping';

/** A recipe ingredient and the household product it means, if any. */
export type LinkedIngredient = { ingredient: Ingredient; product: Product | null };

const parsedOf = (i: Ingredient): Ingredient => (i.qty === null && i.name === null ? IngredientParser.parse(i.raw) : i);
const shoppingName = (i: Ingredient): string => {
  const parsed = parsedOf(i);
  return ShoppingName.of(parsed.name ?? parsed.raw);
};

/** Recipe lines matched to the household's products, the same way the grocery sheet does. */
export const RecipeIngredients = {
  /**
   * With [only] set, lines sharing no word with it are not looked up: every link needs a shared word, so they could
   * not link to anything in it anyway.
   */
  linked(recipe: Recipe, products: Product[] | ProductIndex, only: Set<string> | null = null): LinkedIngredient[] {
    const index = products instanceof ProductIndex ? products : new ProductIndex(products);
    return recipe.content.ingredients.map((ingredient) => {
      const name = shoppingName(ingredient);
      const wanted = PantryMatch.words(name);
      const skip = only !== null && ![...wanted].some((w) => only.has(w));
      return { ingredient, product: skip ? null : ProductLink.find(name, index, wanted) };
    });
  },
};

/**
 * One pantry product a recipe uses, in the "I cooked this" sheet. [suggested] is how much to take off, in the
 * product's unit, when the recipe says it in a way the pantry can count ("3 eggs", "500 g rice" from rice in grams);
 * otherwise the line starts unchosen with the whole stock, so choosing it means "I finished it".
 */
export type CookLine = {
  product: Product;
  /** The recipe lines that mean this product ("2 eggs" and "1 egg, for brushing"). */
  lines: string[];
  /** In stock now, in the product's unit. */
  inStock: number;
  suggested: number | null;
};

export const CookLines = {
  selectedByDefault: (line: CookLine) => line.suggested !== null,
  defaultAmount: (line: CookLine) => (line.suggested !== null ? Math.min(line.suggested, line.inStock) : line.inStock),
};

const SAME_UNIT: Record<string, QuantityUnit> = { g: 'g', kg: 'kg', ml: 'ml', l: 'l', oz: 'oz', lb: 'lb' };
const COUNTED = new Set<string | null>(['can', 'package', null]);

function amount(i: Ingredient, product: Product): number | null {
  const parsed = parsedOf(i);
  if (parsed.qty === null) return null;
  if (COUNTED.has(parsed.unit) && product.unit === 'pcs') return parsed.qty;
  if (parsed.unit !== null && SAME_UNIT[parsed.unit] === product.unit) return parsed.qty;
  return null;
}

/** Scaled pieces rounded up, ignoring floating-point dust: 4.5 is 5, 3.0000000001 is 3. */
const wholePieces = (value: number, factor: number): number =>
  Math.abs(factor - 1) < 1e-9 ? value : Math.ceil(value - 1e-9);

export const Cooking = {
  /**
   * The in-stock products [recipe] uses, at [factor] times its servings (the detail screen's stepper), in recipe
   * order. Lines that mean the same product add up. Scaled, a product kept in pieces is suggested in whole pieces,
   * rounded up like the grocery list ("4½ eggs" takes 5, after v1, LOGIC-13); at the recipe's own servings the amount
   * is as written ("½ onion"). [words] give tiny scaled amounts their text (Scaling.line).
   */
  lines(recipe: Recipe, pantry: PantryData, words: RecipeWords, factor = 1): CookLine[] {
    const stock = new Map<string, number>();
    for (const b of pantry.batches)
      if (b.quantity > 0) stock.set(b.productId, (stock.get(b.productId) ?? 0) + b.quantity);
    const groups = new Map<string, { product: Product; ingredients: Ingredient[] }>();
    for (const { ingredient, product } of RecipeIngredients.linked(recipe, pantry.products)) {
      if (!product || !stock.has(product.id)) continue;
      const group = groups.get(product.id) ?? { product, ingredients: [] };
      group.ingredients.push(ingredient);
      groups.set(product.id, group);
    }
    return [...groups.values()].map(({ product, ingredients }) => {
      const amounts = ingredients.map((i) => amount(i, product));
      const total = amounts.every((a) => a !== null) ? amounts.reduce((s, a) => s + a!, 0) * factor : null;
      return {
        product,
        lines: ingredients.map((i) => Scaling.line(i.raw, factor, words.pinch)),
        inStock: stock.get(product.id)!,
        suggested: total !== null && product.unit === 'pcs' ? wholePieces(total, factor) : total,
      };
    });
  },
};

/** One expiring product a suggested recipe uses, with the batch that expires first. */
export type ExpiringUse = { product: Product; batch: StockBatch };

/** A saved recipe that uses food expiring soon. */
export type UseItUpSuggestion = { recipe: Recipe; uses: ExpiringUse[] };

/**
 * "Use it up" on Home: saved recipes that use what expires today or in the next few days, the ones using the most
 * expiring products first, then the most urgent. Expired food is left out: it belongs in Needs attention. Recipes
 * already planned from today to the end of the week ([planned], `MealWeek.plannedFrom`) are left out too (after v1,
 * UX-05).
 */
export const UseItUp = {
  /** At most this many recipes on Home. */
  MAX: 3,

  suggestions(
    recipes: Recipe[],
    pantry: PantryData,
    today: LocalDate,
    planned: ReadonlySet<string> = new Set(),
  ): UseItUpSuggestion[] {
    const expiring = new Map<string, StockBatch>();
    for (const b of pantry.batches) {
      if (b.quantity <= 0 || b.expiryDate === null) continue;
      const status = Expiry.of(b.expiryDate, today);
      if (!NeedsAttention.usable(status)) continue;
      const first = expiring.get(b.productId);
      if (!first || b.expiryDate < first.expiryDate!) expiring.set(b.productId, b);
    }
    if (expiring.size === 0) return [];
    const index = new ProductIndex(pantry.products);
    // Only lines that share a word with an expiring product are linked (for large recipe boxes).
    const expiringWords = new Set(
      pantry.products.filter((p) => expiring.has(p.id)).flatMap((p) => [...index.linkWords(p)]),
    );
    const suggestions = recipes.flatMap((recipe): UseItUpSuggestion[] => {
      if (planned.has(recipe.id)) return [];
      const seen = new Set<string>();
      const uses: ExpiringUse[] = [];
      for (const { product } of RecipeIngredients.linked(recipe, index, expiringWords)) {
        const batch = product && !seen.has(product.id) ? expiring.get(product.id) : undefined;
        if (product) seen.add(product.id);
        if (product && batch) uses.push({ product, batch });
      }
      const sorted = sortedWith(uses, (a, b) => compareStrings(a.batch.expiryDate!, b.batch.expiryDate!));
      return sorted.length === 0 ? [] : [{ recipe, uses: sorted }];
    });
    return sortedWith(
      suggestions,
      (a, b) => b.uses.length - a.uses.length,
      (a, b) => compareStrings(a.uses[0]!.batch.expiryDate!, b.uses[0]!.batch.expiryDate!),
      (a, b) => Number(b.recipe.favorite) - Number(a.recipe.favorite),
      (a, b) => compareStrings(a.recipe.content.title.toLowerCase(), b.recipe.content.title.toLowerCase()),
    ).slice(0, UseItUp.MAX);
  },
};
