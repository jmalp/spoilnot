// Nightly JSON exports and restoring from one (DESIGN 13.4, M7). The file format is the app's Settings > Export
// format (formatVersion 1), so a file from either source can be restored. Uses the Admin SDK.
// The nightly export leaves out profiles and the activity log, which the apps' Export includes (after v1, 2026-11-11).
import { FieldValue, Timestamp, type DocumentData, type Firestore } from 'firebase-admin/firestore';
import { bulkWrites } from './bulk.ts';
import { JobError } from './errors.ts';

export const FORMAT = 'spoilnot-export';
export const FORMAT_VERSION = 1;

type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
type JsonDoc = { id: string; [field: string]: Json };

export interface HouseholdExport {
  format: typeof FORMAT;
  formatVersion: number;
  appVersion: string;
  exportedAt: string;
  household: JsonDoc | null;
  members: JsonDoc[];
  products: JsonDoc[];
  stock: JsonDoc[];
  groceryLists: Array<JsonDoc & { items: JsonDoc[] }>;
  recipes: JsonDoc[];
  /** Insights counts, one document per month (added after v1; older files have none). */
  stats?: JsonDoc[];
  /** Planned meals (after v1, DESIGN 5.9; older files have none). */
  meals?: JsonDoc[];
  /** The exporting person's own profile (the apps' Export after 2026-11-11; never restored). */
  profile?: JsonDoc | null;
  /** The household's activity log (the apps' Export after 2026-11-11; never restored). */
  activity?: JsonDoc[];
}

const ISO_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/;

/** Firestore values to JSON: timestamps become ISO 8601 strings, as in the app's export. */
export function toJson(value: unknown): Json {
  if (value === null || value === undefined) return null;
  if (value instanceof Timestamp) return value.toDate().toISOString();
  if (value instanceof Date) return value.toISOString();
  if (Array.isArray(value)) return value.map(toJson);
  if (typeof value === 'object') {
    const out: Record<string, Json> = {};
    for (const key of Object.keys(value as object).sort()) out[key] = toJson((value as Record<string, unknown>)[key]);
    return out;
  }
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value;
  return String(value);
}

/**
 * The fields the data model stores as timestamps (DESIGN 8.1). Only these come back as timestamps; any other text
 * stays text, even when it looks like a date-time (a note "2026-12-24T18:00:00Z").
 */
export const TIMESTAMP_FIELDS: ReadonlySet<string> = new Set([
  'createdAt', 'updatedAt', 'deletedAt', 'checkedAt', 'cookedAt', 'joinedAt', 'requestedAt', 'exportedAt',
]);

/** JSON back to Firestore values, unchanged: strings stay strings, calendar dates included (DESIGN 8.1). */
export function fromJson(value: Json): unknown {
  if (Array.isArray(value)) return value.map(fromJson);
  if (value !== null && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [key, v] of Object.entries(value)) out[key] = fromJson(v);
    return out;
  }
  return value;
}

/** One exported document's fields back to Firestore values: the known timestamp fields become timestamps again. */
export function fromJsonDoc(fields: Record<string, Json>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, v] of Object.entries(fields)) {
    out[key] = TIMESTAMP_FIELDS.has(key) && typeof v === 'string' && ISO_INSTANT.test(v)
      ? Timestamp.fromDate(new Date(v))
      : fromJson(v);
  }
  return out;
}

function doc(id: string, data: DocumentData): JsonDoc {
  return { id, ...(toJson(data) as Record<string, Json>) };
}

/** One household, read in full. Tombstones (deleted documents waiting for the daily job, DESIGN 9.8) are left out. */
export async function exportHousehold(db: Firestore, householdId: string, now: Date, appVersion: string): Promise<HouseholdExport> {
  const base = db.doc(`households/${householdId}`);
  const all = async (path: string) => (await db.collection(path).get()).docs
    .filter((d) => d.get('deletedAt') == null)
    .map((d) => doc(d.id, d.data()));
  const household = await base.get();
  const lists = await base.collection('groceryLists').get();
  return {
    format: FORMAT,
    formatVersion: FORMAT_VERSION,
    appVersion,
    exportedAt: now.toISOString(),
    household: household.exists ? doc(household.id, household.data()!) : null,
    members: await all(`${base.path}/members`),
    products: await all(`${base.path}/products`),
    stock: await all(`${base.path}/stock`),
    groceryLists: await Promise.all(
      lists.docs.map(async (l) => ({ ...doc(l.id, l.data()), items: await all(`${l.ref.path}/items`) })),
    ),
    recipes: await all(`${base.path}/recipes`),
    stats: await all(`${base.path}/stats`),
    meals: await all(`${base.path}/meals`),
  };
}

/** Every household's id. */
export async function householdIds(db: Firestore): Promise<string[]> {
  return (await db.collection('households').select().get()).docs.map((d) => d.id);
}

export interface RestorePlan {
  householdId: string;
  /** The exported household's owner, which the target's must match unless asked otherwise; null when unknown. */
  sourceOwnerUid: string | null;
  /** Document path to its data, in write order. */
  writes: Array<{ path: string; data: Record<string, unknown> }>;
}

/** Who wrote a restored document, in `updatedBy` (after v1, 2026-11-11); never a member's uid. */
export const RESTORE_AUTHOR = 'restore';

/**
 * What a restore writes: products, stock, grocery lists and their items, recipes, Insights counts and planned meals,
 * each document set as it was, with a new update stamp: `updatedAt` is the server's time of the restore, so every
 * phone's changes-only listener (DESIGN 9.8) picks it up within a minute, and `updatedBy` is 'restore'. Grocery lists
 * take `updatedAt` only, the one stamp their rules allow.
 * The household and its members are never restored: they decide who has access, and the export may be older than a
 * removal. Nor are a profile or the activity log that an app's export may hold. Documents created after the export
 * are left alone.
 */
export function planRestore(file: HouseholdExport, householdId: string): RestorePlan {
  if (file.format !== FORMAT || file.formatVersion > FORMAT_VERSION) {
    throw new JobError('not-an-export', `Not a Spoil Not export this job understands (format ${file.format} ${file.formatVersion})`);
  }
  const base = `households/${householdId}`;
  const writes: RestorePlan['writes'] = [];
  const add = (path: string, d: JsonDoc, stamp: Record<string, unknown> = fullStamp()) => {
    const { id: _id, ...fields } = d;
    writes.push({ path, data: { ...fromJsonDoc(fields), ...stamp } });
  };
  for (const p of file.products) add(`${base}/products/${p.id}`, p);
  for (const s of file.stock) add(`${base}/stock/${s.id}`, s);
  for (const list of file.groceryLists) {
    const { items, ...listDoc } = list;
    add(`${base}/groceryLists/${list.id}`, listDoc as JsonDoc, { updatedAt: FieldValue.serverTimestamp() });
    for (const item of items) add(`${base}/groceryLists/${list.id}/items/${item.id}`, item);
  }
  for (const r of file.recipes) add(`${base}/recipes/${r.id}`, r);
  for (const m of file.stats ?? []) add(`${base}/stats/${m.id}`, m);
  for (const m of file.meals ?? []) add(`${base}/meals/${m.id}`, m);
  const owner = file.household?.ownerUid;
  return { householdId, sourceOwnerUid: typeof owner === 'string' ? owner : null, writes };
}

function fullStamp(): Record<string, unknown> {
  return { updatedAt: FieldValue.serverTimestamp(), updatedBy: RESTORE_AUTHOR };
}

export interface RestoreOptions {
  /** Write into a household whose owner is not the exported household's (the workflow's "different owner"). */
  differentOwner?: boolean;
}

/**
 * Checks the target before anything is written: it must exist, and its owner must be the exported household's
 * owner unless [options.differentOwner] says otherwise, so a mistyped ID cannot put one family's pantry in another's
 * household. Returns whether the owners match. Reads one document.
 */
export async function checkTarget(db: Firestore, plan: RestorePlan, options: RestoreOptions = {}): Promise<boolean> {
  const household = await db.doc(`households/${plan.householdId}`).get();
  if (!household.exists) {
    throw new JobError('no-such-household', `Household ${shortId(plan.householdId)} does not exist; create it in the app first`);
  }
  const sameOwner = plan.sourceOwnerUid != null && household.get('ownerUid') === plan.sourceOwnerUid;
  if (!sameOwner && !options.differentOwner) {
    throw new JobError('different-owner', `Household ${shortId(plan.householdId)} has a different owner than the export`);
  }
  return sameOwner;
}

/**
 * Applies a plan with the Admin SDK's bulk writer, after checkTarget. A document that cannot be written is counted,
 * not thrown (bulk.ts); running the restore again writes the same documents.
 */
export async function applyRestore(
  db: Firestore,
  plan: RestorePlan,
  options: RestoreOptions = {},
): Promise<{ written: number; failed: number }> {
  await checkTarget(db, plan, options);
  const writes = bulkWrites(db);
  for (const w of plan.writes) writes.set(db.doc(w.path), w.data);
  const failed = await writes.close();
  return { written: plan.writes.length - failed, failed };
}

/** The first four characters of a household ID, enough to pick an export without publishing the ID. */
export function shortId(id: string): string {
  return id.length > 4 ? `${id.slice(0, 4)}…` : id;
}
