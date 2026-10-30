// Runs the whole job against the Firestore emulator with seeded data (DESIGN 10.5). FCM has no emulator, so a
// recording sender stands in for it. Uses its own project id so it never touches the app's emulator data.
import assert from 'node:assert/strict';
import { after, before, beforeEach, describe, it } from 'node:test';
import { deleteApp, initializeApp, type App } from 'firebase-admin/app';
import { getFirestore, Timestamp, type Firestore } from 'firebase-admin/firestore';
import { PLAN_ROUTE, runJob, ROUTE, WEB_ICON, type Message, type Sender } from '../../src/job.ts';

const PROJECT = 'demo-reminder-test';
const host = process.env.FIRESTORE_EMULATOR_HOST;

class RecordingSender implements Sender {
  sent: Message[] = [];
  async sendEach(messages: Message[]) {
    this.sent.push(...messages);
    return messages.map((m) => ({ success: m.token !== 'dead-token', deadToken: m.token === 'dead-token' }));
  }
}

let app: App;
let db: Firestore;

before(() => {
  assert.ok(host, 'Run inside the Firestore emulator (FIRESTORE_EMULATOR_HOST)');
  app = initializeApp({ projectId: PROJECT }, 'reminder-test');
  db = getFirestore(app);
});

after(async () => {
  await deleteApp(app);
});

const prefs = { enabled: true, digestMinutes: 540, stages: [3, 2, 1, 0], expiredMode: 'once' };
// 2026-10-12 13:07 UTC is 9:07 in New York (EDT) and 22:07 in Tokyo (too late there).
const NOW = new Date('2026-10-12T13:07:00Z');

beforeEach(async () => {
  await fetch(`http://${host}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
  const batch = db.batch();
  const user = (uid: string, fields: object) => batch.set(db.doc(`users/${uid}`), {
    displayName: uid, householdId: 'h1', timezone: 'America/New_York', locale: 'en', notificationPrefs: prefs,
    lastDigestDate: null, lastExpiredThrough: null, ...fields,
  });
  user('alice', {});
  user('bob', {}); // no devices: skipped and retried next run
  user('carol', { notificationPrefs: { ...prefs, enabled: false } });
  user('erin', { timezone: 'Asia/Tokyo' }); // past 22:00 there
  user('frank', { notificationPrefs: { ...prefs, stages: [] , expiredMode: 'never' } });
  batch.set(db.doc('users/alice/devices/phone'), { token: 'alice-token-1234567890', platform: 'android' });
  batch.set(db.doc('users/alice/devices/old'), { token: 'dead-token', platform: 'android' });
  batch.set(db.doc('users/alice/devices/iphone'), { token: 'alice-web-token-1234567890', platform: 'web' });
  batch.set(db.doc('users/frank/devices/phone'), { token: 'frank-token-1234567890', platform: 'android' });
  batch.set(db.doc('users/erin/devices/phone'), { token: 'erin-token-1234567890', platform: 'android' });
  batch.set(db.doc('households/h1'), { name: 'Test', ownerUid: 'alice' });
  const stock = (id: string, productName: string, expiryDate: string | null, dateType = 'use_by') =>
    batch.set(db.doc(`households/h1/stock/${id}`), { productName, expiryDate, dateType, quantity: 1, location: 'fridge' });
  stock('s1', 'Milk', '2026-10-11');
  stock('s2', 'Yogurt', '2026-10-13');
  stock('s3', 'Eggs', '2026-10-13');
  stock('s4', 'Rice', '2027-06-01');
  stock('s5', 'Salt', null);
  // Deleted batches are tombstones (DESIGN 9.8): never in a digest; removed after 7 days.
  const deletedLongAgo = Timestamp.fromDate(new Date('2026-10-01T00:00:00Z'));
  const deletedToday = Timestamp.fromDate(new Date('2026-10-12T08:00:00Z'));
  batch.set(db.doc('households/h1/stock/gone'), {
    productName: 'Ham', expiryDate: '2026-10-13', dateType: 'use_by', quantity: 1, location: 'fridge', deletedAt: deletedToday,
  });
  batch.set(db.doc('households/h1/stock/old-gone'), { productName: 'Old', expiryDate: '2026-09-20', quantity: 1, deletedAt: deletedLongAgo });
  batch.set(db.doc('households/h1/groceryLists/g1'), { name: 'Groceries', isDefault: true });
  batch.set(db.doc('households/h1/groceryLists/g1/items/i-old'), { name: 'Bread', deletedAt: deletedLongAgo });
  batch.set(db.doc('households/h1/groceryLists/g1/items/i-live'), { name: 'Milk' });
  batch.set(db.doc('households/h1/recipes/r-old'), { title: 'Soup', deletedAt: deletedLongAgo });
  // Planned meals (DESIGN 5.9): kept 28 days after their day; an old deleted one goes as a tombstone.
  batch.set(db.doc('households/h1/meals/m-past'), { date: '2026-09-10', slot: 'dinner', title: 'Tacos' });
  batch.set(db.doc('households/h1/meals/m-recent'), { date: '2026-10-01', slot: 'dinner', title: 'Soup' });
  batch.set(db.doc('households/h1/meals/m-gone'), { date: '2026-10-14', slot: 'lunch', title: 'Salad', deletedAt: deletedLongAgo });
  const old = Timestamp.fromDate(new Date('2026-09-01T00:00:00Z'));
  const recent = Timestamp.fromDate(new Date('2026-10-10T00:00:00Z'));
  batch.set(db.doc('households/h1/activity/old'), { type: 'stock_added', createdAt: old });
  batch.set(db.doc('households/h1/activity/new'), { type: 'stock_added', createdAt: recent });
  batch.set(db.doc('households/h1/joinRequests/stale'), { status: 'pending', requestedAt: old });
  batch.set(db.doc('households/h1/joinRequests/fresh'), { status: 'pending', requestedAt: recent });
  await batch.commit();
});

describe('reminder job against the emulator', () => {
  it('sends the right digest to every device, deletes dead tokens and records the day', async () => {
    const sender = new RecordingSender();
    const summary = await runJob(db, sender, { now: NOW });

    const alice = sender.sent.filter((m) => m.token.startsWith('alice') || m.token === 'dead-token');
    assert.equal(alice.length, 3);
    assert.deepEqual(alice[0]!.notification, { title: '3 items need attention', body: 'Throw away: Milk. Tomorrow: Yogurt, Eggs.' });
    assert.deepEqual(alice[0]!.data, { type: 'expiry_digest', route: ROUTE, date: '2026-10-12' });
    assert.deepEqual(alice[0]!.android, { priority: 'high', notification: { channelId: 'expiry', tag: 'expiry_digest' } });
    // The web app's iPhone gets the same digest through its service worker (DESIGN 18.4).
    const web = alice.find((m) => m.token.startsWith('alice-web'))!;
    assert.deepEqual(web.webpush, {
      headers: { Urgency: 'high' },
      notification: { title: '3 items need attention', body: 'Throw away: Milk. Tomorrow: Yogurt, Eggs.', tag: 'expiry_digest', icon: WEB_ICON },
    });
    assert.equal(web.data.route, ROUTE);

    assert.equal((await db.doc('users/alice/devices/old').get()).exists, false, 'dead token deleted');
    assert.equal((await db.doc('users/alice/devices/phone').get()).exists, true);
    const aliceDoc = (await db.doc('users/alice').get()).data()!;
    assert.equal(aliceDoc.lastDigestDate, '2026-10-12');
    assert.equal(aliceDoc.lastExpiredThrough, '2026-10-11');

    assert.equal(sender.sent.some((m) => m.token.startsWith('erin')), false, 'too late in Tokyo');
    assert.equal(sender.sent.some((m) => m.token.startsWith('frank')), false, 'nothing in his stages');
    assert.equal((await db.doc('users/frank').get()).get('lastDigestDate'), '2026-10-12', 'an empty digest counts as done');
    assert.equal((await db.doc('users/bob').get()).get('lastDigestDate'), null, 'no devices: try again next run');
    assert.equal(summary.skippedNoDevices, 1);
    assert.equal(summary.deadTokens, 1);

    assert.equal(summary.prunedActivity, 1);
    assert.equal(summary.prunedJoinRequests, 1);
    assert.equal(summary.prunedTombstones, 4);
    assert.equal(summary.prunedMeals, 1);
    assert.equal((await db.doc('households/h1/meals/m-past').get()).exists, false, 'more than 28 days past');
    assert.equal((await db.doc('households/h1/meals/m-recent').get()).exists, true);
    assert.equal((await db.doc('households/h1/meals/m-gone').get()).exists, false, 'an old meal tombstone');
    assert.equal((await db.doc('households/h1/stock/gone').get()).exists, true, 'a recent tombstone stays');
    assert.equal((await db.doc('households/h1/groceryLists/g1/items/i-live').get()).exists, true);
    assert.equal((await db.doc('households/h1/stock/old-gone').get()).exists, false);
    assert.equal((await db.doc('households/h1/activity/new').get()).exists, true);
    assert.equal((await db.doc('households/h1/joinRequests/fresh').get()).exists, true);
  });

  it('a second run on the same day sends nothing', async () => {
    await runJob(db, new RecordingSender(), { now: NOW });
    const again = new RecordingSender();
    await runJob(db, again, { now: new Date('2026-10-12T15:07:00Z') });
    assert.deepEqual(again.sent.filter((m) => !m.token.startsWith('bob')), []);
  });

  it('the next day reports new items but not expired ones again', async () => {
    await runJob(db, new RecordingSender(), { now: NOW });
    const tomorrow = new RecordingSender();
    await runJob(db, tomorrow, { now: new Date('2026-10-13T13:07:00Z') });
    const alice = tomorrow.sent.find((m) => m.token.startsWith('alice'));
    assert.deepEqual(alice?.notification, { title: '2 items need attention', body: 'Today: Yogurt, Eggs.' });
  });

  it('a dry run sends and changes nothing', async () => {
    const sender = new RecordingSender();
    const lines: string[] = [];
    const summary = await runJob(db, sender, { now: NOW, dryRun: true, log: (l) => lines.push(l) });
    assert.equal(sender.sent.length, 0);
    assert.ok(summary.sent >= 2);
    assert.ok(lines.some((l) => l.includes('3 item(s), 0 meal(s)')));
    // Logs are public with the repository: no user IDs and no item names.
    assert.ok(lines.every((l) => !l.includes('alice') && !l.includes('Yogurt') && !l.includes('Milk')));
    assert.equal((await db.doc('users/alice').get()).get('lastDigestDate'), null);
    assert.equal((await db.doc('users/alice/devices/old').get()).exists, true);
    assert.equal((await db.doc('households/h1/activity/old').get()).exists, true);
  });

  it('cooks hear about today\'s meals; with only meals the digest opens the Plan tab', async () => {
    const batch = db.batch();
    batch.set(db.doc('households/h1/meals/m-today'), {
      date: '2026-10-12', slot: 'dinner', position: 0, title: 'Tacos', cooks: ['alice', 'frank'], cookedAt: null,
    });
    batch.set(db.doc('households/h1/meals/m-breakfast'), {
      date: '2026-10-12', slot: 'breakfast', position: 0, title: 'Pancakes', cooks: ['alice'], cookedAt: null,
    });
    // Not for anyone today: cooked already, removed, another day.
    batch.set(db.doc('households/h1/meals/m-done'), {
      date: '2026-10-12', slot: 'lunch', position: 0, title: 'Soup', cooks: ['alice'], cookedAt: Timestamp.fromDate(NOW),
    });
    batch.set(db.doc('households/h1/meals/m-removed'), {
      date: '2026-10-12', slot: 'lunch', position: 1, title: 'Salad', cooks: ['alice'], deletedAt: Timestamp.fromDate(NOW),
    });
    batch.set(db.doc('households/h1/meals/m-tomorrow'), {
      date: '2026-10-13', slot: 'dinner', position: 0, title: 'Curry', cooks: ['alice'],
    });
    await batch.commit();
    const sender = new RecordingSender();
    const summary = await runJob(db, sender, { now: NOW, prune: false });

    const alice = sender.sent.find((m) => m.token.startsWith('alice'))!;
    assert.equal(alice.notification.title, '3 items need attention');
    assert.equal(
      alice.notification.body,
      'Throw away: Milk. Tomorrow: Yogurt, Eggs. You\'re cooking: Pancakes (breakfast), Tacos (dinner).',
    );
    assert.equal(alice.data.route, ROUTE);
    // Frank has no food in his stages but cooks tonight: a digest about the meal that opens Plan.
    const frank = sender.sent.find((m) => m.token.startsWith('frank'))!;
    assert.deepEqual(frank.notification, { title: 'You\'re cooking Tacos for dinner', body: 'You\'re cooking: Tacos (dinner).' });
    assert.equal(frank.data.route, PLAN_ROUTE);
    assert.equal(summary.emptyDigests, 0);
  });

  it('the cooking line can be switched off', async () => {
    await db.doc('users/frank').update({ 'notificationPrefs.cooking': false });
    await db.doc('households/h1/meals/m-today').set({
      date: '2026-10-12', slot: 'dinner', position: 0, title: 'Tacos', cooks: ['frank'], cookedAt: null,
    });
    const sender = new RecordingSender();
    await runJob(db, sender, { now: NOW, prune: false });
    assert.equal(sender.sent.some((m) => m.token.startsWith('frank')), false);
  });
});
