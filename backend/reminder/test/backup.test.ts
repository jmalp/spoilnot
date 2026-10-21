import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { Timestamp } from 'firebase-admin/firestore';
import { FORMAT, fromJson, planRestore, shortId, toJson, type HouseholdExport } from '../src/backup.ts';

describe('export values', () => {
  it('timestamps become ISO strings and come back as timestamps; calendar dates stay strings', () => {
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
    const back = fromJson(json) as Record<string, unknown>;
    assert.ok(back.createdAt instanceof Timestamp);
    assert.equal((back.createdAt as Timestamp).toMillis(), at.toMillis());
    assert.equal(back.expiryDate, '2026-10-20');
    assert.ok((back.nested as Record<string, unknown>).at instanceof Timestamp);
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
    stock: [{ id: 's1', productId: 'p1', quantity: 2, expiryDate: '2026-10-20' }],
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
  });

  it('restores files from before stats existed', () => {
    const { stats: _stats, ...older } = file;
    assert.equal(planRestore(older, 'h2').writes.length, 5);
  });

  it('refuses other files and newer formats', () => {
    assert.throws(() => planRestore({ ...file, format: 'other' } as unknown as HouseholdExport, 'h1'));
    assert.throws(() => planRestore({ ...file, formatVersion: 2 }, 'h1'));
  });
});

describe('shortId', () => {
  it('shows only the first four characters of a household ID in public logs', () => {
    assert.equal(shortId('oQS9hyK4LlN9YDjp3kMl'), 'oQS9…');
    assert.equal(shortId('h1'), 'h1');
  });
});
