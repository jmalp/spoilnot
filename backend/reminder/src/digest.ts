// Who gets a digest, what is in it, and how it reads (DESIGN 5.4, 10.3). Pure functions, tested with fixed clocks.
import { addDays, daysBetween, toLocal, type LocalTime } from './time.ts';

export type ExpiredMode = 'once' | 'daily' | 'never';

export interface NotificationPrefs {
  enabled: boolean;
  /** Minutes after local midnight; the UI offers whole hours. Default 540 (9 AM). */
  digestMinutes: number;
  /** Subset of [3, 2, 1, 0]. */
  stages: number[];
  expiredMode: ExpiredMode;
  /** After v1 (DESIGN 5.9): add the meals the user cooks today. Default on. */
  cooking: boolean;
}

export interface UserDoc {
  uid: string;
  householdId: string | null;
  timezone: string | null;
  locale: string | null;
  prefs: NotificationPrefs;
  lastDigestDate: string | null;
  lastExpiredThrough: string | null;
  /** Runs on `date` (the user's local date) in which the digest reached no device; the job's own field. */
  digestAttempts?: { date: string; count: number } | null;
}

export interface Batch {
  productName: string;
  expiryDate: string;
  dateType: 'use_by' | 'best_before';
  quantity: number;
}

/** 'expired' or the number of days left (0 to 3). */
export type Stage = 'expired' | 0 | 1 | 2 | 3;

export interface DigestItem {
  stage: Stage;
  name: string;
  dateType: Batch['dateType'];
  /** Days until expiry; negative when expired. */
  days: number;
}

/** No digest after 22:00 local time; the next day's run catches up (DESIGN 10.3). */
export const LATEST_MINUTES = 22 * 60;

export const DEFAULT_PREFS: NotificationPrefs = { enabled: true, digestMinutes: 540, stages: [3, 2, 1, 0], expiredMode: 'once', cooking: true };

/** Prefs as stored, with defaults for anything missing (new fields are optional on read, DESIGN 8.1). */
export function readPrefs(raw: unknown): NotificationPrefs {
  const p = (raw ?? {}) as Partial<NotificationPrefs>;
  const stages = Array.isArray(p.stages) ? p.stages.filter((s) => [0, 1, 2, 3].includes(s)) : DEFAULT_PREFS.stages;
  const mode = p.expiredMode === 'daily' || p.expiredMode === 'never' ? p.expiredMode : 'once';
  return {
    enabled: p.enabled !== false,
    digestMinutes: typeof p.digestMinutes === 'number' ? p.digestMinutes : DEFAULT_PREFS.digestMinutes,
    stages,
    expiredMode: mode,
    cooking: p.cooking !== false,
  };
}

/** Local time when this user is due now, otherwise null. */
export function dueAt(user: UserDoc, now: Date): LocalTime | null {
  if (!user.prefs.enabled || !user.householdId) return null;
  const local = toLocal(now, user.timezone);
  if (local.minutesOfDay < user.prefs.digestMinutes) return null;
  if (local.minutesOfDay >= LATEST_MINUTES) return null;
  if (user.lastDigestDate === local.date) return null;
  return local;
}

/** The stock window to load for users whose local dates are `dates` (DESIGN 10.3). */
export function stockWindow(dates: string[]): { lo: string; hi: string } {
  const sorted = [...dates].sort();
  return { lo: addDays(sorted[0]!, -14), hi: addDays(sorted[sorted.length - 1]!, 3) };
}

/** Expiring within this many days, today included, needs attention (DESIGN 5.2 statuses Today to Soon). */
export const ATTENTION_DAYS = 3;

/** A batch in the Needs attention set, with its calendar days left (negative once expired). */
export interface AttentionBatch {
  batch: Batch;
  days: number;
}

/**
 * Needs attention (DESIGN 5.4, 5.6; one set and one name after v1, review R8): every batch in stock that has expired or
 * expires within [ATTENTION_DAYS] days of [localDate]. The apps compute the same set (`NeedsAttention` on Android,
 * `NeedsAttention` in web/src/domain/pantry/items.ts) for Home's card and Pantry's Needs attention filter, which the
 * digest's tap opens. The digest narrows it by the member's choices ([selectItems]).
 */
export function needsAttention(localDate: string, batches: Batch[]): AttentionBatch[] {
  const set: AttentionBatch[] = [];
  for (const batch of batches) {
    if (!(batch.quantity > 0) || !batch.expiryDate) continue;
    const days = daysBetween(localDate, batch.expiryDate);
    if (days <= ATTENTION_DAYS) set.push({ batch, days });
  }
  return set;
}

/**
 * The items for one user's digest: the [needsAttention] set narrowed by their own choices. Each of the stages 3, 2, 1
 * and 0 days can be off, and expired items come once (only those that expired after the last digest that reported
 * expired items), every day or never. The stock read reaches 14 days back ([stockWindow]), so an item expired longer
 * ago is never in a digest, though it stays in the app's set.
 */
export function selectItems(user: UserDoc, localDate: string, batches: Batch[]): DigestItem[] {
  const items: DigestItem[] = [];
  for (const { batch: b, days: d } of needsAttention(localDate, batches)) {
    if (d >= 0) {
      if (user.prefs.stages.includes(d)) {
        items.push({ stage: d as Stage, name: b.productName, dateType: b.dateType, days: d });
      }
    } else if (user.prefs.expiredMode !== 'never') {
      // Once: only items that expired after the last digest that reported expired items.
      if (user.prefs.expiredMode === 'daily' || b.expiryDate > (user.lastExpiredThrough ?? '')) {
        items.push({ stage: 'expired', name: b.productName, dateType: b.dateType, days: d });
      }
    }
  }
  return items;
}

const MAX_NAMES = 3;
/** A product name or meal title in a digest is cut to this many characters (DESIGN 10.3). */
export const MAX_NAME_CHARS = 80;
/** The body is cut to this many UTF-8 bytes, well inside FCM's 4,096-byte message limit (DESIGN 10.3). */
export const MAX_BODY_BYTES = 1000;

/** At most [max] characters (code points), ending with "…" when cut. */
export function shorten(text: string, max = MAX_NAME_CHARS): string {
  const chars = [...text];
  return chars.length <= max ? text : `${chars.slice(0, max - 1).join('')}…`;
}

/** At most [max] UTF-8 bytes, cut between characters and ending with "…" when cut. */
export function capBytes(text: string, max = MAX_BODY_BYTES): string {
  const encoder = new TextEncoder();
  if (encoder.encode(text).length <= max) return text;
  const room = max - encoder.encode('…').length;
  let out = '';
  let bytes = 0;
  for (const ch of text) {
    const size = encoder.encode(ch).length;
    if (bytes + size > room) break;
    out += ch;
    bytes += size;
  }
  return `${out}…`;
}

function names(items: DigestItem[]): string {
  const unique = [...new Set(items.map((i) => shorten(i.name)))];
  const shown = unique.slice(0, MAX_NAMES).join(', ');
  return unique.length > MAX_NAMES ? `${shown} +${unique.length - MAX_NAMES} more` : shown;
}

/** Most urgent first, in the body's order: throw away, past best before, today, tomorrow, in 2 to 3 days. */
function urgency(item: DigestItem): number {
  if (item.stage === 'expired') return item.dateType === 'use_by' ? 0 : 1;
  return 2 + item.stage;
}

function single(item: DigestItem): string {
  const bb = item.dateType === 'best_before';
  const name = shorten(item.name);
  switch (item.stage) {
    case 'expired':
      return bb ? `${name} is past its best before date` : `${name} has expired. Throw it away`;
    case 0:
      return bb ? `${name} is best before today` : `${name} expires today`;
    case 1:
      return bb ? `${name} is best before tomorrow` : `${name} expires tomorrow`;
    default:
      return bb ? `${name} is best before in ${item.days} days` : `${name} expires in ${item.days} days`;
  }
}

/**
 * The notification text (DESIGN 10.3). One product: "Milk expires tomorrow", from its most urgent batch. Several:
 * "N items need attention", N being the number of distinct names.
 * The body lists, in order, Throw away, Past best before, Today, Tomorrow and In 2 to 3 days, up to three names
 * each then "+K more". Best-before items say "past best before" instead of "throw away".
 */
export function buildDigest(items: DigestItem[]): { title: string; body: string } | null {
  const distinct = new Map<string, DigestItem>();
  for (const i of items) distinct.set(`${i.stage}|${i.name}|${i.dateType}`, i);
  const all = [...distinct.values()];
  if (all.length === 0) return null;

  const groups: Array<[string, DigestItem[]]> = [
    ['Throw away', all.filter((i) => i.stage === 'expired' && i.dateType === 'use_by')],
    ['Past best before', all.filter((i) => i.stage === 'expired' && i.dateType === 'best_before')],
    ['Today', all.filter((i) => i.stage === 0)],
    ['Tomorrow', all.filter((i) => i.stage === 1)],
    ['In 2 to 3 days', all.filter((i) => i.stage === 2 || i.stage === 3)],
  ];
  const body = groups
    .filter(([, list]) => list.length > 0)
    .map(([label, list]) => `${label}: ${names(list)}.`)
    .join(' ');
  const count = new Set(all.map((i) => i.name)).size;
  const mostUrgent = [...all].sort((a, b) => urgency(a) - urgency(b) || a.days - b.days)[0]!;
  const title = count === 1 ? single(mostUrgent) : `${count} items need attention`;
  return { title, body: capBytes(body) };
}

export type Slot = 'breakfast' | 'lunch' | 'dinner';

/** A planned meal as the job reads it (DESIGN 5.9). */
export interface Meal {
  date: string;
  slot: Slot;
  position: number;
  title: string;
  cooks: string[];
  cooked: boolean;
}

const SLOTS: Slot[] = ['breakfast', 'lunch', 'dinner'];

/** The meals [user] cooks on their local date, not yet cooked, breakfast first (after v1, DESIGN 5.9). */
export function cookingFor(user: UserDoc, localDate: string, meals: Meal[]): Meal[] {
  if (!user.prefs.cooking) return [];
  return meals
    .filter((m) => m.date === localDate && !m.cooked && m.cooks.includes(user.uid))
    .sort((a, b) => SLOTS.indexOf(a.slot) - SLOTS.indexOf(b.slot) || a.position - b.position);
}

/** "You're cooking: Tacos (dinner), Pancakes (breakfast)." */
function cookingLine(meals: Meal[]): string {
  return `You're cooking: ${meals.map((m) => `${shorten(m.title)} (${m.slot})`).join(', ')}.`;
}

/**
 * The digest with the user's meals for today (DESIGN 5.9). With food to report the title stays and the meals are a
 * last line; with only meals the title says what is cooked ("You're cooking Tacos for dinner").
 */
export function buildDigestWithMeals(
  items: DigestItem[],
  meals: Meal[],
): { title: string; body: string; mealsOnly: boolean } | null {
  const food = buildDigest(items);
  if (meals.length === 0) return food && { ...food, mealsOnly: false };
  const line = cookingLine(meals);
  if (food) return { title: food.title, body: capBytes(`${food.body} ${line}`), mealsOnly: false };
  const title = meals.length === 1
    ? `You're cooking ${shorten(meals[0]!.title)} for ${meals[0]!.slot}`
    : `You're cooking ${meals.length} meals today`;
  return { title, body: capBytes(line), mealsOnly: true };
}

/** What to record after a digest was delivered: today, and expired items reported up to yesterday. */
export function afterSend(localDate: string): { lastDigestDate: string; lastExpiredThrough: string } {
  return { lastDigestDate: localDate, lastExpiredThrough: addDays(localDate, -1) };
}
