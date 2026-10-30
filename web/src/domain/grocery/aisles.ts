// Aisles (DESIGN 5.3, after v1), ported from AisleGuess and StoreOrder.
import { CATEGORIES, type Category, type Product } from '../pantry/models';
import { NameKey } from '../pantry/rules';

/** A plural word in singular (PantryMatch.singular), so "tomatoes" finds "tomato". */
export function singular(word: string): string {
  if (word.length <= 3) return word;
  if (word.endsWith('ies')) return `${word.slice(0, -3)}y`;
  if (word.endsWith('oes')) return word.slice(0, -2);
  if (word.endsWith('ches') || word.endsWith('shes') || word.endsWith('sses') || word.endsWith('xes'))
    return word.slice(0, -2);
  if (word.endsWith('ss') || word.endsWith('us') || word.endsWith('is')) return word;
  if (word.endsWith('s')) return word.slice(0, -1);
  return word;
}

const WORD = /[^\p{L}\p{N}]+/u;

/** In order and singular, so phrases can be found. */
const words = (text: string): string[] =>
  NameKey.of(text)
    .split(WORD)
    .filter((w) => w !== '')
    .map(singular);

/** Phrases (singular, lowercase) to aisles. Multi-word phrases win over the words in them. */
const PHRASES = new Map<string, Category>();
const put = (category: Category, ...phrases: string[]) => phrases.forEach((p) => PHRASES.set(p, category));
put(
  'produce',
  ...'apple banana orange lemon lime grape berry strawberry blueberry raspberry avocado tomato potato'.split(' '),
  'sweet potato',
  ...'onion scallion shallot garlic carrot celery lettuce spinach kale arugula cucumber'.split(' '),
  'bell pepper',
  ...'zucchini broccoli cauliflower mushroom cilantro parsley basil mint ginger cabbage pear peach plum mango'.split(
    ' ',
  ),
  ...'pineapple melon watermelon corn squash asparagus'.split(' '),
  'green bean',
  ...'pea radish beet leek jalapeno salad herb fruit vegetable'.split(' '),
);
put(
  'dairy',
  ...'milk cheese yogurt yoghurt butter cream'.split(' '),
  'sour cream',
  'egg',
  'cottage cheese',
  ...'mozzarella cheddar parmesan feta kefir'.split(' '),
  'half and half',
  'creamer',
);
put(
  'meat_fish',
  ...'chicken beef pork turkey bacon sausage ham steak lamb fish salmon'.split(' '),
  'tuna steak',
  ...'cod shrimp prawn'.split(' '),
  'ground beef',
  ...'mince meat chorizo salami'.split(' '),
);
put('bakery', ...'bread bagel bun roll tortilla croissant muffin baguette pita cake loaf brioche naan'.split(' '));
put('frozen', 'frozen', 'ice cream', 'ice', 'frozen pea', 'frozen pizza', 'popsicle');
put(
  'pantry_staples',
  ...'rice pasta spaghetti noodle flour sugar salt'.split(' '),
  'black pepper',
  'oil',
  'olive oil',
  'vinegar',
  'sauce',
  'soy sauce',
  ...'ketchup mustard mayonnaise spice cereal oat bean lentil chickpea broth stock honey jam'.split(' '),
  'peanut butter',
  'coffee',
  'tea',
  'canned',
  'tuna',
  'tomato sauce',
  'coconut milk',
  'baking soda',
  'baking powder',
  'yeast',
  'syrup',
);
put('drinks', 'water', 'sparkling water', 'juice', 'orange juice', 'soda', 'beer', 'wine', 'kombucha');
put(
  'snacks',
  'chip',
  'tortilla chip',
  'potato chip',
  ...'crisp cookie cracker chocolate candy nut popcorn pretzel'.split(' '),
);

const LONGEST_FIRST = [...PHRASES.keys()].sort(
  (a, b) => b.split(' ').length * 100 + b.length - (a.split(' ').length * 100 + a.length),
);

/**
 * The aisle for a grocery item typed without a matching product, so "bananas" goes under Produce. The household's
 * own products come first (the one with the most words, all in the name); then a built-in English word list, longest
 * phrase first, so "peanut butter" is a pantry staple. Unknown names stay without an aisle (Other).
 */
export function guessAisle(name: string, products: Product[] = []): Category | null {
  const typed = words(name);
  if (typed.length === 0) return null;
  let best: Product | null = null;
  let bestSize = 0;
  for (const p of products) {
    const own = words(p.name);
    if (own.length > 0 && own.every((w) => typed.includes(w)) && own.length > bestSize) {
      best = p;
      bestSize = own.length;
    }
  }
  if (best) return best.category;
  const text = ` ${typed.join(' ')} `;
  const phrase = LONGEST_FIRST.find((p) => text.includes(` ${p} `));
  return phrase === undefined ? null : PHRASES.get(phrase)!;
}

/**
 * Aisles in the order the household walks its store, learned from check-offs; each list keeps its own. When the
 * first item of an aisle is checked off on a trip, and the saved order puts that aisle before one already visited,
 * it moves to just after the last visited one. The first aisle of a trip changes nothing.
 */
export const StoreOrder = {
  /** The saved order completed with the remaining aisles in the default order. */
  effective(saved: Category[]): Category[] {
    const unique = [...new Set(saved)];
    return [...unique, ...CATEGORIES.filter((c) => !unique.includes(c))];
  },

  /** The new order after [checked] was visited following [visited], or null when the order already agrees. */
  learn(saved: Category[], visited: Category[], checked: Category): Category[] | null {
    if (visited.includes(checked) || visited.length === 0) return null;
    const order = StoreOrder.effective(saved);
    const lastVisited = Math.max(...visited.map((c) => order.indexOf(c)));
    if (order.indexOf(checked) > lastVisited) return null;
    const without = order.filter((c) => c !== checked);
    const insertAt = without.indexOf(order[lastVisited]!) + 1;
    return [...without.slice(0, insertAt), checked, ...without.slice(insertAt)];
  },

  /** [aisles] in [saved] order. */
  sort<T>(aisles: [Category, T][], saved: Category[]): [Category, T][] {
    const order = StoreOrder.effective(saved);
    return [...aisles].sort((a, b) => order.indexOf(a[0]) - order.indexOf(b[0]));
  },
};

/** One shopping trip on this phone: the aisles in the order their first item was checked off. */
export class ShoppingTrip {
  /** A trip ends after two hours without a check-off, or when another list is used. */
  static readonly GAP_MS = 2 * 60 * 60 * 1000;
  private listId: string | null = null;
  private last: number | null = null;
  private visited: Category[] = [];

  constructor(private readonly now: () => number) {}

  /** Records a check-off and returns the aisles visited before it on this trip. */
  checked(listId: string, category: Category): Category[] {
    const now = this.now();
    const stale = this.last === null || now - this.last > ShoppingTrip.GAP_MS;
    if (stale || this.listId !== listId) {
      this.visited = [];
      this.listId = listId;
    }
    this.last = now;
    const before = [...this.visited];
    if (!this.visited.includes(category)) this.visited.push(category);
    return before;
  }
}
