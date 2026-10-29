// Quota simulation (DESIGN 9.7, M7): 10 households against the Firestore emulator, to check the daily read and
// write math against the free Spark plan (50,000 reads, 20,000 writes per day).
//
//   cd firebase && npm run quota-sim
//
// Each household is DESIGN 9.7's typical one: 30 products, 60 batches, 2 grocery lists (25 items), 15 recipes,
// 4 phones. Every phone "opens the app" 8 times a day, attaching the listeners the app attaches at start, and once
// in 3 opens also the Recipes tab. The simulation has no offline cache, so each open reads every document, which is
// what Firestore bills when a listener reconnects after more than 30 minutes (the usual gap between opens).
// Delivered documents are counted from real snapshots; security-rule lookups and the scheduled jobs' reads are
// counted from the rules and the job code (the emulator does not report billing).
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection, doc, documentId, limit, onSnapshot, orderBy, query, serverTimestamp, setDoc, updateDoc, where, writeBatch,
} from 'firebase/firestore';

const QUOTA = { reads: 50_000, writes: 20_000 };
const HOUSEHOLDS = Number(process.env.HOUSEHOLDS ?? 10);
const PHONES = 4;
const OPENS_PER_DAY = 8;
const WRITES_PER_HOUSEHOLD = 40;
const REMINDER_RUNS_PER_DAY = 24;
// Of the day's writes, this many finish a batch (used up or thrown away) and also bump the month's Insights counts.
const FINISHES_PER_HOUSEHOLD = 10;
const MONTH = '2026-10';
// Deleted documents wait as tombstones for 2 days (DESIGN 9.8): about this many per day, and twice that in place.
const TOMBSTONES_PER_DAY = 15;
const TOMBSTONES = 2 * TOMBSTONES_PER_DAY;
// Meal planner (after v1, DESIGN 5.9): this week's planned meals, and others in the four weeks kept either side.
const WEEK_MEALS = 12;
const OTHER_MEALS = 30;
const WEEK_START = '2026-10-26';
const WEEK_END = '2026-11-01';

const env = await initializeTestEnvironment({
  projectId: 'demo-quota-sim',
  firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
});
await env.clearFirestore();

// --- Seed (rules off) ---
const stamp = (uid) => ({ createdAt: new Date(), createdBy: uid, updatedAt: new Date(), updatedBy: uid });
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'config/app'), { minSupportedBuild: 1, message: '' });
  for (let h = 0; h < HOUSEHOLDS; h++) {
    const hid = `h${h}`;
    const owner = `${hid}-u0`;
    let batch = writeBatch(db);
    let n = 0;
    const put = async (path, data) => {
      batch.set(doc(db, path), data);
      if (++n % 400 === 0) {
        await batch.commit();
        batch = writeBatch(db);
      }
    };
    await put(`households/${hid}`, { name: `Home ${h}`, ownerUid: owner, inviteCode: null, schemaVersion: 1 });
    for (let p = 0; p < PHONES; p++) {
      const uid = `${hid}-u${p}`;
      await put(`users/${uid}`, {
        displayName: `User ${p}`, householdId: hid, pendingJoin: null, timezone: 'America/New_York', locale: 'en',
        units: 'imperial', notificationPrefs: { enabled: true, digestMinutes: 540, stages: [3, 2, 1, 0], expiredMode: 'once' },
      });
      await put(`users/${uid}/devices/d1`, { token: `token-${uid}-123456789012345`, platform: 'android' });
      await put(`households/${hid}/members/${uid}`, { role: p === 0 ? 'owner' : 'member', displayName: `User ${p}`, joinedAt: new Date() });
    }
    for (let i = 0; i < 30; i++) {
      await put(`households/${hid}/products/p${i}`, { name: `Product ${i}`, nameKey: `product ${i}`, category: 'other', unit: 'pcs', minQuantity: i < 5 ? 2 : null, ...stamp(owner) });
    }
    for (let i = 0; i < 60; i++) {
      await put(`households/${hid}/stock/s${i}`, { productId: `p${i % 30}`, productName: `Product ${i % 30}`, unit: 'pcs', category: 'other', quantity: 1, expiryDate: '2026-10-25', dateType: 'use_by', location: 'fridge', note: null, ...stamp(owner) });
    }
    await put(`households/${hid}/groceryLists/default`, { name: 'Groceries', isDefault: true });
    await put(`households/${hid}/groceryLists/party`, { name: 'Party', isDefault: false });
    for (let i = 0; i < 20; i++) await put(`households/${hid}/groceryLists/default/items/i${i}`, { name: `Item ${i}`, checked: false, ...stamp(owner) });
    for (let i = 0; i < 5; i++) await put(`households/${hid}/groceryLists/party/items/i${i}`, { name: `Party ${i}`, checked: false, ...stamp(owner) });
    for (let i = 0; i < 15; i++) await put(`households/${hid}/recipes/r${i}`, { title: `Recipe ${i}`, ingredients: [], steps: [], ...stamp(owner) });
    for (let i = 0; i < WEEK_MEALS + OTHER_MEALS; i++) {
      const day = i < WEEK_MEALS ? 26 + (i % 7) : 1 + (i % 25);
      const month = i < WEEK_MEALS ? (day > 31 ? '11' : '10') : '09';
      const date = `2026-${month}-${String(day > 31 ? day - 31 : day).padStart(2, '0')}`;
      await put(`households/${hid}/meals/m${i}`, { date, slot: 'dinner', position: 0, recipeId: null, title: `Meal ${i}`, servings: null, cooks: [], note: null, ...stamp(owner) });
    }
    await put(`households/${hid}/stats/${MONTH}`, { used: 20, expired: 2, thrownByDay: { 3: 2 }, updatedAt: new Date(), updatedBy: owner });
    // Tombstones in place (read by every full read and the export).
    for (let i = 0; i < TOMBSTONES; i++) await put(`households/${hid}/stock/t${i}`, { productId: 'p0', productName: 'Gone', unit: 'pcs', category: 'other', quantity: 0, expiryDate: null, dateType: 'use_by', location: 'fridge', note: null, ...stamp(owner), deletedAt: new Date() });
    for (let i = 0; i < 40; i++) await put(`households/${hid}/activity/a${i}`, { type: 'stock_added', actorUid: owner, actorName: 'User 0', params: {}, createdAt: new Date(Date.now() - i * 3_600_000) });
    await batch.commit();
  }
});

// --- Counting ---
const reads = { listeners: 0, ruleLookups: 0, jobs: 0 };
let writes = 0;

/** Attaches a listener, counts the first snapshot (an empty result still costs one read), then detaches. */
function once(ref) {
  return new Promise((resolve, reject) => {
    const unsubscribe = onSnapshot(ref, (snap) => {
      unsubscribe();
      reads.listeners += 'size' in snap ? Math.max(1, snap.size) : 1;
      resolve();
    }, reject);
  });
}

/**
 * One app start, with the listeners the app attaches (DESIGN 9.1). After v1 (DESIGN 9.8) products, stock, grocery
 * items and recipes are read in full only on a phone's first open of the day; later opens read only what changed
 * since the previous open (the app starts 5 minutes before its newest cached change; in reality a few documents
 * more). Each query costs at least one read.
 */
async function open(db, uid, hid, isOwner, since) {
  const h = `households/${hid}`;
  const synced = (path) => (since ? query(collection(db, path), where('updatedAt', '>', since)) : collection(db, path));
  const targets = [
    doc(db, `users/${uid}`), // session and notification settings
    doc(db, `${h}/members/${uid}`), // membership watcher
    doc(db, 'config/app'), // update gate
    synced(`${h}/products`), // pantry, Home, restock engine (one listener each in the SDK)
    synced(`${h}/stock`),
    collection(db, `${h}/groceryLists`),
    synced(`${h}/groceryLists/default/items`), // Home's grocery card and the list
    // Home > Recent activity: the newest 10 on the first open of the day, then only new events (after v1).
    since
      ? query(collection(db, `${h}/activity`), where('createdAt', '>', since))
      : query(collection(db, `${h}/activity`), orderBy('createdAt', 'desc'), limit(10)),
    query(collection(db, `${h}/stats`), where(documentId(), '>=', MONTH)), // Home > Your kitchen this month
  ];
  if (isOwner) targets.push(collection(db, `${h}/joinRequests`)); // owner's badge
  targets.push(synced(`${h}/recipes`)); // Home > Use it up reads the recipes on every open (after v1)
  // Plan tab and Home's Today card: this week's meals, changes-only (DESIGN 9.8); the full read is the week only.
  targets.push(since
    ? query(collection(db, `${h}/meals`), where('updatedAt', '>', since))
    : query(collection(db, `${h}/meals`), where('date', '>=', WEEK_START), where('date', '<=', WEEK_END)));
  await Promise.all(targets.map(once));
  // Rules: every household-scoped query checks membership with one exists() (users and config need none).
  reads.ruleLookups += targets.length - 3;
}

const started = Date.now();
const WRITES_PER_ROUND = Math.ceil(WRITES_PER_HOUSEHOLD / OPENS_PER_DAY);
for (let h = 0; h < HOUSEHOLDS; h++) {
  const hid = `h${h}`;
  const writer = env.authenticatedContext(`${hid}-u1`).firestore();
  const phones = Array.from({ length: PHONES }, (_, p) => env.authenticatedContext(`${hid}-u${p}`).firestore());
  const lastOpen = new Array(PHONES).fill(null);
  let w = 0;
  for (let o = 0; o < OPENS_PER_DAY; o++) {
    // The day's writes (DESIGN 9.7: about 40) happen between opens, made by phone 1.
    if (o > 0) {
      for (let i = 0; i < WRITES_PER_ROUND && w < WRITES_PER_HOUSEHOLD; i++, w++) {
        await updateDoc(doc(writer, `households/${hid}/stock/s${w % 60}`), {
          quantity: 2 + w, updatedAt: serverTimestamp(), updatedBy: `${hid}-u1`,
        });
        // Every write is counted as logging an event too (in the app only some do), so Recent activity has news.
        await setDoc(doc(writer, `households/${hid}/activity/e${w}`), {
          type: 'stock_added', actorUid: `${hid}-u1`, actorName: 'User 1', params: {}, createdAt: serverTimestamp(),
        });
      }
    }
    for (let p = 0; p < PHONES; p++) {
      const since = lastOpen[p];
      lastOpen[p] = new Date();
      await open(phones[p], `${hid}-u${p}`, hid, p === 0, since);
    }
  }
  writes += WRITES_PER_HOUSEHOLD + FINISHES_PER_HOUSEHOLD;
  reads.ruleLookups += WRITES_PER_HOUSEHOLD; // isMember on each write
}

// Scheduled jobs (backend/reminder): the users query every hour; housekeeping once a day (households, then per
// household the activity and join request queries, the lists, and a tombstone query for products, stock, recipes
// and each list's items, each at least one read, plus the tombstones it deletes); once a day per user a stock query
// and a devices read; the nightly export reads every household document once, tombstones included. After v1 the
// digest also reads today's meals (about two) once per user and day, the worst case of members due in different runs.
const users = HOUSEHOLDS * PHONES;
const TODAY_MEALS = 2;
// Housekeeping per household: activity, join requests, lists, tombstones (products, stock, recipes, meals, each
// list's items) and past meals (after v1), each at least one read.
const housekeeping = HOUSEHOLDS + HOUSEHOLDS * (2 + 1 + 4 + 2 + 1) + HOUSEHOLDS * TOMBSTONES_PER_DAY;
const perHousehold = 1 + PHONES + 30 + 60 + 2 + 25 + 15 + 1 + TOMBSTONES + WEEK_MEALS + OTHER_MEALS; // + stats, meals
reads.jobs = REMINDER_RUNS_PER_DAY * users + housekeeping + users * (2 + TODAY_MEALS) + HOUSEHOLDS * perHousehold;
writes += users; // lastDigestDate once a day

const total = reads.listeners + reads.ruleLookups + reads.jobs;
const pct = (n, of) => `${((100 * n) / of).toFixed(1)}%`;
console.log(`\nQuota simulation: ${HOUSEHOLDS} households x ${PHONES} phones, ${OPENS_PER_DAY} opens a day`);
console.log(`  app listeners       ${reads.listeners.toLocaleString('en-US')} reads`);
console.log(`  rule lookups        ${reads.ruleLookups.toLocaleString('en-US')} reads`);
console.log(`  scheduled jobs      ${reads.jobs.toLocaleString('en-US')} reads`);
console.log(`  total               ${total.toLocaleString('en-US')} reads = ${pct(total, QUOTA.reads)} of ${QUOTA.reads.toLocaleString('en-US')}`);
console.log(`  per household       ${Math.round(total / HOUSEHOLDS).toLocaleString('en-US')} reads`);
console.log(`  writes              ${writes.toLocaleString('en-US')} = ${pct(writes, QUOTA.writes)} of ${QUOTA.writes.toLocaleString('en-US')}`);
console.log(`  (simulated in ${((Date.now() - started) / 1000).toFixed(0)} s)\n`);

await env.cleanup();
process.exit(0);
