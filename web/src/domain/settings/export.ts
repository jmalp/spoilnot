// Settings > Data > Export (DESIGN 13.4), ported from HouseholdExport.kt: products, stock, lists, recipes, Insights
// counts and meals as one JSON document, in the same format as the Android app's export.
import type { LocalDate } from '../time';

/** A point in time from Firestore, written like java.time.Instant ("2026-10-17T12:00:00Z"). */
export class Instant {
  constructor(
    readonly seconds: number,
    readonly nanos = 0,
  ) {}

  static ofMillis = (millis: number) => new Instant(Math.floor(millis / 1000), (((millis % 1000) + 1000) % 1000) * 1e6);

  /** ISO 8601 in UTC with the fraction only when there is one, in groups of three digits (Instant.toString). */
  toString(): string {
    const base = new Date(this.seconds * 1000).toISOString().slice(0, 19);
    if (this.nanos === 0) return `${base}Z`;
    const digits = String(this.nanos).padStart(9, '0');
    const fraction = this.nanos % 1e6 === 0 ? digits.slice(0, 3) : this.nanos % 1e3 === 0 ? digits.slice(0, 6) : digits;
    return `${base}.${fraction}Z`;
  }
}

/** Plain values read for the export: maps, lists, strings, numbers, booleans and instants. */
export type ExportValue = null | string | number | boolean | Instant | ExportValue[] | { [key: string]: ExportValue };

/** A document read for the export: its id and plain values. */
export type ExportDoc = { id: string; fields: Record<string, ExportValue> };

/** Everything the export contains. */
export type ExportData = {
  household: ExportDoc | null;
  members: ExportDoc[];
  products: ExportDoc[];
  stock: ExportDoc[];
  /** Each list with its items. */
  groceryLists: [ExportDoc, ExportDoc[]][];
  recipes: ExportDoc[];
  /** Insights counts, one document per month. */
  stats: ExportDoc[];
  /** Planned meals (DESIGN 5.9). */
  meals: ExportDoc[];
};

/** The household's data as a JSON document; reads the server when online, the phone's copy otherwise. */
export interface HouseholdExporter {
  export(householdId: string): Promise<string>;
}

type Json = null | string | number | boolean | Json[] | { [key: string]: Json };

const byKey = (entries: [string, Json][]) =>
  Object.fromEntries(entries.sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)));

export const ExportFormat = {
  VERSION: 1,

  fileName: (today: LocalDate) => `spoilnot-export-${today}.json`,

  write(data: ExportData, exportedAt: Instant, appVersion: string): string {
    const root: Record<string, Json> = {
      format: 'spoilnot-export',
      formatVersion: ExportFormat.VERSION,
      appVersion,
      exportedAt: exportedAt.toString(),
      household: data.household ? doc(data.household) : null,
      members: data.members.map(doc),
      products: data.products.map(doc),
      stock: data.stock.map(doc),
      groceryLists: data.groceryLists.map(([list, items]) => ({ ...doc(list), items: items.map(doc) })),
      recipes: data.recipes.map(doc),
      stats: data.stats.map(doc),
      meals: data.meals.map(doc),
    };
    // Four spaces, like kotlinx.serialization's prettyPrint.
    return JSON.stringify(root, null, 4);
  },

  /** Plain values to JSON; instants become ISO 8601 strings and map keys are sorted. */
  value(v: ExportValue): Json {
    if (v instanceof Instant) return v.toString();
    if (Array.isArray(v)) return v.map(ExportFormat.value);
    if (v !== null && typeof v === 'object') {
      return byKey(Object.entries(v).map(([k, x]): [string, Json] => [k, ExportFormat.value(x)]));
    }
    return v;
  },
};

const doc = (d: ExportDoc): { [key: string]: Json } => ({
  id: d.id,
  ...byKey(Object.entries(d.fields).map(([k, x]): [string, Json] => [k, ExportFormat.value(x)])),
});
