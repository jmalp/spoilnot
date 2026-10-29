// The hourly reminder job (DESIGN 10.1, 10.3). Reads Firestore with the Admin SDK (which bypasses the rules),
// sends one digest per due user through FCM, and prunes old activity, join requests and tombstones.
import type { Firestore } from 'firebase-admin/firestore';
import {
  afterSend, buildDigestWithMeals, cookingFor, dueAt, readPrefs, selectItems, stockWindow, type Batch, type Meal,
  type UserDoc,
} from './digest.ts';
import { isCalendarDate, type LocalTime } from './time.ts';

export interface Message {
  token: string;
  notification: { title: string; body: string };
  data: Record<string, string>;
  android: { priority: 'high'; notification: { channelId: string; tag: string } };
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
   * Delete old activity, join requests and tombstones in this run (default true; the hourly workflow prunes once a
   * day).
   */
  prune?: boolean;
  log?: (line: string) => void;
}

export interface JobSummary {
  due: number;
  sent: number;
  emptyDigests: number;
  skippedNoDevices: number;
  deadTokens: number;
  prunedActivity: number;
  prunedJoinRequests: number;
  prunedTombstones: number;
  prunedMeals: number;
}

export const ROUTE = '/pantry?filter=expiring';
/** A digest with only meals opens the Plan tab (after v1, DESIGN 5.9). */
export const PLAN_ROUTE = '/plan';
const CHANNEL = 'expiry';
const ACTIVITY_DAYS = 30;
const JOIN_REQUEST_DAYS = 7;
/** Deleted documents stay as tombstones this long (longer than the phones' daily full read, DESIGN 9.8). */
export const TOMBSTONE_DAYS = 2;
/** Household collections whose deletions are tombstones (plus every list's items). */
const TOMBSTONED = ['products', 'stock', 'recipes', 'meals'];
/** Planned meals are kept this long after their day (DESIGN 5.9). */
export const MEAL_DAYS = 28;

function toUser(id: string, data: FirebaseFirestore.DocumentData): UserDoc {
  return {
    uid: id,
    householdId: typeof data.householdId === 'string' ? data.householdId : null,
    timezone: typeof data.timezone === 'string' ? data.timezone : null,
    locale: typeof data.locale === 'string' ? data.locale : null,
    prefs: readPrefs(data.notificationPrefs),
    lastDigestDate: isCalendarDate(data.lastDigestDate) ? data.lastDigestDate : null,
    lastExpiredThrough: isCalendarDate(data.lastExpiredThrough) ? data.lastExpiredThrough : null,
  };
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

export async function runJob(db: Firestore, sender: Sender, options: JobOptions): Promise<JobSummary> {
  const { now, dryRun = false, prune: shouldPrune = true } = options;
  const log = options.log ?? (() => {});
  const summary: JobSummary = { due: 0, sent: 0, emptyDigests: 0, skippedNoDevices: 0, deadTokens: 0, prunedActivity: 0, prunedJoinRequests: 0, prunedTombstones: 0, prunedMeals: 0 };

  // 1-2. Users with reminders on who are due now.
  const users = await db.collection('users').where('notificationPrefs.enabled', '==', true).get();
  const due: Array<{ user: UserDoc; local: LocalTime }> = [];
  for (const doc of users.docs) {
    const user = toUser(doc.id, doc.data());
    const local = dueAt(user, now);
    if (local) due.push({ user, local });
  }
  summary.due = due.length;

  // 3. One stock query per household, covering every due member's window.
  const byHousehold = new Map<string, typeof due>();
  for (const entry of due) {
    const list = byHousehold.get(entry.user.householdId!) ?? [];
    list.push(entry);
    byHousehold.set(entry.user.householdId!, list);
  }

  for (const [householdId, members] of byHousehold) {
    const { lo, hi } = stockWindow(members.map((m) => m.local.date));
    const stock = await db.collection(`households/${householdId}/stock`)
      .where('expiryDate', '>=', lo)
      .where('expiryDate', '<=', hi)
      .get();
    const batches = stock.docs.map((d) => toBatch(d.data())).filter((b): b is Batch => b !== null);
    // Today's meals, only when a due member wants them: one more query per household and run (DESIGN 9.7).
    let meals: Meal[] = [];
    if (members.some((m) => m.user.prefs.cooking)) {
      const dates = members.map((m) => m.local.date).sort();
      const planned = await db.collection(`households/${householdId}/meals`)
        .where('date', '>=', dates[0]!)
        .where('date', '<=', dates[dates.length - 1]!)
        .get();
      meals = planned.docs.map((d) => toMeal(d.data())).filter((m): m is Meal => m !== null);
    }

    for (const { user, local } of members) {
      // 4. Each user's digest from their own stages and local date.
      const items = selectItems(user, local.date, batches);
      const cooking = cookingFor(user, local.date, meals);
      const userRef = db.doc(`users/${user.uid}`);
      const devices = await db.collection(`users/${user.uid}/devices`).get();
      if (devices.empty) {
        // Try again next run (DESIGN 10.3).
        summary.skippedNoDevices++;
        continue;
      }
      const digest = buildDigestWithMeals(items, cooking);
      if (!digest) {
        summary.emptyDigests++;
        if (!dryRun) await userRef.update({ lastDigestDate: local.date });
        continue;
      }

      // 5. Send to every device; delete dead tokens.
      const tokens = devices.docs
        .map((d) => ({ ref: d.ref, token: d.get('token') as unknown }))
        .filter((t): t is { ref: FirebaseFirestore.DocumentReference; token: string } => typeof t.token === 'string');
      const messages: Message[] = tokens.map(({ token }) => ({
        token,
        notification: { title: digest.title, body: digest.body },
        data: { type: 'expiry_digest', route: digest.mealsOnly ? PLAN_ROUTE : ROUTE, date: local.date },
        android: { priority: 'high', notification: { channelId: CHANNEL, tag: 'expiry_digest' } },
      }));
      if (dryRun) {
        // Workflow logs are public with the repository: counts only, never a user ID, item names or meal titles.
        log(`[dry run] ${local.date}: ${items.length} item(s), ${cooking.length} meal(s) to ${messages.length} device(s)`);
        summary.sent += messages.length;
        continue;
      }
      const results = await sender.sendEach(messages);
      const dead = tokens.filter((_, i) => results[i]?.deadToken);
      await Promise.all(dead.map((t) => t.ref.delete()));
      summary.deadTokens += dead.length;
      const delivered = results.filter((r) => r.success).length;
      summary.sent += delivered;
      log(`${local.date}: ${delivered}/${messages.length} delivered`);

      // 6. Only a delivered digest counts; otherwise the next run retries.
      if (delivered > 0) await userRef.update(afterSend(local.date));
    }
  }

  // 7. Housekeeping (DESIGN 5.1, 5.6).
  if (!dryRun && shouldPrune) {
    const pruned = await prune(db, now);
    summary.prunedActivity = pruned.activity;
    summary.prunedJoinRequests = pruned.joinRequests;
    summary.prunedTombstones = pruned.tombstones;
    summary.prunedMeals = pruned.meals;
  }
  return summary;
}

/**
 * Deletes activity older than 30 days, join requests older than 7 days, tombstones older than 2 days and planned
 * meals more than 28 days past (after v1). Each query costs at least one read, even when empty (quota simulation).
 */
async function prune(
  db: Firestore,
  now: Date,
): Promise<{ activity: number; joinRequests: number; tombstones: number; meals: number }> {
  const activityCutoff = new Date(now.getTime() - ACTIVITY_DAYS * 86_400_000);
  const requestCutoff = new Date(now.getTime() - JOIN_REQUEST_DAYS * 86_400_000);
  const tombstoneCutoff = new Date(now.getTime() - TOMBSTONE_DAYS * 86_400_000);
  // Meal dates are 'YYYY-MM-DD' strings in the household's calendar; a UTC date is close enough for a 4-week cutoff.
  const mealCutoff = new Date(now.getTime() - MEAL_DAYS * 86_400_000).toISOString().slice(0, 10);
  let activity = 0;
  let meals = 0;
  let joinRequests = 0;
  let tombstones = 0;
  const households = await db.collection('households').select().get();
  for (const household of households.docs) {
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
    const writer = db.bulkWriter();
    old.docs.forEach((d) => writer.delete(d.ref));
    stale.docs.forEach((d) => writer.delete(d.ref));
    deleted.forEach((d) => writer.delete(d.ref));
    // A past meal that is also an old tombstone is already being deleted.
    const deletedPaths = new Set(deleted.map((d) => d.ref.path));
    const oldMeals = pastMeals.docs.filter((d) => !deletedPaths.has(d.ref.path));
    oldMeals.forEach((d) => writer.delete(d.ref));
    await writer.close();
    meals += oldMeals.length;
    activity += old.size;
    joinRequests += stale.size;
    tombstones += deleted.length;
  }
  return { activity, joinRequests, tombstones, meals };
}

