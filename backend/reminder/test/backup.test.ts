import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import { FORMAT, fromJsonDoc, planRestore, shortId, summarize, toJson, type HouseholdExport } from '../src/backup.ts';

describe('export values', () => {
  it('timestamps become ISO strings and the known timestamp fields come back as timestamps', () => {
    const at = Timestamp.fromDate(new Date('2026-10-17T12:00:00.000Z'));
    const json = toJson({ createdAt: at, expiryDate: '2026-10-20', tags: ['a'], nested: { at }, n: 2, none: null });
    assert.deepEqual(json, {
      createdAt: '2026-10-17T12:00:00.000Z',
      expiryDate: '2026-10-20',
      n: 2,
      nested: { at: '2026-10-17T12:00:00.000Z' },
      none: null,
      tags: ['a'],
    });
    const back = fromJsonDoc(json as Record<string, never>);
    assert.ok(back.createdAt instanceof Timestamp);
    assert.equal((back.createdAt as Timestamp).toMillis(), at.toMillis());
    assert.equal(back.expiryDate, '2026-10-20');
    assert.deepEqual(back.tags, ['a']);
  });

  it('text that looks like a date-time stays text outside the timestamp fields', () => {
    const back = fromJsonDoc({
      note: '2026-12-24T18:00:00Z',
      name: '2026-12-24T18:00:00.000Z',
      steps: [{ text: '2026-12-24T18:00:00Z' }],
      checkedAt: '2026-12-24T18:00:00Z',
      cookedAt: 'not a time',
    });
    assert.equal(back.note, '2026-12-24T18:00:00Z');
    assert.equal(back.name, '2026-12-24T18:00:00.000Z');
    assert.deepEqual(back.steps, [{ text: '2026-12-24T18:00:00Z' }]);
    assert.ok(back.checkedAt instanceof Timestamp);
    assert.equal(back.cookedAt, 'not a time');
  });
});

describe('restore plan', () => {
  const file: HouseholdExport = {
    format: FORMAT,
    formatVersion: 1,
    appVersion: 'test',
    exportedAt: '2026-10-17T12:00:00.000Z',
    household: { id: 'h1', name: 'Home', ownerUid: 'u1' },
    members: [{ id: 'u1', role: 'owner', displayName: 'Sam' }],
    products: [{ id: 'p1', name: 'Milk' }],
    stock: [{ id: 's1', productId: 'p1', quantity: 2, expiryDate: '2026-10-20', location: 'fridge', dateType: 'use_by' }],
    groceryLists: [{ id: 'g1', name: 'Groceries', isDefault: true, items: [{ id: 'i1', name: 'Eggs' }] }],
    recipes: [{ id: 'r1', title: 'Pie', createdAt: '2026-10-01T08:00:00.000Z' }],
    stats: [{ id: '2026-10', used: 4, spoiled: 1, thrownByDay: { '3': 1 } }],
  };

  it('writes products, stock, lists with items, recipes and stats, but never the household or members', () => {
    const plan = planRestore(file, 'h2');
    assert.deepEqual(plan.writes.map((w) => w.path), [
      'households/h2/products/p1',
      'households/h2/stock/s1',
      'households/h2/groceryLists/g1',
      'households/h2/groceryLists/g1/items/i1',
      'households/h2/recipes/r1',
      'households/h2/stats/2026-10',
    ]);
    assert.equal(plan.writes[0]!.data.id, undefined);
    assert.equal(plan.writes[2]!.data.items, undefined);
    assert.ok(plan.writes[4]!.data.createdAt instanceof Timestamp);
    assert.equal(plan.sourceOwnerUid, 'u1');
  });

  it("stamps every document with the server's time and 'restore'; lists with updatedAt only", () => {
    const plan = planRestore({ ...file, stock: [{ ...file.stock[0]!, updatedAt: '2026-10-01T08:00:00.000Z', updatedBy: 'u1' }] }, 'h1');
    for (const w of plan.writes) {
      assert.ok((w.data.updatedAt as FieldValue).isEqual(FieldValue.serverTimestamp()), w.path);
      if (w.path.endsWith('/groceryLists/g1')) assert.equal('updatedBy' in w.data, false, w.path);
      else assert.equal(w.data.updatedBy, 'restore', w.path);
    }
  });

  it('never writes a profile or the activity log from an app export', () => {
    const plan = planRestore({ ...file, profile: { id: 'u1', displayName: 'Sam' }, activity: [{ id: 'a1', type: 'stock_added' }] }, 'h1');
    assert.equal(plan.writes.some((w) => w.path.includes('activity') || w.path.startsWith('users/')), false);
  });

  it('names no owner for a file without its household', () => {
    assert.equal(planRestore({ ...file, household: null }, 'h1').sourceOwnerUid, null);
  });

  it('restores files from before stats existed', () => {
    const { stats: _stats, ...older } = file;
    assert.equal(planRestore(older, 'h2').writes.length, 5);
  });

  it('refuses other files and newer formats', () => {
    assert.throws(() => planRestore({ ...file, format: 'other' } as unknown as HouseholdExport, 'h1'));
    assert.throws(() => planRestore({ ...file, formatVersion: 2 }, 'h1'));
  });

  it('refuses a file whose collections are not lists', () => {
    assert.throws(() => planRestore({ ...file, products: { id: 'p1' } } as unknown as HouseholdExport, 'h1'), {
      code: 'not-an-export',
    });
  });
});

describe('restore checks each document against the rules (after v1, S5)', () => {
  const file: HouseholdExport = {
    format: FORMAT,
    formatVersion: 1,
    appVersion: 'test',
    exportedAt: '2026-11-25T12:00:00.000Z',
    household: { id: 'h1', name: 'Home', ownerUid: 'u1' },
    members: [],
    products: [],
    stock: [],
    groceryLists: [],
    recipes: [],
  };
  const why = (f: Partial<HouseholdExport>) => planRestore({ ...file, ...f }, 'h1').skipped;

  it('writes documents of the right shape, and skips one of the wrong shape with its collection and field', () => {
    const plan = planRestore({
      ...file,
      products: [{ id: 'p1', name: 'Milk' }, { id: 'p2', name: 'x'.repeat(81) }],
      stock: [{ id: 's1', quantity: 1, location: 'fridge', dateType: 'use_by', expiryDate: 'tomorrow' }],
    }, 'h1');
    assert.deepEqual(plan.writes.map((w) => w.path), ['households/h1/products/p1']);
    assert.deepEqual(plan.skipped, [{ collection: 'products', why: 'name' }, { collection: 'stock', why: 'expiryDate' }]);
  });

  it('refuses fields no app writes, and names none of them', () => {
    assert.deepEqual(why({ products: [{ id: 'p1', name: 'Milk', role: 'owner' }] }), [{ collection: 'products', why: 'unknown field' }]);
  });

  it('refuses IDs that would leave their collection', () => {
    for (const id of ['a/b', '..', '.', '__x__', '', 'x'.repeat(1501)]) {
      assert.deepEqual(why({ products: [{ id, name: 'Milk' }] }), [{ collection: 'products', why: 'id' }], id);
    }
    assert.deepEqual(why({ products: [{ name: 'Milk' } as never, 'Milk' as never] }), [
      { collection: 'products', why: 'id' },
      { collection: 'products', why: 'id' },
    ]);
    assert.deepEqual(why({ stats: [{ id: '2026-13', used: 1 }] }), [{ collection: 'stats', why: 'id' }]);
  });

  it('refuses a tombstone, a text where a time belongs and links that are not http', () => {
    assert.deepEqual(why({ products: [{ id: 'p1', name: 'Milk', deletedAt: '2026-11-25T12:00:00.000Z' }] }), [
      { collection: 'products', why: 'deletedAt' },
    ]);
    assert.deepEqual(why({ products: [{ id: 'p1', name: 'Milk', createdAt: 'yesterday' }] }), [
      { collection: 'products', why: 'createdAt' },
    ]);
    assert.deepEqual(why({ recipes: [{ id: 'r1', title: 'Pie', imageUrl: 'data:image/png;base64,AAAA' }] }), [
      { collection: 'recipes', why: 'imageUrl' },
    ]);
    assert.deepEqual(why({ recipes: [{ id: 'r1', title: 'Pie', sourceUrl: 'HTTPS://example.com/pie' }] }), []);
  });

  it('checks lists, items, counts and meals as the rules do', () => {
    assert.deepEqual(why({
      groceryLists: [
        { id: 'g1', name: 'Groceries', isDefault: true, items: [{ id: 'i1', name: 'Eggs', checkedAt: 'now' }] },
        { id: 'g2', name: '', isDefault: false, items: [{ id: 'i2', name: 'Bread' }] },
      ],
      stats: [{ id: '2026-11', used: 1.5 }, { id: '2026-10', thrownByDay: { '32': 1 } }, { id: '2026-09', thrownByDay: { '3': 2 } }],
      meals: [
        { id: 'm1', date: '2026-11-25', slot: 'dinner', position: 0, title: 'Tacos', cooks: ['u1'], cookedBy: 'u1' },
        { id: 'm2', date: '2026-11-25', slot: 'brunch', position: 1, title: 'Eggs', cooks: [] },
        { id: 'm3', date: '2026-11-25', slot: 'lunch', position: 2, title: 'Soup', cooks: [], recipeId: null },
      ],
    }), [
      { collection: 'items', why: 'checkedAt' },
      { collection: 'groceryLists', why: 'name' },
      // Items under a list that isn't written: the rules refuse items under a list that doesn't exist.
      { collection: 'items', why: 'list' },
      { collection: 'stats', why: 'used' },
      { collection: 'stats', why: 'thrownByDay' },
      { collection: 'meals', why: 'cookedAt' },
      { collection: 'meals', why: 'slot' },
    ]);
  });

  it('a list whose items are not a list is skipped with them', () => {
    assert.deepEqual(why({ groceryLists: [{ id: 'g1', name: 'Groceries', isDefault: true, items: 'Eggs' } as never] }), [
      { collection: 'groceryLists', why: 'items' },
    ]);
  });

  it('the summary counts what is skipped and why, never a value', () => {
    const plan = planRestore({ ...file, products: [{ id: 'p1', name: 'Milk' }, { id: 'p2', name: 'Secret', role: 'x' }] }, 'h1');
    const line = summarize(plan);
    assert.equal(line, '1 document(s) {"products":1}; 1 skipped, a shape the rules refuse {"products (unknown field)":1}');
    assert.equal(line.includes('Secret'), false);
  });
});

describe('shortId', () => {
  it('shows only the first four characters of a household ID in public logs', () => {
    assert.equal(shortId('oQS9hyK4LlN9YDjp3kMl'), 'oQS9…');
    assert.equal(shortId('h1'), 'h1');
  });
});
