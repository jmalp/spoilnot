// Export and restore against the Firestore emulator (DESIGN 13.4, M7).
import assert from 'node:assert/strict';
import { after, before, beforeEach, it } from 'node:test';
import { deleteApp, initializeApp, type App } from 'firebase-admin/app';
import { getFirestore, Timestamp, type Firestore } from 'firebase-admin/firestore';
import { applyRestore, exportHousehold, householdIds, planRestore } from '../../src/backup.ts';

const PROJECT = 'demo-backup-test';
const host = process.env.FIRESTORE_EMULATOR_HOST;
let app: App;
let db: Firestore;

before(() => {
  assert.ok(host, 'Run inside the Firestore emulator (FIRESTORE_EMULATOR_HOST)');
  app = initializeApp({ projectId: PROJECT }, 'backup-test');
  db = getFirestore(app);
});

after(async () => {
  await deleteApp(app);
});

const created = Timestamp.fromDate(new Date('2026-10-01T08:00:00.000Z'));

beforeEach(async () => {
  await fetch(`http://${host}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
  const b = db.batch();
  b.set(db.doc('households/h1'), { name: 'Home', ownerUid: 'u1', createdAt: created });
  b.set(db.doc('households/h1/members/u1'), { role: 'owner', displayName: 'Sam', joinedAt: created });
  b.set(db.doc('households/h1/products/p1'), { name: 'Milk', nameKey: 'milk', minQuantity: null, createdAt: created });
  b.set(db.doc('households/h1/stock/s1'), { productId: 'p1', quantity: 2, expiryDate: '2026-10-20', createdAt: created });
  b.set(db.doc('households/h1/groceryLists/g1'), { name: 'Groceries', isDefault: true });
  b.set(db.doc('households/h1/groceryLists/g1/items/i1'), { name: 'Eggs', checked: false, createdAt: created });
  b.set(db.doc('households/h1/recipes/r1'), { title: 'Pie', ingredients: [{ raw: '1 cup flour', qty: 1 }], createdAt: created });
  b.set(db.doc('households/h1/activity/a1'), { type: 'stock_added', createdAt: created });
  b.set(db.doc('households/h1/meals/m1'), { date: '2026-10-29', slot: 'dinner', title: 'Pie', cooks: ['u1'], createdAt: created });
  b.set(db.doc('households/h1/stock/s-gone'), { productId: 'p1', quantity: 1, expiryDate: '2026-10-21', deletedAt: created });
  b.set(db.doc('households/h2'), { name: 'Other', ownerUid: 'u2' });
  await b.commit();
});

it('exports every household in the app format, without the activity log or tombstones', async () => {
  assert.deepEqual((await householdIds(db)).sort(), ['h1', 'h2']);
  const data = await exportHousehold(db, 'h1', new Date('2026-10-18T03:17:00Z'), 'test');
  assert.equal(data.format, 'spoilnot-export');
  assert.equal(data.household?.name, 'Home');
  assert.equal(data.members[0]?.displayName, 'Sam');
  assert.equal(data.products[0]?.createdAt, '2026-10-01T08:00:00.000Z');
  assert.equal(data.stock[0]?.expiryDate, '2026-10-20');
  assert.equal(data.stock.length, 1, 'the deleted batch is left out');
  assert.equal(data.groceryLists[0]?.items[0]?.name, 'Eggs');
  assert.equal(data.recipes[0]?.title, 'Pie');
  assert.equal(data.meals?.[0]?.slot, 'dinner', 'planned meals (after v1)');
  assert.equal(JSON.stringify(data).includes('stock_added'), false);
});

it('a restore puts deleted and changed documents back as they were', async () => {
  const data = JSON.parse(JSON.stringify(await exportHousehold(db, 'h1', new Date(), 'test')));
  await db.doc('households/h1/products/p1').delete();
  await db.doc('households/h1/stock/s1').update({ quantity: 0 });
  await db.doc('households/h1/groceryLists/g1/items/i1').delete();
  await db.doc('households/h1/products/p2').set({ name: 'Bread' });
  await db.doc('households/h1/meals/m1').delete();

  const written = await applyRestore(db, planRestore(data, 'h1'));
  assert.equal(written, 6);
  assert.equal((await db.doc('households/h1/meals/m1').get()).get('title'), 'Pie', 'meals come back too');
  const milk = await db.doc('households/h1/products/p1').get();
  assert.equal(milk.get('name'), 'Milk');
  assert.ok(milk.get('createdAt') instanceof Timestamp);
  assert.equal(milk.get('createdAt').toMillis(), created.toMillis());
  assert.equal((await db.doc('households/h1/stock/s1').get()).get('quantity'), 2);
  assert.equal((await db.doc('households/h1/groceryLists/g1/items/i1').get()).get('name'), 'Eggs');
  // Added after the export: left alone.
  assert.equal((await db.doc('households/h1/products/p2').get()).get('name'), 'Bread');
});

it('refuses to restore into a household that does not exist', async () => {
  const data = await exportHousehold(db, 'h1', new Date(), 'test');
  await assert.rejects(applyRestore(db, planRestore(data, 'gone')), /does not exist/);
});
