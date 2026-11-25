// The restore's shapes (after v1, S5) against the rules file, so the two lists of fields can't drift apart.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import { Timestamp } from 'firebase-admin/firestore';
import { FIELDS, idOk, refusal, type Collection } from '../src/shapes.ts';

const rules = readFileSync(new URL('../../../firebase/firestore.rules', import.meta.url), 'utf8');

/** Where each collection's list of allowed fields starts in the rules. */
const ANCHORS: Record<Collection, string> = {
  products: 'function productOk()',
  stock: 'function batchOk()',
  stats: 'function statsOk()',
  groceryLists: 'match /groceryLists/{listId}',
  items: 'function itemOk()',
  recipes: 'function recipeOk()',
  meals: 'function mealOk()',
};

function quoted(text: string): string[] {
  return [...text.matchAll(/'([^']*)'/g)].map((m) => m[1]!);
}

/** The fields the rules allow for [collection]: its `hasOnly([...])`, with the audit fields when it adds them. */
function rulesFields(collection: Collection): string[] {
  const start = rules.indexOf(ANCHORS[collection]);
  assert.ok(start >= 0, `${ANCHORS[collection]} is in the rules`);
  const open = rules.indexOf('hasOnly(', start);
  const close = rules.indexOf(']', open);
  const fields = quoted(rules.slice(open, close));
  if (rules.slice(close, close + 30).startsWith('].concat(auditFields())')) {
    const audit = /function auditFields\(\) \{ return \[([^\]]*)\]/.exec(rules);
    assert.ok(audit, 'auditFields() is in the rules');
    fields.push(...quoted(audit[1]!));
  }
  return fields;
}

describe('the shapes a restore checks (DESIGN 13.4)', () => {
  for (const collection of Object.keys(FIELDS) as Collection[]) {
    it(`allows exactly the fields the rules allow for ${collection}`, () => {
      assert.deepEqual([...FIELDS[collection]].sort(), rulesFields(collection).sort());
    });
  }

  it('accepts what the apps write', () => {
    const at = Timestamp.fromDate(new Date('2026-11-25T12:00:00Z'));
    assert.equal(refusal('products', {
      name: 'Milk', nameKey: 'milk', category: 'Dairy', defaultLocation: 'fridge', unit: 'l', minQuantity: 1,
      autoAddToGrocery: true, buyQuantity: 2, restockArmed: false, typicalShelfLifeDays: 7, recentShelfLifeDays: [7, 6],
      lastAddedQuantity: 1, openedShelfLifeDays: 4, aliases: ['whole milk'], createdAt: at, createdBy: 'u1',
      updatedAt: at, updatedBy: 'u1', deletedAt: null,
    }), null);
    assert.equal(refusal('stock', {
      productId: 'p1', productName: 'Milk', quantity: -1, expiryDate: null, dateType: 'best_before', location: 'other',
      note: '', openedOn: '2026-11-20',
    }), null);
    assert.equal(refusal('items', { name: 'Eggs', checked: true, checkedBy: 'u1', checkedAt: at, source: 'restock' }), null);
    assert.equal(refusal('recipes', {
      title: 'Pie', sourceUrl: 'https://example.com/pie', ingredients: [{ raw: '1 cup flour' }], steps: [], tags: ['easy'],
      servings: 4, rating: 5, importMethod: 'web',
    }), null);
    assert.equal(refusal('meals', {
      date: '2026-11-25', slot: 'lunch', position: 0, recipeId: null, title: 'Soup', servings: null, cooks: ['u1'],
      note: null, cookedAt: at, cookedBy: 'u1',
    }), null);
    assert.equal(refusal('stats', { used: 3, thrownByDay: { '1': 2, '31': 1 }, updatedBy: 'u1' }), null);
    assert.equal(refusal('groceryLists', { name: 'Groceries', isDefault: true, aisleOrder: ['Dairy'] }), null);
    assert.equal(idOk('stats', '2026-11'), true);
    assert.equal(idOk('products', 'aB3xYz'), true);
  });
});
