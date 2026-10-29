// Nightly JSON exports and restoring from one (DESIGN 13.4, M7). The file format is the app's Settings > Export
// format (formatVersion 1), so a file from either source can be restored. Uses the Admin SDK.
import { Timestamp, type DocumentData, type Firestore } from 'firebase-admin/firestore';

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

/** JSON back to Firestore values: ISO instants become timestamps again; calendar dates stay strings (DESIGN 8.1). */
export function fromJson(value: Json): unknown {
  if (typeof value === 'string' && ISO_INSTANT.test(value)) return Timestamp.fromDate(new Date(value));
  if (Array.isArray(value)) return value.map(fromJson);
  if (value !== null && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [key, v] of Object.entries(value)) out[key] = fromJson(v);
    return out;
  }
  return value;
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
  /** Document path to its data, in write order. */
  writes: Array<{ path: string; data: Record<string, unknown> }>;
}

/**
 * What a restore writes: products, stock, grocery lists and their items, recipes and Insights counts, each document
 * set as it was.
 * The household and its members are never restored: they decide who has access, and the export may be older than a
 * removal. Documents created after the export are left alone.
 */
export function planRestore(file: HouseholdExport, householdId: string): RestorePlan {
  if (file.format !== FORMAT || file.formatVersion > FORMAT_VERSION) {
    throw new Error(`Not a Spoil Not export this job understands (format ${file.format} ${file.formatVersion})`);
  }
  const base = `households/${householdId}`;
  const writes: RestorePlan['writes'] = [];
  const add = (path: string, d: JsonDoc) => {
    const { id: _id, ...fields } = d;
    writes.push({ path, data: fromJson(fields) as Record<string, unknown> });
  };
  for (const p of file.products) add(`${base}/products/${p.id}`, p);
  for (const s of file.stock) add(`${base}/stock/${s.id}`, s);
  for (const list of file.groceryLists) {
    const { items, ...listDoc } = list;
    add(`${base}/groceryLists/${list.id}`, listDoc as JsonDoc);
    for (const item of items) add(`${base}/groceryLists/${list.id}/items/${item.id}`, item);
  }
  for (const r of file.recipes) add(`${base}/recipes/${r.id}`, r);
  for (const m of file.stats ?? []) add(`${base}/stats/${m.id}`, m);
  for (const m of file.meals ?? []) add(`${base}/meals/${m.id}`, m);
  return { householdId, writes };
}

/** Applies a plan with the Admin SDK's bulk writer. The household must still exist. */
export async function applyRestore(db: Firestore, plan: RestorePlan): Promise<number> {
  const household = await db.doc(`households/${plan.householdId}`).get();
  if (!household.exists) throw new Error(`Household ${plan.householdId} does not exist; create it in the app first`);
  const writer = db.bulkWriter();
  for (const w of plan.writes) void writer.set(db.doc(w.path), w.data);
  await writer.close();
  return plan.writes.length;
}

/** The first four characters of a household ID, enough to pick an export without publishing the ID. */
export function shortId(id: string): string {
  return id.length > 4 ? `${id.slice(0, 4)}…` : id;
}
