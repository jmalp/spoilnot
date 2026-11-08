// The reminder job, three runs an hour (DESIGN 10.1, 10.3). Reads Firestore with the Admin SDK (which bypasses the rules),
// sends one digest per due user through FCM, and prunes old activity, join requests and tombstones.
import { FieldValue, GrpcStatus, type Firestore } from 'firebase-admin/firestore';
import { bulkWrites } from './bulk.ts';
import {
  afterSend, buildDigestWithMeals, cookingFor, dueAt, readPrefs, selectItems, stockWindow, type Batch, type Meal,
  type UserDoc,
} from './digest.ts';
import { errorCode } from './errors.ts';
import { isCalendarDate, type LocalTime } from './time.ts';

export interface Message {
  token: string;
  notification: { title: string; body: string };
  data: Record<string, string>;
  android: { priority: 'high'; notification: { channelId: string; tag: string } };
  /**
   * For the web app's tokens (DESIGN 18.4): the same title and body, shown by its service worker, which opens
   * `data.route` when tapped. Android ignores it.
   */
  webpush: { headers: { Urgency: 'high' }; notification: { title: string; body: string; tag: string; icon: string } };
}

/** Per-message outcome; `deadToken` means the token should be deleted. */
export interface SendResult {
  success: boolean;
  deadToken: boolean;
}

/** FCM in production, a recorder in tests, a logger in a dry run. */
export interface Sender {
  sendEach(messages: Message[]): Promise<SendResult[]>;
}

export interface JobOptions {
  now: Date;
  /** Log what would be sent and change nothing (DESIGN 10.5). */
  dryRun?: boolean;
  /**
   * Delete old activity, join requests and tombstones: 'daily' (the default) in the first run of each UTC day, keyed
   * on `jobs/state.lastPruneDate`; true or false to force it on or off (tests).
   */
  prune?: boolean | 'daily';
  log?: (line: string) => void;
}

export interface JobSummary {
  due: number;
  sent: number;
  emptyDigests: number;
  skippedNoDevices: number;
  /** Due users whose household no longer lists them (removed, or it was deleted): no digest, pointer cleared. */
  skippedNotMember: number;
  /** Due users whose profile was deleted while the run went on (an account deletion). */
  skippedDeleted: number;
  /** Users marked done for the day after MAX_SEND_ATTEMPTS runs without a delivered digest. */
  gaveUp: number;
  deadTokens: number;
  /** Households and users whose step failed this run; counted, never named, and the run carries on. */
  failures: number;
  /** Whether this run pruned, and the UTC date of the last prune without failures (null if none yet). */
  pruned: boolean;
  lastPruneDate: string | null;
  prunedActivity: number;
  prunedJoinRequests: number;
  prunedTombstones: number;
  prunedMeals: number;
  /** Prune deletions that failed after retries (also in the counts above); the next prune finds them again. */
  pruneFailures: number;
}

type Profile = FirebaseFirestore.QueryDocumentSnapshot;

export const ROUTE = '/pantry?filter=expiring';
/** A digest with only meals opens the Plan tab (after v1, DESIGN 5.9). */
export const PLAN_ROUTE = '/plan';
const CHANNEL = 'expiry';
/** The web app's icon, relative to its origin (web/public/icons). */
export const WEB_ICON = '/icons/icon-192.png';
const ACTIVITY_DAYS = 30;
const JOIN_REQUEST_DAYS = 7;
/** Deleted documents stay as tombstones this long (longer than the phones' daily full read, DESIGN 9.8). */
export const TOMBSTONE_DAYS = 2;
/** Household collections whose deletions are tombstones (plus every list's items). */
const TOMBSTONED = ['products', 'stock', 'recipes', 'meals'];
/** Planned meals are kept this long after their day (DESIGN 5.9). */
export const MEAL_DAYS = 28;
/** A user's digest goes to at most this many devices, the most recently registered (DESIGN 10.3). */
export const MAX_DEVICES = 10;
/** FCM's sendEach takes at most 500 messages a call. */
export const SEND_CHUNK = 500;
/** One household's stock query reads at most this many batches, soonest expiry first. */
export const MAX_STOCK = 500;
/** And its meals query at most this many planned meals. */
export const MAX_MEALS = 100;
/** After this many runs in one local day without a delivered digest, the user is done for the day. */
export const MAX_SEND_ATTEMPTS = 3;
/** The job's own document: the rules match no such path, so clients can neither read nor write it. */
export const STATE_DOC = 'jobs/state';
/**
 * FCM error codes that mean the token itself is gone, so its device document is deleted. Other errors, such as
 * `messaging/invalid-argument` (which also covers a message FCM refuses), keep the token.
 */
export const DEAD_TOKEN_CODES: ReadonlySet<string> = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

export function isDeadToken(code: string | undefined): boolean {
  return code !== undefined && DEAD_TOKEN_CODES.has(code);
}

/** The UTC calendar date of [now], 'YYYY-MM-DD'. */
export function utcDate(now: Date): string {
  return now.toISOString().slice(0, 10);
}

/** The prune runs in the first run of each UTC day: when the last prune without failures was before today. */
export function pruneDue(lastPruneDate: string | null, now: Date): boolean {
  return lastPruneDate === null || lastPruneDate < utcDate(now);
}

/** [list] in pieces of at most [size]. */
export function chunks<T>(list: readonly T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < list.length; i += size) out.push(list.slice(i, i + size));
  return out;
}

function toUser(id: string, data: FirebaseFirestore.DocumentData): UserDoc {
  return {
    uid: id,
    householdId: typeof data.householdId === 'string' ? data.householdId : null,
    timezone: typeof data.timezone === 'string' ? data.timezone : null,
    locale: typeof data.locale === 'string' ? data.locale : null,
    prefs: readPrefs(data.notificationPrefs),
    lastDigestDate: isCalendarDate(data.lastDigestDate) ? data.lastDigestDate : null,
    lastExpiredThrough: isCalendarDate(data.lastExpiredThrough) ? data.lastExpiredThrough : null,
    digestAttempts: readAttempts(data.digestAttempts),
  };
}

function readAttempts(raw: unknown): UserDoc['digestAttempts'] {
  const a = raw as { date?: unknown; count?: unknown } | null | undefined;
  return a && isCalendarDate(a.date) && typeof a.count === 'number' ? { date: a.date, count: a.count } : null;
}

function toMeal(data: FirebaseFirestore.DocumentData): Meal | null {
  // Tombstones are removed meals (DESIGN 9.8).
  if (data.deletedAt != null || !isCalendarDate(data.date)) return null;
  if (data.slot !== 'breakfast' && data.slot !== 'lunch' && data.slot !== 'dinner') return null;
  return {
    date: data.date,
    slot: data.slot,
    position: typeof data.position === 'number' ? data.position : 0,
    title: typeof data.title === 'string' ? data.title : 'A meal',
    cooks: Array.isArray(data.cooks) ? data.cooks.filter((c: unknown): c is string => typeof c === 'string') : [],
    cooked: data.cookedAt != null,
  };
}

function toBatch(data: FirebaseFirestore.DocumentData): Batch | null {
  // A tombstone is a deleted batch (DESIGN 9.8).
  if (data.deletedAt != null || !isCalendarDate(data.expiryDate)) return null;
  return {
    productName: typeof data.productName === 'string' ? data.productName : 'Item',
    expiryDate: data.expiryDate,
    dateType: data.dateType === 'best_before' ? 'best_before' : 'use_by',
    quantity: typeof data.quantity === 'number' ? data.quantity : 0,
  };
}

type Due = { user: UserDoc; local: LocalTime; profile: Profile };
type Token = { ref: FirebaseFirestore.DocumentReference; token: string };

export async function runJob(db: Firestore, sender: Sender, options: JobOptions): Promise<JobSummary> {
  const { now, dryRun = false, prune: pruneOption = 'daily' } = options;
  const log = options.log ?? (() => {});
  const summary: JobSummary = {
    due: 0, sent: 0, emptyDigests: 0, skippedNoDevices: 0, skippedNotMember: 0, skippedDeleted: 0, gaveUp: 0,
    deadTokens: 0, failures: 0, pruned: false, lastPruneDate: null, prunedActivity: 0, prunedJoinRequests: 0,
    prunedTombstones: 0, prunedMeals: 0, pruneFailures: 0,
  };
  // Workflow logs are public: a failure is one line with its code, never an ID, a path or the error's message.
  const failed = (step: string, error: unknown) => {
    summary.failures++;
    log(`${step} failed: ${errorCode(error)}`);
  };

  // 1-2. Users with reminders on who are due now. Without this read nothing else can run, so a failure here ends
  // the run (runMain prints its code).
  const users = await db.collection('users').where('notificationPrefs.enabled', '==', true).get();
  const due: Due[] = [];
  for (const doc of users.docs) {
    const user = toUser(doc.id, doc.data());
    const local = dueAt(user, now);
    if (local) due.push({ user, local, profile: doc });
  }
  summary.due = due.length;

  const byHousehold = new Map<string, Due[]>();
  for (const entry of due) {
    const list = byHousehold.get(entry.user.householdId!) ?? [];
    list.push(entry);
    byHousehold.set(entry.user.householdId!, list);
  }

  // 3-6. Each household on its own: one that fails is counted and the next one still gets its digests.
  for (const [householdId, entries] of byHousehold) {
    try {
      await household(db, sender, householdId, entries, { dryRun, log, summary, failed });
    } catch (error) {
      failed('a household', error);
    }
  }

  // 7. Housekeeping (DESIGN 5.1, 5.6), in the first run of each UTC day.
  try {
    const state = await db.doc(STATE_DOC).get();
    const last = state.get('lastPruneDate');
    summary.lastPruneDate = isCalendarDate(last) ? last : null;
    const shouldPrune = pruneOption === 'daily' ? pruneDue(summary.lastPruneDate, now) : pruneOption;
    if (!dryRun && shouldPrune) {
      summary.pruned = true;
      const pruned = await prune(db, now);
      summary.prunedActivity = pruned.activity;
      summary.prunedJoinRequests = pruned.joinRequests;
      summary.prunedTombstones = pruned.tombstones;
      summary.prunedMeals = pruned.meals;
      summary.pruneFailures = pruned.failures;
      // Only a prune without failures counts; otherwise the next run tries again.
      if (pruned.failures === 0) {
        summary.lastPruneDate = utcDate(now);
        await db.doc(STATE_DOC).set(
          { lastPruneDate: summary.lastPruneDate, updatedAt: FieldValue.serverTimestamp() },
          { merge: true },
        );
      }
    }
  } catch (error) {
    failed('the prune', error);
  }
  return summary;
}

interface Context {
  dryRun: boolean;
  log: (line: string) => void;
  summary: JobSummary;
  failed: (step: string, error: unknown) => void;
}

async function household(db: Firestore, sender: Sender, householdId: string, entries: Due[], ctx: Context) {
  const { dryRun, summary } = ctx;
  // Only current members (DESIGN 12.2 control 5): a removed member's profile points here until their own app
  // clears it, so the job clears it instead. One read per due user.
  const memberships = await db.getAll(...entries.map((e) => db.doc(`households/${householdId}/members/${e.user.uid}`)));
  const members = entries.filter((_, i) => memberships[i]!.exists);
  for (const { profile } of entries.filter((_, i) => !memberships[i]!.exists)) {
    summary.skippedNotMember++;
    if (dryRun) continue;
    try {
      await clearHousehold(profile);
    } catch (error) {
      ctx.failed('a profile', error);
    }
  }

  // Devices first: a user with none is skipped before anything of the household is read (DESIGN 10.3).
  const ready: Array<Due & { tokens: Token[] }> = [];
  for (const entry of members) {
    try {
      const tokens = await devicesOf(db, entry.user.uid);
      if (tokens.length === 0) {
        // Try again next run (DESIGN 10.3).
        summary.skippedNoDevices++;
        continue;
      }
      ready.push({ ...entry, tokens });
    } catch (error) {
      ctx.failed('a device read', error);
    }
  }
  if (ready.length === 0) return;

  // One stock query per household, covering every ready member's window, soonest expiry first and capped.
  const { lo, hi } = stockWindow(ready.map((m) => m.local.date));
  const stock = await db.collection(`households/${householdId}/stock`)
    .where('expiryDate', '>=', lo)
    .where('expiryDate', '<=', hi)
    .orderBy('expiryDate')
    .limit(MAX_STOCK)
    .get();
  const batches = stock.docs.map((d) => toBatch(d.data())).filter((b): b is Batch => b !== null);
  // Today's meals, only when a ready member wants them: one more query per household and run (DESIGN 9.7).
  let meals: Meal[] = [];
  if (ready.some((m) => m.user.prefs.cooking)) {
    const dates = ready.map((m) => m.local.date).sort();
    const planned = await db.collection(`households/${householdId}/meals`)
      .where('date', '>=', dates[0]!)
      .where('date', '<=', dates[dates.length - 1]!)
      .limit(MAX_MEALS)
      .get();
    meals = planned.docs.map((d) => toMeal(d.data())).filter((m): m is Meal => m !== null);
  }

  // Each user on their own: one failed send is counted and the household's other members still get theirs.
  for (const entry of ready) {
    try {
      await digestFor(db, sender, entry, batches, meals, ctx);
    } catch (error) {
      if (isNotFound(error)) {
        // The profile was deleted during the run: an account deletion, not a failure.
        summary.skippedDeleted++;
      } else {
        ctx.failed('a digest', error);
      }
    }
  }
}

/** The user's newest MAX_DEVICES device tokens. */
async function devicesOf(db: Firestore, uid: string): Promise<Token[]> {
  const devices = db.collection(`users/${uid}/devices`);
  let snapshot = await devices.orderBy('updatedAt', 'desc').limit(MAX_DEVICES).get();
  // Both apps write updatedAt; a device document without one is read only when the user has no other.
  if (snapshot.empty) snapshot = await devices.limit(MAX_DEVICES).get();
  return snapshot.docs
    .map((d) => ({ ref: d.ref, token: d.get('token') as unknown }))
    .filter((t): t is Token => typeof t.token === 'string' && t.token.length > 0);
}

async function digestFor(
  db: Firestore,
  sender: Sender,
  { user, local, profile, tokens }: Due & { tokens: Token[] },
  batches: Batch[],
  meals: Meal[],
  { dryRun, log, summary }: Context,
): Promise<void> {
  // 4. Each user's digest from their own stages and local date.
  const items = selectItems(user, local.date, batches);
  const cooking = cookingFor(user, local.date, meals);
  const userRef = profile.ref;
  const digest = buildDigestWithMeals(items, cooking);
  if (!digest) {
    summary.emptyDigests++;
    if (!dryRun) await userRef.update({ lastDigestDate: local.date });
    return;
  }

  // 5. Send to every device, in chunks; delete tokens FCM says are gone.
  const messages: Message[] = tokens.map(({ token }) => ({
    token,
    notification: { title: digest.title, body: digest.body },
    data: { type: 'expiry_digest', route: digest.mealsOnly ? PLAN_ROUTE : ROUTE, date: local.date },
    android: { priority: 'high', notification: { channelId: CHANNEL, tag: 'expiry_digest' } },
    webpush: {
      headers: { Urgency: 'high' },
      notification: { title: digest.title, body: digest.body, tag: 'expiry_digest', icon: WEB_ICON },
    },
  }));
  if (dryRun) {
    // Workflow logs are public with the repository: counts only, never a user ID, item names or meal titles.
    log(`[dry run] ${local.date}: ${items.length} item(s), ${cooking.length} meal(s) to ${messages.length} device(s)`);
    summary.sent += messages.length;
    return;
  }
  let results: SendResult[];
  try {
    results = [];
    for (const chunk of chunks(messages, SEND_CHUNK)) results.push(...await sender.sendEach(chunk));
  } catch (error) {
    // The send's own error is the one counted.
    await recordFailedAttempt(userRef, user, local.date, summary).catch(() => {});
    throw error;
  }
  const dead = tokens.filter((_, i) => results[i]?.deadToken);
  await Promise.all(dead.map((t) => t.ref.delete()));
  summary.deadTokens += dead.length;
  const delivered = results.filter((r) => r.success).length;
  summary.sent += delivered;
  log(`${local.date}: ${delivered}/${messages.length} delivered`);

  // 6. Only a delivered digest counts; otherwise the next run retries, up to MAX_SEND_ATTEMPTS a day.
  if (delivered > 0) await userRef.update({ ...afterSend(local.date), digestAttempts: FieldValue.delete() });
  else await recordFailedAttempt(userRef, user, local.date, summary);
}

/**
 * Counts a run in which the user's digest reached no device. At MAX_SEND_ATTEMPTS in one local day the user is done
 * for that day (`lastDigestDate`), so a digest that keeps failing stops costing reads in every run.
 */
async function recordFailedAttempt(
  userRef: FirebaseFirestore.DocumentReference,
  user: UserDoc,
  date: string,
  summary: JobSummary,
): Promise<void> {
  const attempts = (user.digestAttempts?.date === date ? user.digestAttempts.count : 0) + 1;
  if (attempts >= MAX_SEND_ATTEMPTS) {
    summary.gaveUp++;
    await userRef.update({ lastDigestDate: date, digestAttempts: FieldValue.delete() });
  } else {
    await userRef.update({ digestAttempts: { date, count: attempts } });
  }
}

function isNotFound(error: unknown): boolean {
  return (error as { code?: unknown } | null)?.code === GrpcStatus.NOT_FOUND;
}

/**
 * What a removed member's app writes when it notices (SessionManager), unless the profile changed since this run read
 * it: they may have joined a household again. The next run looks again.
 */
async function clearHousehold(profile: Profile): Promise<void> {
  try {
    await profile.ref.update(
      { householdId: null, updatedAt: FieldValue.serverTimestamp() },
      { lastUpdateTime: profile.updateTime },
    );
  } catch (error) {
    const code = (error as { code?: unknown }).code;
    if (code !== GrpcStatus.FAILED_PRECONDITION && code !== GrpcStatus.NOT_FOUND) throw error;
  }
}

/**
 * Deletes activity older than 30 days, join requests older than 7 days, tombstones older than 2 days and planned
 * meals more than 28 days past (after v1). Each query costs at least one read, even when empty (quota simulation).
 * A deletion that fails is counted, never thrown (bulk.ts), and so is a household whose queries fail.
 */
async function prune(
  db: Firestore,
  now: Date,
): Promise<{ activity: number; joinRequests: number; tombstones: number; meals: number; failures: number }> {
  const activityCutoff = new Date(now.getTime() - ACTIVITY_DAYS * 86_400_000);
  const requestCutoff = new Date(now.getTime() - JOIN_REQUEST_DAYS * 86_400_000);
  const tombstoneCutoff = new Date(now.getTime() - TOMBSTONE_DAYS * 86_400_000);
  // Meal dates are 'YYYY-MM-DD' strings in the household's calendar; a UTC date is close enough for a 4-week cutoff.
  const mealCutoff = new Date(now.getTime() - MEAL_DAYS * 86_400_000).toISOString().slice(0, 10);
  let activity = 0;
  let meals = 0;
  let joinRequests = 0;
  let tombstones = 0;
  let failures = 0;
  const households = await db.collection('households').select().get();
  for (const household of households.docs) {
    // One household whose queries fail is counted; the others are still pruned.
    try {
      const old = await household.ref.collection('activity').where('createdAt', '<', activityCutoff).get();
      const stale = await household.ref.collection('joinRequests').where('requestedAt', '<', requestCutoff).get();
      const pastMeals = await household.ref.collection('meals').where('date', '<', mealCutoff).get();
      // deletedAt exists only on tombstones, so a range on it reads nothing else.
      const lists = await household.ref.collection('groceryLists').select().get();
      const tombstoneQueries = [
        ...TOMBSTONED.map((name) => household.ref.collection(name)),
        ...lists.docs.map((list) => list.ref.collection('items')),
      ].map((collection) => collection.where('deletedAt', '<', tombstoneCutoff).get());
      const deleted = (await Promise.all(tombstoneQueries)).flatMap((snapshot) => snapshot.docs);
      const writes = bulkWrites(db);
      old.docs.forEach((d) => writes.delete(d.ref));
      stale.docs.forEach((d) => writes.delete(d.ref));
      deleted.forEach((d) => writes.delete(d.ref));
      // A past meal that is also an old tombstone is already being deleted.
      const deletedPaths = new Set(deleted.map((d) => d.ref.path));
      const oldMeals = pastMeals.docs.filter((d) => !deletedPaths.has(d.ref.path));
      oldMeals.forEach((d) => writes.delete(d.ref));
      failures += await writes.close();
      meals += oldMeals.length;
      activity += old.size;
      joinRequests += stale.size;
      tombstones += deleted.length;
    } catch {
      failures++;
    }
  }
  return { activity, joinRequests, tombstones, meals, failures };
}

