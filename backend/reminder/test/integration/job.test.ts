// Runs the whole job against the Firestore emulator with seeded data (DESIGN 10.5). FCM has no emulator, so a
// recording sender stands in for it. Uses its own project id so it never touches the app's emulator data.
import assert from 'node:assert/strict';
import { after, before, beforeEach, describe, it } from 'node:test';
import { deleteApp, initializeApp, type App } from 'firebase-admin/app';
import { getFirestore, Timestamp, type Firestore } from 'firebase-admin/firestore';
import {
  MAX_DEVICES, MAX_SEND_ATTEMPTS, MAX_STOCK, PLAN_ROUTE, runJob, ROUTE, STATE_DOC, WEB_ICON, type Message, type Sender,
} from '../../src/job.ts';

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
  for (const uid of ['alice', 'bob', 'carol', 'erin', 'frank']) {
    batch.set(db.doc(`households/h1/members/${uid}`), { role: uid === 'alice' ? 'owner' : 'member', displayName: uid });
  }
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

/** A profile that still points at a household that no longer lists them, with a registered phone. */
async function seedFormerMember(uid: string, householdId: string) {
  const batch = db.batch();
  batch.set(db.doc(`users/${uid}`), {
    displayName: uid, householdId, timezone: 'America/New_York', locale: 'en', notificationPrefs: prefs,
    lastDigestDate: null, lastExpiredThrough: null,
  });
  batch.set(db.doc(`users/${uid}/devices/phone`), { token: `${uid}-token-1234567890`, platform: 'android' });
  await batch.commit();
}

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
    assert.equal(summary.pruned, true);
    assert.equal(summary.lastPruneDate, '2026-10-12');
    assert.equal(summary.failures, 0);
    assert.equal((await db.doc(STATE_DOC).get()).get('lastPruneDate'), '2026-10-12');
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

  it('a removed member gets nothing, and their pointer is cleared as their app would', async () => {
    // The owner removed gus, who cooks tonight; his app has not run since. Hana's household was deleted.
    await db.doc('households/h1/meals/m-today').set({
      date: '2026-10-12', slot: 'dinner', position: 0, title: 'Tacos', cooks: ['gus', 'frank'], cookedAt: null,
    });
    await seedFormerMember('gus', 'h1');
    await seedFormerMember('hana', 'h-deleted');
    const sender = new RecordingSender();
    const summary = await runJob(db, sender, { now: NOW, prune: false });

    assert.equal(sender.sent.some((m) => m.token.startsWith('gus') || m.token.startsWith('hana')), false);
    assert.ok(sender.sent.some((m) => m.token.startsWith('frank')), 'the members still get theirs');
    assert.equal(summary.skippedNotMember, 2);
    const gus = (await db.doc('users/gus').get()).data()!;
    assert.equal(gus.householdId, null);
    assert.ok(gus.updatedAt instanceof Timestamp);
    assert.equal(gus.lastDigestDate, null);
    assert.equal((await db.doc('users/hana').get()).get('householdId'), null);

    // No longer due anywhere, so later runs read nothing for them.
    const later = await runJob(db, new RecordingSender(), { now: new Date('2026-10-12T14:07:00Z'), prune: false });
    assert.equal(later.skippedNotMember, 0);
  });

  it('a dry run skips a removed member and leaves the pointer', async () => {
    await seedFormerMember('gus', 'h1');
    const sender = new RecordingSender();
    const summary = await runJob(db, sender, { now: NOW, dryRun: true });
    assert.equal(summary.skippedNotMember, 1);
    assert.equal((await db.doc('users/gus').get()).get('householdId'), 'h1');
  });

  it('a removed member who joined a household while the run was going keeps the new pointer', async () => {
    await seedFormerMember('gus', 'h1');
    // Gus's app writes his new household between the job's reads and its clearing.
    const racing = new Proxy(db, {
      get(target, prop) {
        const value = Reflect.get(target, prop, target);
        if (prop !== 'getAll') return typeof value === 'function' ? value.bind(target) : value;
        return async (...refs: Parameters<Firestore['getAll']>) => {
          const snapshots = await target.getAll(...refs);
          await target.doc('users/gus').update({ householdId: 'h2' });
          return snapshots;
        };
      },
    });
    const summary = await runJob(racing, new RecordingSender(), { now: NOW, prune: false });
    assert.equal(summary.skippedNotMember, 1);
    assert.equal((await db.doc('users/gus').get()).get('householdId'), 'h2');
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

  it('three runs an hour: one digest per user a day, and one prune, in the day\'s first run', async () => {
    await db.doc(STATE_DOC).set({ lastPruneDate: '2026-10-11' });
    const sender = new RecordingSender();
    const pruned: string[] = [];
    // Every scheduled start from 00:07 to 23:47 UTC on 2026-10-12 (9:00 in New York is 13:00 UTC).
    for (let h = 0; h < 24; h++) {
      for (const m of [7, 27, 47]) {
        const now = new Date(Date.UTC(2026, 9, 12, h, m));
        const summary = await runJob(db, sender, { now });
        assert.equal(summary.failures, 0);
        if (summary.pruned) pruned.push(now.toISOString());
      }
    }
    assert.deepEqual(pruned, ['2026-10-12T00:07:00.000Z']);
    // Per device and local date: 00:07 UTC is still 11 October in New York, so alice also gets that day's digest.
    const perDay = new Map<string, number>();
    for (const m of sender.sent) perDay.set(`${m.token} ${m.data.date}`, (perDay.get(`${m.token} ${m.data.date}`) ?? 0) + 1);
    assert.deepEqual(Object.fromEntries(perDay), {
      'alice-token-1234567890 2026-10-11': 1,
      'alice-web-token-1234567890 2026-10-11': 1,
      'dead-token 2026-10-11': 1,
      'erin-token-1234567890 2026-10-12': 1,
      'alice-token-1234567890 2026-10-12': 1,
      'alice-web-token-1234567890 2026-10-12': 1,
    });
  });

  it('a send that throws costs only that user this run; the other households still get theirs', async () => {
    // Appendix E of the reliability review: three households due, the first send throws.
    const batch = db.batch();
    for (const [uid, hid] of [['a-first', 'hh-a'], ['b-second', 'hh-b'], ['c-third', 'hh-c']] as const) {
      batch.set(db.doc(`users/${uid}`), { householdId: hid, timezone: 'America/New_York', notificationPrefs: prefs });
      batch.set(db.doc(`users/${uid}/devices/d1`), { token: `tok-${uid}-1234567890`, platform: 'android' });
      batch.set(db.doc(`households/${hid}/members/${uid}`), { role: 'owner', displayName: uid });
      batch.set(db.doc(`households/${hid}/stock/s1`), { productName: 'Milk', expiryDate: '2026-10-13', dateType: 'use_by', quantity: 1 });
    }
    await batch.commit();
    let calls = 0;
    const sent: Message[] = [];
    const lines: string[] = [];
    const throwing: Sender = {
      async sendEach(messages) {
        if (++calls === 1) throw Object.assign(new Error('fcm timeout for tok-a-first'), { code: 'app/network-timeout' });
        sent.push(...messages);
        return messages.map(() => ({ success: true, deadToken: false }));
      },
    };
    const summary = await runJob(db, throwing, { now: NOW, prune: false, log: (l) => lines.push(l) });

    assert.equal(summary.failures, 1);
    assert.ok(sent.some((m) => m.token.startsWith('tok-b-second')) && sent.some((m) => m.token.startsWith('tok-c-third')));
    assert.equal((await db.doc('users/a-first').get()).get('lastDigestDate'), undefined, 'retried next run');
    assert.deepEqual((await db.doc('users/a-first').get()).get('digestAttempts'), { date: '2026-10-12', count: 1 });
    assert.equal((await db.doc('users/b-second').get()).get('lastDigestDate'), '2026-10-12');
    // The failure line has the code only: no ID, no message.
    assert.deepEqual(lines.filter((l) => l.includes('failed')), ['a digest failed: app/network-timeout']);
    assert.ok(lines.every((l) => !l.includes('a-first') && !l.includes('fcm timeout')));
  });

  it(`after ${MAX_SEND_ATTEMPTS} runs without a delivery the user is done for the day`, async () => {
    let calls = 0;
    const refusing: Sender = {
      async sendEach(messages) {
        calls++;
        return messages.map(() => ({ success: false, deadToken: false }));
      },
    };
    const runs = Array.from({ length: MAX_SEND_ATTEMPTS + 1 }, (_, i) => new Date(NOW.getTime() + i * 20 * 60_000));
    const gaveUp = [];
    for (const now of runs) gaveUp.push((await runJob(db, refusing, { now, prune: false })).gaveUp);
    // Alice's digest reaches none of her devices; the last run no longer reads or sends for her.
    assert.deepEqual(gaveUp, [0, 0, 1, 0]);
    assert.equal(calls, MAX_SEND_ATTEMPTS);
    const alice = (await db.doc('users/alice').get()).data()!;
    assert.equal(alice.lastDigestDate, '2026-10-12');
    assert.equal(alice.lastExpiredThrough, null, 'nothing was reported');
    assert.equal(alice.digestAttempts, undefined);
  });

  it('a device a send refuses for another reason keeps its token', async () => {
    const sender: Sender = { sendEach: async (messages) => messages.map(() => ({ success: false, deadToken: false })) };
    const summary = await runJob(db, sender, { now: NOW, prune: false });
    assert.equal(summary.deadTokens, 0);
    assert.equal((await db.doc('users/alice/devices/old').get()).exists, true);
  });

  it(`a digest goes to at most the ${MAX_DEVICES} newest devices`, async () => {
    const batch = db.batch();
    for (let i = 0; i < MAX_DEVICES + 5; i++) {
      batch.set(db.doc(`users/frank/devices/d${i}`), {
        token: `frank-many-${String(i).padStart(2, '0')}-1234567890`, platform: 'android',
        updatedAt: Timestamp.fromMillis(NOW.getTime() - (100 - i) * 60_000),
      });
    }
    batch.set(db.doc('households/h1/meals/m-today'), {
      date: '2026-10-12', slot: 'dinner', position: 0, title: 'Tacos', cooks: ['frank'], cookedAt: null,
    });
    await batch.commit();
    const sender = new RecordingSender();
    await runJob(db, sender, { now: NOW, prune: false });
    const frank = sender.sent.filter((m) => m.token.startsWith('frank')).map((m) => m.token).sort();
    assert.equal(frank.length, MAX_DEVICES);
    assert.equal(frank[0], 'frank-many-05-1234567890', 'the oldest five are left out');
  });

  it('users without a device are skipped before the household\'s stock is read', async () => {
    const queried: string[] = [];
    const watching = new Proxy(db, {
      get(target, prop) {
        const value = Reflect.get(target, prop, target);
        if (prop !== 'collection') return typeof value === 'function' ? value.bind(target) : value;
        return (path: string) => { queried.push(path); return target.collection(path); };
      },
    });
    // Only bob (no devices) is due.
    for (const uid of ['alice', 'frank']) await db.doc(`users/${uid}`).update({ lastDigestDate: '2026-10-12' });
    const summary = await runJob(watching, new RecordingSender(), { now: NOW, prune: false });
    assert.equal(summary.skippedNoDevices, 1);
    assert.deepEqual(queried.filter((p) => p.startsWith('households/')), []);
  });

  it(`one household's stock query reads at most ${MAX_STOCK} batches, soonest first`, async () => {
    for (let start = 0; start < MAX_STOCK + 20; start += 400) {
      const batch = db.batch();
      for (let i = start; i < Math.min(start + 400, MAX_STOCK + 20); i++) {
        batch.set(db.doc(`households/h1/stock/many-${i}`), {
          productName: `P${i}`, expiryDate: i < 10 ? '2026-10-12' : '2026-10-14', dateType: 'use_by', quantity: 1,
        });
      }
      await batch.commit();
    }
    const sender = new RecordingSender();
    await runJob(db, sender, { now: NOW, prune: false });
    const alice = sender.sent.find((m) => m.token.startsWith('alice'))!;
    // The cap keeps the soonest: Milk (expired), today's ten and the rest of the 500.
    assert.equal(alice.notification.title, `${MAX_STOCK - 1} items need attention`);
    assert.match(alice.notification.body, /^Throw away: Milk\. Today: P\d, P\d, P\d \+7 more\./);
  });

  it('a profile deleted during the run is skipped as a deleted account, not a failure', async () => {
    const deleting = new Proxy(db, {
      get(target, prop) {
        const value = Reflect.get(target, prop, target);
        if (prop !== 'getAll') return typeof value === 'function' ? value.bind(target) : value;
        return async (...refs: Parameters<Firestore['getAll']>) => {
          const snapshots = await target.getAll(...refs);
          await target.doc('users/frank').delete();
          return snapshots;
        };
      },
    });
    const sender = new RecordingSender();
    const summary = await runJob(deleting, sender, { now: NOW, prune: false });
    assert.equal(summary.skippedDeleted, 1);
    assert.equal(summary.failures, 0);
    assert.ok(sender.sent.some((m) => m.token.startsWith('alice')));
  });

  it('prunes once per UTC day, keyed on the job\'s own document', async () => {
    const first = await runJob(db, new RecordingSender(), { now: new Date('2026-10-12T00:07:00Z') });
    assert.equal(first.pruned, true);
    assert.equal(first.lastPruneDate, '2026-10-12');
    // Something old enough appears later that day: not pruned until the next UTC day.
    await db.doc('households/h1/activity/later').set({ type: 'stock_added', createdAt: Timestamp.fromDate(new Date('2026-09-01T00:00:00Z')) });
    const second = await runJob(db, new RecordingSender(), { now: new Date('2026-10-12T23:47:00Z') });
    assert.equal(second.pruned, false);
    assert.equal(second.lastPruneDate, '2026-10-12');
    assert.equal((await db.doc('households/h1/activity/later').get()).exists, true);
    const next = await runJob(db, new RecordingSender(), { now: new Date('2026-10-13T00:27:00Z') });
    assert.equal(next.pruned, true);
    assert.equal(next.lastPruneDate, '2026-10-13');
    assert.equal((await db.doc('households/h1/activity/later').get()).exists, false);
    // A dry run reports the date and changes nothing.
    const dry = await runJob(db, new RecordingSender(), { now: new Date('2026-10-14T00:07:00Z'), dryRun: true });
    assert.equal(dry.pruned, false);
    assert.equal(dry.lastPruneDate, '2026-10-13');
  });
});
