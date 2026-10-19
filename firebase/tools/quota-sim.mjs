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
const RECIPE_VISITS_PER_DAY = 3;
const WRITES_PER_HOUSEHOLD = 40;
const REMINDER_RUNS_PER_DAY = 24;
// Of the day's writes, this many finish a batch (used up or thrown away) and also bump the month's Insights counts.
const FINISHES_PER_HOUSEHOLD = 10;
const MONTH = '2026-10';

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
    await put(`households/${hid}/stats/${MONTH}`, { used: 20, expired: 2, thrownByDay: { 3: 2 }, updatedAt: new Date(), updatedBy: owner });
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

/** One app start, with the listeners the app attaches (DESIGN 9.1). */
async function open(db, uid, hid, isOwner, withRecipes) {
  const h = `households/${hid}`;
  const targets = [
    doc(db, `users/${uid}`), // session and notification settings
    doc(db, `${h}/members/${uid}`), // membership watcher
    doc(db, 'config/app'), // update gate
    collection(db, `${h}/products`), // pantry, Home, restock engine (one listener each in the SDK)
    collection(db, `${h}/stock`),
    collection(db, `${h}/groceryLists`),
    collection(db, `${h}/groceryLists/default/items`), // Home's grocery card and the list
    query(collection(db, `${h}/activity`), orderBy('createdAt', 'desc'), limit(10)), // Home > Recent activity
    query(collection(db, `${h}/stats`), where(documentId(), '>=', MONTH)), // Home > Your kitchen this month
  ];
  if (isOwner) targets.push(collection(db, `${h}/joinRequests`)); // owner's badge
  if (withRecipes) targets.push(collection(db, `${h}/recipes`)); // first visit to Recipes
  await Promise.all(targets.map(once));
  // Rules: every household-scoped query checks membership with one exists() (users and config need none).
  reads.ruleLookups += targets.length - 3;
}

const started = Date.now();
for (let h = 0; h < HOUSEHOLDS; h++) {
  const hid = `h${h}`;
  for (let p = 0; p < PHONES; p++) {
    const uid = `${hid}-u${p}`;
    const db = env.authenticatedContext(uid).firestore();
    for (let o = 0; o < OPENS_PER_DAY; o++) await open(db, uid, hid, p === 0, o < RECIPE_VISITS_PER_DAY);
  }
  // A day's writes (DESIGN 9.7: about 40), made by phone 1 while phone 2 is open and listening.
  const writer = env.authenticatedContext(`${hid}-u1`).firestore();
  const watcher = env.authenticatedContext(`${hid}-u2`).firestore();
  let delivered = 0;
  const stop = onSnapshot(collection(watcher, `households/${hid}/stock`), (snap) => {
    delivered += snap.docChanges().length;
  });
  await new Promise((r) => setTimeout(r, 300));
  const initial = delivered;
  for (let w = 0; w < WRITES_PER_HOUSEHOLD; w++) {
    await updateDoc(doc(writer, `households/${hid}/stock/s${w % 60}`), {
      quantity: 2 + w, updatedAt: serverTimestamp(), updatedBy: `${hid}-u1`,
    });
  }
  await new Promise((r) => setTimeout(r, 500));
  stop();
  writes += WRITES_PER_HOUSEHOLD + FINISHES_PER_HOUSEHOLD;
  reads.listeners += delivered - initial; // the open phone receives each change
  reads.ruleLookups += WRITES_PER_HOUSEHOLD; // isMember on each write
}

// Scheduled jobs (backend/reminder): the users query every hour; housekeeping once a day (households, then two
// queries per household, each at least one read); once a day per user a stock query and a devices read; the
// nightly export reads every household document once.
const users = HOUSEHOLDS * PHONES;
const perHousehold = 1 + PHONES + 30 + 60 + 2 + 25 + 15 + 1; // + this month's stats
reads.jobs = REMINDER_RUNS_PER_DAY * users + (HOUSEHOLDS + 2 * HOUSEHOLDS) + users * 2 + HOUSEHOLDS * perHousehold;
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
