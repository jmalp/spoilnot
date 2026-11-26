// The shapes the Firestore rules accept for the documents a restore writes (after v1, S5; DESIGN 13.4). The restore
// writes with the Admin SDK, which skips the rules, and a Settings > Export file can be edited on a computer before
// it is restored, so every document is checked here first. Each check mirrors firebase/firestore.rules for that
// collection: the allowed fields, their types and sizes. What the rules check against the writer or the stored
// document (who wrote it, at what time, by how much a count moved) has no meaning for a restore and is left out;
// a field the rules read without a default is accepted when absent, since the apps read it as empty.
// test/shapes.test.ts compares the field lists with the rules file, so the two can't drift apart.
import { Timestamp } from 'firebase-admin/firestore';

/** A document's fields after fromJsonDoc: Firestore values, timestamps included. */
type Fields = Record<string, unknown>;

/** Why a document was refused: a known field's name, `unknown field` or `id`. Never a value from the file. */
export type Refusal = string;

const AUDIT = ['createdAt', 'createdBy', 'updatedAt', 'updatedBy', 'deletedAt'];

export const FIELDS = {
  products: ['name', 'nameKey', 'category', 'defaultLocation', 'unit', 'minQuantity', 'autoAddToGrocery', 'buyQuantity',
    'restockArmed', 'typicalShelfLifeDays', 'recentShelfLifeDays', 'lastAddedQuantity', 'openedShelfLifeDays', 'aliases',
    ...AUDIT],
  stock: ['productId', 'productName', 'unit', 'category', 'quantity', 'expiryDate', 'dateType', 'location', 'note',
    'openedOn', ...AUDIT],
  stats: ['used', 'expired', 'spoiled', 'didnt_like', 'other', 'thrownByDay', 'updatedAt', 'updatedBy'],
  groceryLists: ['name', 'isDefault', 'aisleOrder', 'createdAt', 'updatedAt'],
  items: ['name', 'nameKey', 'quantity', 'unit', 'note', 'category', 'checked', 'checkedBy', 'checkedAt', 'source',
    'productId', 'recipeId', 'aliasOf', ...AUDIT],
  recipes: ['title', 'sourceUrl', 'sourceName', 'imageUrl', 'description', 'servings', 'servingsText', 'prepMinutes',
    'cookMinutes', 'totalMinutes', 'ingredients', 'steps', 'notes', 'tags', 'favorite', 'rating', 'importMethod',
    ...AUDIT],
  meals: ['date', 'slot', 'position', 'recipeId', 'title', 'servings', 'cooks', 'note', 'cookedAt', 'cookedBy', ...AUDIT],
} as const satisfies Record<string, readonly string[]>;

export type Collection = keyof typeof FIELDS;

const LOCATIONS = ['fridge', 'freezer', 'pantry', 'other'];
const MAX_QUANTITY = 1_000_000_000;
const UID = 128;

const absent = (v: unknown) => v === undefined || v === null;
/** Characters as the rules count them (code points). */
const chars = (v: string) => [...v].length;
const str = (v: unknown, lo: number, hi: number) => typeof v === 'string' && chars(v) >= lo && chars(v) <= hi;
const optStr = (v: unknown, lo: number, hi: number) => absent(v) || str(v, lo, hi);
const optNum = (v: unknown, hi: number) => absent(v) || (typeof v === 'number' && Number.isFinite(v) && v >= 0 && v <= hi);
const int = (v: unknown, lo: number, hi: number) => Number.isInteger(v) && (v as number) >= lo && (v as number) <= hi;
const optInt = (v: unknown, lo: number, hi: number) => absent(v) || int(v, lo, hi);
const optBool = (v: unknown) => absent(v) || typeof v === 'boolean';
const isDate = (v: unknown) => typeof v === 'string' && /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(v);
const optDate = (v: unknown) => absent(v) || isDate(v);
const optTime = (v: unknown) => absent(v) || v instanceof Timestamp;
const oneOf = (v: unknown, values: string[]) => typeof v === 'string' && values.includes(v);
/** A list of strings or numbers: at most n elements and total characters together, as the rules' join counts them. */
function optStrList(v: unknown, n: number, total: number): boolean {
  if (absent(v)) return true;
  if (!Array.isArray(v) || v.length > n) return false;
  if (!v.every((e) => typeof e === 'string' || (typeof e === 'number' && Number.isFinite(e)))) return false;
  return chars(v.map(String).join('')) <= total;
}
/** Recipe links and photos: http or https only, up to 2,000 characters (DESIGN 11.3). */
const optWebUrl = (v: unknown) => absent(v) || (str(v, 8, 2000) && /^https?:\/\/.+$/s.test((v as string).toLowerCase()));

type Check = [field: string, ok: (v: unknown, d: Fields) => boolean];

/** The audit fields a restore keeps: createdBy is someone's uid; a restored document is never a tombstone. */
const audit: Check[] = [
  ['createdAt', optTime],
  ['createdBy', (v) => optStr(v, 1, UID)],
  ['deletedAt', absent],
];

const CHECKS: Record<Collection, Check[]> = {
  products: [
    ['name', (v) => str(v, 1, 80)],
    ['nameKey', (v) => optStr(v, 0, 200)],
    ['category', (v) => optStr(v, 1, 40)],
    ['unit', (v) => optStr(v, 1, 20)],
    ['defaultLocation', (v) => absent(v) || oneOf(v, LOCATIONS)],
    ['minQuantity', (v) => optNum(v, MAX_QUANTITY)],
    ['buyQuantity', (v) => optNum(v, MAX_QUANTITY)],
    ['lastAddedQuantity', (v) => optNum(v, MAX_QUANTITY)],
    ['autoAddToGrocery', optBool],
    ['restockArmed', optBool],
    ['typicalShelfLifeDays', (v) => optInt(v, 0, 100_000)],
    ['recentShelfLifeDays', (v) => optStrList(v, 100, 1000)],
    ['aliases', (v) => optStrList(v, 30, 30 * 80)],
    ['openedShelfLifeDays', (v) => optInt(v, 1, 365)],
    ...audit,
  ],
  stock: [
    ['productId', (v) => optStr(v, 1, 100)],
    ['productName', (v) => optStr(v, 1, 80)],
    ['unit', (v) => optStr(v, 1, 20)],
    ['category', (v) => optStr(v, 1, 40)],
    ['quantity', (v) => typeof v === 'number' && Number.isFinite(v) && v >= -MAX_QUANTITY && v <= MAX_QUANTITY],
    ['expiryDate', optDate],
    ['location', (v) => oneOf(v, LOCATIONS)],
    ['dateType', (v) => oneOf(v, ['use_by', 'best_before'])],
    ['note', (v) => optStr(v, 0, 500)],
    ['openedOn', optDate],
    ...audit,
  ],
  stats: [
    ...(['used', 'expired', 'spoiled', 'didnt_like', 'other'] as const).map(
      (f): Check => [f, (v) => absent(v) || int(v, 0, 100_000)],
    ),
    ['thrownByDay', (v) => absent(v) || (isPlainObject(v) && Object.entries(v).every(
      ([day, n]) => /^([1-9]|[12][0-9]|3[01])$/.test(day) && int(n, 0, Number.MAX_SAFE_INTEGER),
    ))],
  ],
  groceryLists: [
    ['name', (v) => str(v, 1, 60)],
    ['isDefault', (v) => typeof v === 'boolean'],
    ['aisleOrder', (v) => optStrList(v, 30, 30 * 40)],
    ['createdAt', optTime],
  ],
  items: [
    ['name', (v) => str(v, 1, 80)],
    ['nameKey', (v) => optStr(v, 0, 200)],
    ['quantity', (v) => optNum(v, MAX_QUANTITY)],
    ['unit', (v) => optStr(v, 1, 20)],
    ['category', (v) => optStr(v, 1, 40)],
    ['note', (v) => optStr(v, 0, 500)],
    ['checked', optBool],
    ['checkedBy', (v) => optStr(v, 1, UID)],
    ['checkedAt', optTime],
    ['source', (v) => optStr(v, 1, 40)],
    ['productId', (v) => optStr(v, 1, 100)],
    ['recipeId', (v) => optStr(v, 1, 100)],
    ['aliasOf', (v) => optStr(v, 1, 80)],
    ...audit,
  ],
  recipes: [
    ['title', (v) => str(v, 1, 200)],
    ['sourceUrl', optWebUrl],
    ['imageUrl', optWebUrl],
    ['sourceName', (v) => optStr(v, 0, 100)],
    ['description', (v) => optStr(v, 0, 2000)],
    ['servings', (v) => optInt(v, 0, 10_000)],
    ['servingsText', (v) => optStr(v, 0, 100)],
    ['prepMinutes', (v) => optInt(v, 0, 1_000_000)],
    ['cookMinutes', (v) => optInt(v, 0, 1_000_000)],
    ['totalMinutes', (v) => optInt(v, 0, 1_000_000)],
    // Lists of maps, which the rules cannot walk: only their number is checked.
    ['ingredients', (v) => absent(v) || (Array.isArray(v) && v.length <= 100)],
    ['steps', (v) => absent(v) || (Array.isArray(v) && v.length <= 100)],
    ['notes', (v) => optStr(v, 0, 5000)],
    ['tags', (v) => optStrList(v, 8, 8 * 40)],
    ['favorite', optBool],
    ['rating', (v) => optInt(v, 0, 100)],
    ['importMethod', (v) => absent(v) || oneOf(v, ['manual', 'web'])],
    ...audit,
  ],
  meals: [
    ['date', isDate],
    ['slot', (v) => oneOf(v, ['breakfast', 'lunch', 'dinner'])],
    ['position', (v) => Number.isInteger(v)],
    ['title', (v) => str(v, 1, 120)],
    ['recipeId', (v) => optStr(v, 1, 100)],
    ['servings', (v) => optInt(v, 1, 1000)],
    ['cooks', (v) => Array.isArray(v) && v.length <= 20 && v.every((c) => typeof c === 'string')
      && chars(v.join('')) <= 20 * UID],
    ['note', (v) => optStr(v, 1, 200)],
    // Cooked: both set or both cleared.
    ['cookedAt', (v, d) => absent(v) ? absent(d.cookedBy) : v instanceof Timestamp && str(d.cookedBy, 1, UID)],
    ['cookedBy', (v, d) => absent(v) === absent(d.cookedAt)],
    ...audit,
  ],
};

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v) && !(v instanceof Timestamp);
}

/**
 * A document ID Firestore accepts as one path segment: no slash, not `.` or `..`, not `__…__`, at most 1,500 bytes.
 * Stats documents are months ('2026-11').
 */
export function idOk(collection: Collection, id: unknown): boolean {
  if (typeof id !== 'string' || id.length === 0 || Buffer.byteLength(id, 'utf8') > 1500) return false;
  if (id.includes('/') || id === '.' || id === '..' || /^__.*__$/.test(id)) return false;
  return collection !== 'stats' || /^[0-9]{4}-(0[1-9]|1[0-2])$/.test(id);
}

/**
 * Null when [fields] (without its id) has a shape the rules accept for [collection]; otherwise why not, as a known
 * field's name or `unknown field`.
 */
export function refusal(collection: Collection, fields: Fields): Refusal | null {
  const allowed: readonly string[] = FIELDS[collection];
  if (Object.keys(fields).some((k) => !allowed.includes(k))) return 'unknown field';
  for (const [field, ok] of CHECKS[collection]) if (!ok(fields[field], fields)) return field;
  return null;
}
