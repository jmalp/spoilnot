// Settings > Data > Export (DESIGN 13.4), ported from FirestoreHouseholdExporter: reads the household's collections
// once (about 130 reads for a typical household, DESIGN 9.7), from the server when online and the phone's copy
// otherwise.
import { collection, doc, GeoPoint, getDoc, getDocs, Timestamp, type DocumentSnapshot } from 'firebase/firestore';
import {
  ExportFormat,
  Instant,
  type ExportData,
  type ExportDoc,
  type ExportValue,
  type HouseholdExporter,
} from '../domain/settings/export';
import type { Clock } from '../domain/time';
import { Tombstones } from './delta-sync';
import type { FirebaseSdk } from './firebase';
import { mapped } from './firestore-rx';

/** Firestore types to plain values. */
function plain(v: unknown): ExportValue {
  if (v === null || v === undefined) return null;
  if (v instanceof Timestamp) return new Instant(v.seconds, v.nanoseconds);
  if (v instanceof GeoPoint) return { latitude: v.latitude, longitude: v.longitude };
  if (Array.isArray(v)) return v.map(plain);
  if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') return v;
  if (Object.getPrototypeOf(v) === Object.prototype) {
    return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, plain(x)]));
  }
  return String(v);
}

const toExport = (snapshot: DocumentSnapshot): ExportDoc => ({
  id: snapshot.id,
  fields: Object.fromEntries(Object.entries(snapshot.data() ?? {}).map(([k, v]) => [k, plain(v)])),
});

export class FirestoreHouseholdExporter implements HouseholdExporter {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly clock: Clock,
    private readonly appVersion: string,
  ) {}

  async export(householdId: string): Promise<string> {
    const db = this.sdk.current;
    const base = `households/${householdId}`;
    // Deleted documents wait as tombstones for the daily job (DESIGN 9.8); they are not exported.
    const all = async (path: string) =>
      (await mapped(getDocs(collection(db, path)))).docs.filter((d) => !Tombstones.is(d)).map(toExport);
    const household = await mapped(getDoc(doc(db, base)));
    const lists = (await mapped(getDocs(collection(db, `${base}/groceryLists`)))).docs;
    const data: ExportData = {
      household: household.exists() ? toExport(household) : null,
      members: await all(`${base}/members`),
      products: await all(`${base}/products`),
      stock: await all(`${base}/stock`),
      groceryLists: await Promise.all(
        lists.map(async (list): Promise<[ExportDoc, ExportDoc[]]> => [
          toExport(list),
          await all(`${base}/groceryLists/${list.id}/items`),
        ]),
      ),
      recipes: await all(`${base}/recipes`),
      stats: await all(`${base}/stats`),
      meals: await all(`${base}/meals`),
    };
    return ExportFormat.write(data, Instant.ofMillis(this.clock.now()), this.appVersion);
  }
}
