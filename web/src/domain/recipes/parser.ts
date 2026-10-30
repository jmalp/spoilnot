// Best-effort ingredient parsing and servings scaling (DESIGN 5.5, 11.5), ported from IngredientParser and Scaling.
import { ingredient, type Ingredient } from './models';

/** Where the quantity is in the line, so scaling can replace just that part and keep the rest. */
export type Quantity = { value: number; max: number | null; end: number };

const VULGAR: Record<string, number> = {
  '½': 1 / 2,
  '⅓': 1 / 3,
  '⅔': 2 / 3,
  '¼': 1 / 4,
  '¾': 3 / 4,
  '⅕': 1 / 5,
  '⅖': 2 / 5,
  '⅗': 3 / 5,
  '⅘': 4 / 5,
  '⅙': 1 / 6,
  '⅚': 5 / 6,
  '⅛': 1 / 8,
  '⅜': 3 / 8,
  '⅝': 5 / 8,
  '⅞': 7 / 8,
};
const V = Object.keys(VULGAR).join('');
const ONE = `\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:\\.\\d+)?(?:\\s*[${V}])?|\\.\\d+|[${V}]`;
const QUANTITY = new RegExp(`^\\s*(${ONE})(?:\\s*(?:-|–|—|to|or)\\s*(${ONE}))?`, 'i');

/** Canonical unit to the words that mean it (DESIGN 11.5 list, with common spellings). */
export const UNITS: [string, string[]][] = [
  ['cup', ['cups', 'cup', 'c']],
  ['tbsp', ['tablespoons', 'tablespoon', 'tbsps', 'tbsp', 'tbs', 'tbl']],
  ['tsp', ['teaspoons', 'teaspoon', 'tsps', 'tsp']],
  ['kg', ['kilograms', 'kilogram', 'kilos', 'kilo', 'kgs', 'kg']],
  ['g', ['grams', 'gram', 'grammes', 'gramme', 'gr', 'g']],
  ['ml', ['milliliters', 'milliliter', 'millilitres', 'millilitre', 'mls', 'ml']],
  ['l', ['liters', 'liter', 'litres', 'litre', 'l']],
  ['oz', ['ounces', 'ounce', 'oz']],
  ['lb', ['pounds', 'pound', 'lbs', 'lb']],
  ['pinch', ['pinches', 'pinch']],
  ['clove', ['cloves', 'clove']],
  ['can', ['cans', 'can', 'tins', 'tin']],
  ['package', ['packages', 'package', 'packets', 'packet', 'pkgs', 'pkg']],
];
const UNIT_WORD = new Map(UNITS.flatMap(([unit, words]) => words.map((w) => [w, unit] as const)));
const UNIT = new RegExp(
  `^\\s*(${[...UNIT_WORD.keys()].sort((a, b) => b.length - a.length).join('|')})\\.?(?![\\p{L}])`,
  'iu',
);
const PARENS = /\([^)]*\)/g;
const LEADING_PARENS = /^\s*\([^)]*\)/;
const OF = /^of\s+/i;

function fraction(text: string): number | null {
  const parts = text.split('/');
  if (parts.length !== 2) return null;
  const n = Number(parts[0]);
  const d = Number(parts[1]);
  if (parts[1] === '' || !Number.isFinite(d) || d === 0 || parts[0] === '' || !Number.isFinite(n)) return null;
  return n / d;
}

function number(text: string): number | null {
  const t = text.trim();
  const last = t.at(-1);
  const vulgar = last === undefined ? undefined : VULGAR[last];
  if (vulgar !== undefined) {
    const whole = t.slice(0, -1).trim();
    if (whole === '') return vulgar;
    const w = Number(whole);
    return Number.isFinite(w) ? w + vulgar : null;
  }
  const parts = t.split(/\s+/);
  if (parts.length === 2) {
    const w = Number(parts[0]);
    const f = fraction(parts[1]!);
    return Number.isFinite(w) && f !== null ? w + f : null;
  }
  if (t.includes('/')) return fraction(t);
  const value = Number(t);
  return t !== '' && Number.isFinite(value) ? value : null;
}

export const IngredientParser = {
  /**
   * A leading quantity (integers, decimals, unicode fractions, mixed numbers, ranges using the lower value), then a
   * known unit, then the name (text after the first comma and anything in parentheses dropped). Lines without a
   * leading quantity keep only `raw`.
   */
  parse(raw: string, group: string | null = null): Ingredient {
    const line = raw.replaceAll('⁄', '/');
    const quantity = IngredientParser.quantity(line);
    if (!quantity) return ingredient(raw, { group });
    // "1 (14 ounce) can tomatoes": the size in parentheses sits between the quantity and the unit.
    let rest = line.slice(quantity.end).replace(LEADING_PARENS, '');
    const unitMatch = UNIT.exec(rest);
    const unit = unitMatch ? (UNIT_WORD.get(unitMatch[1]!.toLowerCase()) ?? null) : null;
    if (unitMatch) rest = rest.slice(unitMatch[0].length);
    const name = rest.replace(PARENS, ' ').split(',')[0]!.replace(/\s+/g, ' ').trim().replace(OF, '').trim();
    return ingredient(raw, { qty: quantity.value, unit, name: name === '' ? null : name, group });
  },

  /** The leading quantity of [line], or null when it does not start with one. */
  quantity(line: string): Quantity | null {
    const text = line.replaceAll('⁄', '/');
    const match = QUANTITY.exec(text);
    if (!match) return null;
    const value = number(match[1]!);
    if (value === null || value <= 0) return null;
    const max = match[2] ? number(match[2]) : null;
    return { value, max: max !== null && max > value ? max : null, end: match[0].length };
  },
};

const FRACTIONS: [number, string][] = [
  [0, ''],
  [1 / 8, '⅛'],
  [1 / 4, '¼'],
  [1 / 3, '⅓'],
  [3 / 8, '⅜'],
  [1 / 2, '½'],
  [5 / 8, '⅝'],
  [2 / 3, '⅔'],
  [3 / 4, '¾'],
  [7 / 8, '⅞'],
  [1, ''],
];

/** Servings scaling (DESIGN 5.5): only quantities that could be parsed change; the rest of the line is kept. */
export const Scaling = {
  factor: (original: number | null, target: number | null): number =>
    original === null || target === null || original <= 0 ? 1 : target / original,

  line(raw: string, factor: number): string {
    if (Math.abs(factor - 1) < 1e-9) return raw;
    const quantity = IngredientParser.quantity(raw);
    if (!quantity) return raw;
    const scaled =
      Scaling.formatQuantity(quantity.value * factor) +
      (quantity.max !== null ? `–${Scaling.formatQuantity(quantity.max * factor)}` : '');
    return scaled + raw.replaceAll('⁄', '/').slice(quantity.end);
  },

  /** 1.5 becomes "1½", 0.333 becomes "⅓", 12.4 becomes "12"; other values keep up to two decimals. */
  formatQuantity(value: number): string {
    if (value >= 10) return String(Math.round(value));
    const whole = Math.floor(value);
    const rest = value - whole;
    const [nearest, symbol] = FRACTIONS.reduce((best, f) =>
      Math.abs(f[0] - rest) < Math.abs(best[0] - rest) ? f : best,
    );
    if (Math.abs(nearest - rest) < 0.02) {
      const w = whole + (nearest === 1 ? 1 : 0);
      if (symbol === '') return String(w);
      return w === 0 ? symbol : `${w}${symbol}`;
    }
    return value.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
  },
};
