// The web's field limits against firebase/firestore.rules, which decide what the server accepts (DESIGN 8.1; after v1,
// review B27, CODE-04). Ported from FieldLimitsTest: each field's limit is read from the rules, and the test fails
// when the web's constant disagrees, or writes more than the rules accept where the web writes less (notes, a
// recipe's servings).
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { AccountValidation } from './account';
import { ReadCaps } from './caps';
import { GroceryRules } from './grocery/models';
import { HouseholdRules } from './household';
import { PantryLimits } from './pantry/models';
import { MealPlanLimits } from './plan/models';
import { RecipeLimits } from './recipes/models';

const rules = readFileSync(join('..', 'firebase', 'firestore.rules'), 'utf8');

const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** The rules of `match /<path>/{…}`, up to the next `match` (a nested collection's rules come after its parent's). */
function block(path: string): string {
  const start = rules.indexOf(`match /${path}/{`);
  if (start < 0) throw new Error(`no match /${path} in the rules`);
  const end = rules.indexOf('match /', start + 1);
  return rules.slice(start, end > 0 ? end : rules.length);
}

/** Every match of `patterns` in `path`'s rules: at least one, and all the same. */
function single(path: string, field: string, ...patterns: RegExp[]): number {
  const text = block(path);
  const found = new Set(patterns.flatMap((p) => [...text.matchAll(p)].map((m) => Number(m[1]))));
  if (found.size !== 1) throw new Error(`expected one limit for ${path}.${field} in the rules, found ${[...found]}`);
  return [...found][0]!;
}

/** The longest string the rules accept: `s(d.field, lo, hi)` or `optS(d, 'field', lo, hi)`. */
const text = (path: string, field: string) =>
  single(
    path,
    field,
    new RegExp(String.raw`\bs\([\w.]+\.${escape(field)}, \d+, (\d+)\)`, 'g'),
    new RegExp(String.raw`optS\([\w.]+, '${escape(field)}', \d+, (\d+)\)`, 'g'),
  );

/** The largest number the rules accept: `optInt(d, 'field', lo, hi)` or `x.field <= hi`. */
const number = (path: string, field: string) =>
  single(
    path,
    field,
    new RegExp(String.raw`optInt\([\w.]+, '${escape(field)}', \d+, (\d+)\)`, 'g'),
    new RegExp(String.raw`\.${escape(field)} <= (\d+)`, 'g'),
  );

/** How many entries the rules accept, and in total characters for a list of strings. */
function list(path: string, field: string): { count: number; total: number | null } {
  const f = escape(field);
  const strings = new RegExp(String.raw`optStrList\([\w.]+, '${f}', (\d+), ([\d *]+)\)`).exec(block(path));
  if (strings) {
    const total = strings[2]!.split('*').reduce((product, n) => product * Number(n.trim()), 1);
    return { count: Number(strings[1]), total };
  }
  const count = single(
    path,
    field,
    new RegExp(String.raw`\.get\('${f}', \[\]\)\.size\(\) <= (\d+)`, 'g'),
    new RegExp(String.raw`\.${f}\.size\(\) <= (\d+)`, 'g'),
  );
  return { count, total: null };
}

/** A recipe link's or photo's longest accepted length (`optWebUrl`). */
function webUrl(): number {
  const fn = rules.slice(rules.indexOf('function optWebUrl')).split('}')[0]!;
  return Number(/\bs\(d\[f\], \d+, (\d+)\)/.exec(fn)![1]);
}

describe('field limits match the rules', () => {
  it('reads the rules', () => {
    // The parser finds what it looks for, so a renamed rule fails here rather than passing unchecked.
    expect(text('products', 'name')).toBe(80);
    expect(list('products', 'aliases')).toEqual({ count: 30, total: 30 * 80 });
    expect(number('meals', 'servings')).toBe(1000);
  });

  it('people and households', () => {
    expect(AccountValidation.MAX_DISPLAY_NAME_LENGTH).toBe(text('users', 'displayName'));
    expect(AccountValidation.MAX_DISPLAY_NAME_LENGTH).toBe(text('joinRequests', 'displayName'));
    expect(ReadCaps.PERSON).toBe(text('members', 'displayName'));
    expect(ReadCaps.PERSON).toBe(text('activity', 'actorName'));
    expect(HouseholdRules.MAX_NAME_LENGTH).toBe(text('households', 'name'));
    expect(HouseholdRules.MAX_NAME_LENGTH).toBe(text('inviteCodes', 'householdName'));
    expect(ReadCaps.LIST_NAME).toBe(text('households', 'name'));
  });

  it('pantry', () => {
    expect(PantryLimits.MAX_NAME_LENGTH).toBe(text('products', 'name'));
    expect(PantryLimits.MAX_NAME_LENGTH).toBe(text('stock', 'productName'));
    expect(ReadCaps.NAME).toBe(text('products', 'name'));
    expect(PantryLimits.MAX_ALIASES).toBe(list('products', 'aliases').count);
    expect(PantryLimits.MAX_ALIASES * PantryLimits.MAX_NAME_LENGTH).toBe(list('products', 'aliases').total);
    expect(PantryLimits.MAX_OPENED_DAYS).toBe(number('products', 'openedShelfLifeDays'));
    expect(PantryLimits.MAX_NOTE_LENGTH).toBeLessThanOrEqual(text('stock', 'note'));
  });

  it('groceries', () => {
    expect(GroceryRules.MAX_LIST_NAME_LENGTH).toBe(text('groceryLists', 'name'));
    expect(ReadCaps.LIST_NAME).toBe(text('groceryLists', 'name'));
    expect(GroceryRules.MAX_LIST_NAME_LENGTH).toBe(text('activity', 'listName'));
    expect(GroceryRules.MAX_NAME_LENGTH).toBe(text('items', 'name'));
    expect(PantryLimits.MAX_NAME_LENGTH).toBe(text('items', 'aliasOf'));
    expect(GroceryRules.MAX_NOTE_LENGTH).toBeLessThanOrEqual(text('items', 'note'));
  });

  it('recipes', () => {
    expect(RecipeLimits.TITLE).toBe(text('recipes', 'title'));
    expect(ReadCaps.RECIPE_TITLE).toBe(text('recipes', 'title'));
    // An event's name is at most a recipe's title (recipe_saved, recipe_cooked).
    expect(RecipeLimits.TITLE).toBe(text('activity', 'name'));
    expect(RecipeLimits.SOURCE_NAME).toBe(text('recipes', 'sourceName'));
    expect(RecipeLimits.DESCRIPTION).toBe(text('recipes', 'description'));
    expect(RecipeLimits.SERVINGS_TEXT).toBe(text('recipes', 'servingsText'));
    expect(RecipeLimits.NOTES).toBe(text('recipes', 'notes'));
    expect(RecipeLimits.MAX_INGREDIENTS).toBe(list('recipes', 'ingredients').count);
    expect(RecipeLimits.MAX_STEPS).toBe(list('recipes', 'steps').count);
    expect(RecipeLimits.MAX_TAGS).toBe(list('recipes', 'tags').count);
    expect(RecipeLimits.MAX_TAGS * RecipeLimits.TAG).toBe(list('recipes', 'tags').total);
    expect(RecipeLimits.URL).toBe(webUrl());
    expect(RecipeLimits.MAX_SERVINGS).toBeLessThanOrEqual(number('recipes', 'servings'));
  });

  it('meals', () => {
    expect(MealPlanLimits.TITLE).toBe(text('meals', 'title'));
    expect(ReadCaps.MEAL_TITLE).toBe(text('meals', 'title'));
    expect(MealPlanLimits.NOTE).toBe(text('meals', 'note'));
    expect(MealPlanLimits.MAX_COOKS).toBe(list('meals', 'cooks').count);
    expect(MealPlanLimits.MAX_SERVINGS).toBe(number('meals', 'servings'));
  });
});
