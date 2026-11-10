// Quota simulation (DESIGN 9.7, M7): 10 households against the Firestore emulator, to check the daily read, write
// and delete math against the free Spark plan (50,000 reads, 20,000 writes, 20,000 deletes per day).
//
//   cd firebase && npm run quota-sim
//   HOUSEHOLDS=10 RUNS=24 WEB=0 WARM=0 HELD=0 npm run quota-sim
//
// Knobs: HOUSEHOLDS; RUNS, the reminder job's runs that start in a day (72 are scheduled, three an hour, but GitHub
// started about 5 of 24 a day in October 2026, DESIGN 10.4; 24 is the planning figure and RUNS=72 the worst case);
// WEB, how many of each household's 4 phones are the web app for iPhone (the same listeners since its update gate,
// DESIGN 18.5; only their device documents differ); WARM, of each phone's 7 later opens, how many find the app's
// process still alive (review 09, EFF-01). Since 2026-11-09 (B09) nothing holds a household listener once its screens
// are gone, so such an open resumes only the profile, membership and update-gate listeners, which every open counts
// anyway: WARM alone changes nothing. HELD=1 models the apps before that change: on Android the restock engine and
// Pantry's and Groceries' screens kept their listeners while the process lived; on the web (WEB phones; WARM is then
// how many later opens resume the suspended Home Screen app instead of reloading it) the restock engine kept its own
// from page load. Each was resumed on the return and, after more than 30 minutes away, billed like a new query.
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

const QUOTA = { reads: 50_000, writes: 20_000, deletes: 20_000 };
const HOUSEHOLDS = Number(process.env.HOUSEHOLDS ?? 10);
const PHONES = 4;
const OPENS_PER_DAY = 8;
const WRITES_PER_HOUSEHOLD = 40;
const REMINDER_RUNS_PER_DAY = Number(process.env.RUNS ?? 24);
const WEB = Math.min(PHONES, Number(process.env.WEB ?? 0));
const WARM = Math.min(OPENS_PER_DAY - 1, Number(process.env.WARM ?? 0));
const HELD = process.env.HELD === '1';
// Of the day's writes, this many finish a batch (used up or thrown away) and also bump the month's Insights counts.
const FINISHES_PER_HOUSEHOLD = 10;
// Of the day's writes, this many create a grocery item (quick add, restock, a recipe's ingredients). Since 2026-11-09
// the rules check that the item's list exists (DESIGN 8.5): one more lookup per batch that creates items.
const ITEM_CREATES_PER_HOUSEHOLD = 10;
const MONTH = '2026-10';
// Deleted documents wait as tombstones for 2 days (DESIGN 9.8): about this many per day, and twice that in place.
const TOMBSTONES_PER_DAY = 15;
const TOMBSTONES = 2 * TOMBSTONES_PER_DAY;
// Meal planner (after v1, DESIGN 5.9): this week's planned meals, and others in the four weeks kept either side.
const WEEK_MEALS = 12;
const OTHER_MEALS = 30;
const WEEK_START = '2026-10-26';
const WEEK_END = '2026-11-01';
// Each of the day's writes logs an activity event (in the app only some do); the prune deletes as many 30 days on.
const EVENTS_PER_DAY = WRITES_PER_HOUSEHOLD;
// Planned meals that pass the 28-day mark each day (about this week's meals spread over 7 days).
const MEALS_EXPIRING_PER_DAY = 2;

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
      const platform = p < PHONES - WEB ? 'android' : 'web';
      await put(`users/${uid}/devices/d1`, { token: `token-${uid}-123456789012345`, platform, updatedAt: new Date() });
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
const reads = { listeners: 0, held: 0, ruleLookups: 0, jobs: 0 };
let writes = 0;
let deletes = 0;

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
 * since the previous open (the app starts 5 minutes before the newest change its server listener received, after
 * v1 2026-11-09, so it rereads its own writes and in reality a few documents more). Each query costs at least one
 * read.
 */
async function open(db, uid, hid, isOwner, since) {
  const h = `households/${hid}`;
  const synced = (path) => (since ? query(collection(db, path), where('updatedAt', '>', since)) : collection(db, path));
  const targets = [
    doc(db, `users/${uid}`), // session and notification settings
    doc(db, `${h}/members/${uid}`), // membership watcher
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
  // The update gate: minSupportedBuild in the Android app, minWebBuild in the web app (DESIGN 13.3, 18.5).
  targets.push(doc(db, 'config/app'));
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

/**
 * HELD=1: a later open that finds the process alive, before B09. The listeners that never let go are resumed: the
 * restock engine's (usually the day's full read of products and stock), Pantry's (changes since its first visit,
 * for removing empty batches) and Groceries' (the list's items since its first visit and the members, for "Sam added
 * Eggs"). On a web phone, WARM counts the opens where iOS resumes the suspended Home Screen app instead of reloading
 * it: the web's restock engine kept its own listeners from page load (the day's full read of products and stock, and
 * the lists). The screen the web app showed when it left also kept its listeners, which this leaves out, so the web's
 * "before" is a lower bound.
 */
async function heldResume(db, hid, dayStart, web) {
  const h = `households/${hid}`;
  const sinceDay = (path) => query(collection(db, path), where('updatedAt', '>', dayStart));
  const targets = web
    ? [collection(db, `${h}/products`), collection(db, `${h}/stock`), collection(db, `${h}/groceryLists`)]
    : [
        collection(db, `${h}/products`),
        collection(db, `${h}/stock`),
        sinceDay(`${h}/products`),
        sinceDay(`${h}/stock`),
        sinceDay(`${h}/groceryLists/default/items`),
        collection(db, `${h}/members`),
      ];
  const before = reads.listeners;
  await Promise.all(targets.map(once));
  reads.held += reads.listeners - before;
  reads.listeners = before;
  reads.ruleLookups += targets.length;
}

const started = Date.now();
const WRITES_PER_ROUND = Math.ceil(WRITES_PER_HOUSEHOLD / OPENS_PER_DAY);
for (let h = 0; h < HOUSEHOLDS; h++) {
  const hid = `h${h}`;
  const writer = env.authenticatedContext(`${hid}-u1`).firestore();
  const phones = Array.from({ length: PHONES }, (_, p) => env.authenticatedContext(`${hid}-u${p}`).firestore());
  const lastOpen = new Array(PHONES).fill(null);
  const dayStart = new Array(PHONES).fill(null);
  let w = 0;
  for (let o = 0; o < OPENS_PER_DAY; o++) {
    // The day's writes (DESIGN 9.7: about 40) happen between opens, made by phone 1.
    if (o > 0) {
      for (let i = 0; i < WRITES_PER_ROUND && w < WRITES_PER_HOUSEHOLD; i++, w++) {
        if (w % (WRITES_PER_HOUSEHOLD / ITEM_CREATES_PER_HOUSEHOLD) === 0) {
          await setDoc(doc(writer, `households/${hid}/groceryLists/default/items/n${w}`), {
            name: `New ${w}`, checked: false, createdAt: serverTimestamp(), createdBy: `${hid}-u1`,
            updatedAt: serverTimestamp(), updatedBy: `${hid}-u1`,
          });
        } else {
          await updateDoc(doc(writer, `households/${hid}/stock/s${w % 60}`), {
            quantity: 2 + w, updatedAt: serverTimestamp(), updatedBy: `${hid}-u1`,
          });
        }
        // Every write is counted as logging an event too (in the app only some do), so Recent activity has news.
        await setDoc(doc(writer, `households/${hid}/activity/e${w}`), {
          type: 'stock_added', actorUid: `${hid}-u1`, actorName: 'User 1', params: {}, createdAt: serverTimestamp(),
        });
      }
    }
    for (let p = 0; p < PHONES; p++) {
      const since = lastOpen[p];
      lastOpen[p] = new Date();
      if (o === 0) dayStart[p] = new Date(Date.now() - 1000);
      await open(phones[p], `${hid}-u${p}`, hid, p === 0, since);
      const web = p >= PHONES - WEB;
      if (HELD && o > 0 && o <= WARM) await heldResume(phones[p], hid, dayStart[p], web);
    }
  }
  // The 40 actions, the 10 month counts and the 40 activity events; isMember on each action and each event, and the
  // list's existsAfter on each item create.
  writes += WRITES_PER_HOUSEHOLD + FINISHES_PER_HOUSEHOLD + EVENTS_PER_DAY;
  reads.ruleLookups += WRITES_PER_HOUSEHOLD + EVENTS_PER_DAY + ITEM_CREATES_PER_HOUSEHOLD;
}

// Scheduled jobs (backend/reminder, DESIGN 10.1). Every run: the users query (every user with reminders on) and the
// job's own state document. Once a day per user: their member document, their devices, and a share of the
// household's stock query; the digest also reads today's meals (about two), the worst case of members due in
// different runs. Once a day, in the first run of the UTC day, housekeeping: the households, then per household the
// activity, join request, past meal and list queries and a tombstone query for products, stock, recipes, meals and
// each list's items (each at least one read), plus every document it deletes (read before deleting): a day's
// tombstones, a day's activity events from 30 days ago and the meals passing 28 days. The nightly export reads every
// household document once, tombstones included.
const users = HOUSEHOLDS * PHONES;
const TODAY_MEALS = 2;
const pruned = TOMBSTONES_PER_DAY + EVENTS_PER_DAY + MEALS_EXPIRING_PER_DAY;
const housekeeping = HOUSEHOLDS + HOUSEHOLDS * (3 + 1 + 4 + 2) + HOUSEHOLDS * pruned;
const perHousehold = 1 + PHONES + 30 + 60 + 2 + 25 + 15 + 1 + TOMBSTONES + WEEK_MEALS + OTHER_MEALS; // + stats, meals
const jobs = {
  runs: REMINDER_RUNS_PER_DAY * (users + 1),
  digests: users * (3 + TODAY_MEALS),
  housekeeping,
  export: HOUSEHOLDS * perHousehold,
};
reads.jobs = Object.values(jobs).reduce((a, b) => a + b, 0);
writes += users + 1; // lastDigestDate once a day per user, lastPruneDate once a day
deletes += HOUSEHOLDS * pruned;

const total = reads.listeners + reads.held + reads.ruleLookups + reads.jobs;
const pct = (n, of) => `${((100 * n) / of).toFixed(1)}%`;
const n = (v) => Math.round(v).toLocaleString('en-US');
// Households of this size that fit under 70% of every daily limit.
const fit = Math.min(...['reads', 'writes', 'deletes'].map((k) =>
  (0.7 * QUOTA[k]) / ({ reads: total, writes, deletes }[k] / HOUSEHOLDS)));
console.log(`\nQuota simulation: ${HOUSEHOLDS} households x ${PHONES} phones (${WEB} web), ${OPENS_PER_DAY} opens a day, ${REMINDER_RUNS_PER_DAY} reminder runs a day`);
console.log(`  warm returns        ${WARM} of ${OPENS_PER_DAY - 1} later opens find the process alive${HELD ? ' (listeners held as before B09)' : ''}`);
console.log(`  app listeners       ${n(reads.listeners)} reads`);
if (HELD) console.log(`  held listeners      ${n(reads.held)} reads (resumed after 30 minutes away)`);
console.log(`  rule lookups        ${n(reads.ruleLookups)} reads`);
console.log(`  scheduled jobs      ${n(reads.jobs)} reads (runs ${n(jobs.runs)}, digests ${n(jobs.digests)}, housekeeping ${n(jobs.housekeeping)}, export ${n(jobs.export)})`);
console.log(`  total               ${n(total)} reads = ${pct(total, QUOTA.reads)} of ${n(QUOTA.reads)}`);
console.log(`  per household       ${n(total / HOUSEHOLDS)} reads`);
console.log(`  writes              ${n(writes)} = ${pct(writes, QUOTA.writes)} of ${n(QUOTA.writes)}`);
console.log(`  deletes             ${n(deletes)} = ${pct(deletes, QUOTA.deletes)} of ${n(QUOTA.deletes)}`);
console.log(`  households under 70% of every limit: ${fit.toFixed(1)}`);
console.log(`  (simulated in ${((Date.now() - started) / 1000).toFixed(0)} s)\n`);

await env.cleanup();
process.exit(0);
