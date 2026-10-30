// What an ingredient is called on a grocery list and which product it means (DESIGN 5.5, after v1), ported from
// ShoppingName and ProductLink.
import type { Product } from '../pantry/models';
import { NameKey } from '../pantry/rules';
import { PantryMatch } from './box';

/** Size words, dropped only at the start ("large eggs"). */
const SIZE = [
  'extra-large',
  'extra large',
  'x-large',
  'xl',
  'large',
  'medium',
  'small',
  'big',
  'jumbo',
  'heaping',
  'heaped',
  'level',
  'generous',
];
/** Preparation and freshness words, dropped at the start or the end ("onion finely chopped"). */
const PREP = [
  ...'fresh freshly ripe chopped diced minced sliced grated shredded peeled crushed finely roughly coarsely thinly'.split(
    ' ',
  ),
  ...'thickly cubed halved quartered mashed melted softened beaten toasted cooked uncooked drained rinsed trimmed'.split(
    ' ',
  ),
  ...'pitted seeded deseeded deveined boneless skinless packed lightly firmly sifted room-temperature'.split(' '),
  'room temperature',
  'about',
  'approximately',
  'and',
];
const LEADING = new Set([...SIZE, ...PREP]);
const TRAILING = new Set(PREP);
/** Phrases that end the name: "salt to taste", "parsley for garnish", "butter or margarine". */
const TAIL = /\s+(?:to taste|for (?:garnish|serving|the \w+)|as needed|if needed|optional|divided|plus more.*|or .*)$/i;
const PARENS = /\([^)]*\)/g;
const EDGE_PUNCTUATION = /^[\s,.;:*-]+|[\s,.;:*-]+$/g;

function dropLeading(words: string[], drop: Set<string>): string[] {
  let i = 0;
  while (i < words.length - 1) {
    const two = `${words[i]} ${words[i + 1]}`.toLowerCase();
    if (drop.has(two) && i + 2 < words.length) i += 2;
    else if (drop.has(words[i]!.toLowerCase())) i += 1;
    else return words.slice(i);
  }
  return words.slice(i);
}

function dropTrailing(words: string[], drop: Set<string>): string[] {
  let end = words.length;
  while (end > 1 && drop.has(words[end - 1]!.toLowerCase())) end--;
  return words.slice(0, end);
}

/**
 * "1 extra-large onion, finely diced" is bought as "Onion": size, preparation and freshness words around the name are
 * dropped, as is anything after a comma, in parentheses, after "or" and phrases such as "to taste". Words that change
 * what is bought (ground, whole, frozen, baby, hot) stay.
 */
export const ShoppingName = {
  of(name: string): string {
    let text = name.replace(PARENS, ' ').split(',')[0]!.replace(/\s+/g, ' ').trim();
    text = text.replace(TAIL, '').replace(EDGE_PUNCTUATION, '');
    let words = text.split(' ').filter((w) => w !== '');
    words = dropLeading(words, LEADING);
    words = dropTrailing(words, TRAILING);
    const result = words.join(' ').replace(EDGE_PUNCTUATION, '');
    const cleaned = result || NameKey.clean(name);
    return cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
  },
};

/** The household's products with their words worked out once, for linking many ingredients. */
export class ProductIndex {
  readonly byKey = new Map<string, Product>();
  readonly aliases: [Product, [string, Set<string>][]][];
  readonly words = new Map<Product, Set<string>>();

  constructor(products: Product[]) {
    for (const p of products) if (!this.byKey.has(p.nameKey)) this.byKey.set(p.nameKey, p);
    this.aliases = products
      .filter((p) => p.aliases.length > 0)
      .map((p) => [p, p.aliases.map((a): [string, Set<string>] => [a, PantryMatch.words(a)])]);
    for (const p of products) {
      const w = PantryMatch.words(p.nameKey);
      if (w.size > 0) this.words.set(p, w);
    }
  }

  /** Every word that could link an ingredient to [product]: its name's and its aliases'. */
  linkWords(product: Product): Set<string> {
    return new Set([...(this.words.get(product) ?? []), ...product.aliases.flatMap((a) => [...PantryMatch.words(a)])]);
  }
}

const sameSet = (a: Set<string>, b: Set<string>) => a.size === b.size && [...a].every((w) => b.has(w));

/**
 * The household product a recipe ingredient means, in stock or not. Names the household taught a product (aliases)
 * come first. Otherwise words are compared singular, and a product matches when its words are all in the ingredient
 * with at most one more; the most shared words win. A product with more words than the ingredient never matches.
 */
export const ProductLink = {
  find(name: string, products: Product[] | ProductIndex, wanted?: Set<string>): Product | null {
    const index = products instanceof ProductIndex ? products : new ProductIndex(products);
    const want = wanted ?? PantryMatch.words(name);
    const key = NameKey.of(name);
    const exact = index.byKey.get(key);
    if (exact) return exact;
    if (want.size === 0) return null;
    // A name someone taught a product, by renaming a recipe item, wins over a word match.
    const taught = index.aliases.find(([, aliases]) => aliases.some(([alias, w]) => alias === key || sameSet(w, want)));
    if (taught) return taught[0];
    let best: Product | null = null;
    let bestSize = -1;
    for (const [product, w] of index.words) {
      if (![...w].every((x) => want.has(x)) || want.size - w.size > 1) continue;
      if (w.size > bestSize || (w.size === bestSize && best !== null && product.nameKey < best.nameKey)) {
        best = product;
        bestSize = w.size;
      }
    }
    return best;
  },
};
