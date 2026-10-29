// Products and batches (feature/pantry/data in the app): changes-only listeners and the writes of PantryChange.
import {
  collection,
  doc,
  increment,
  arrayUnion,
  serverTimestamp,
  Timestamp,
  type DocumentSnapshot,
  type Firestore,
  type WriteBatch,
} from 'firebase/firestore';
import { combineLatest, distinctUntilChanged, map, switchMap, type Observable } from 'rxjs';
import {
  categoryOf,
  dateTypeOf,
  isPantryChange,
  locationOf,
  unitOf,
  type PantryData,
  type Product,
  type StockBatch,
} from '../domain/pantry/models';
import type { PantryRepository } from '../domain/pantry/repository';
import { isLocalDate } from '../domain/time';
import type { Actor, HouseholdChange } from '../domain/write';
import { liveDocuments, Stamps, Tombstones, type SyncLog } from './delta-sync';
import type { FirebaseSdk } from './firebase';
import type { ChangeMapper } from './writer';

export const products = (hid: string) => `households/${hid}/products`;
export const stock = (hid: string) => `households/${hid}/stock`;
export const activity = (hid: string) => `households/${hid}/activity`;

const num = (value: unknown): number | null => (typeof value === 'number' ? value : null);
const str = (value: unknown): string | null => (typeof value === 'string' ? value : null);
const date = (value: unknown) => (isLocalDate(value) ? value : null);
/** A server timestamp, estimated while it is pending (ServerTimestampBehavior.ESTIMATE). */
const millis = (s: DocumentSnapshot, field: string): number | null =>
  (s.get(field, { serverTimestamps: 'estimate' }) as Timestamp | null)?.toMillis?.() ?? null;

// New fields are optional on read with defaults, because phones run different versions (DESIGN 8.1).
export function toProduct(s: DocumentSnapshot): Product {
  return {
    id: s.id,
    name: str(s.get('name')) ?? '',
    nameKey: str(s.get('nameKey')) ?? '',
    category: categoryOf(s.get('category')),
    defaultLocation: locationOf(s.get('defaultLocation')),
    unit: unitOf(s.get('unit')),
    minQuantity: num(s.get('minQuantity')),
    autoAddToGrocery: s.get('autoAddToGrocery') === true,
    buyQuantity: num(s.get('buyQuantity')),
    restockArmed: s.get('restockArmed') !== false,
    typicalShelfLifeDays: num(s.get('typicalShelfLifeDays')),
    recentShelfLifeDays: ((s.get('recentShelfLifeDays') as unknown[] | null) ?? []).filter(
      (v): v is number => typeof v === 'number',
    ),
    createdAt: millis(s, 'createdAt'),
    lastAddedQuantity: num(s.get('lastAddedQuantity')),
    aliases: ((s.get('aliases') as unknown[] | null) ?? []).filter((v): v is string => typeof v === 'string'),
    openedShelfLifeDays: num(s.get('openedShelfLifeDays')),
  };
}

export function toBatch(s: DocumentSnapshot): StockBatch {
  return {
    id: s.id,
    productId: str(s.get('productId')) ?? '',
    productName: str(s.get('productName')) ?? '',
    unit: unitOf(s.get('unit')),
    category: categoryOf(s.get('category')),
    quantity: num(s.get('quantity')) ?? 0,
    expiryDate: date(s.get('expiryDate')),
    dateType: dateTypeOf(s.get('dateType')),
    location: locationOf(s.get('location')),
    note: str(s.get('note')),
    createdAt: millis(s, 'createdAt'),
    pendingSync: s.metadata.hasPendingWrites,
    openedOn: date(s.get('openedOn')),
  };
}

export class FirestorePantryRepository implements PantryRepository {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly log: SyncLog,
  ) {}

  observe(householdId: string): Observable<PantryData> {
    return this.sdk.instance$.pipe(
      switchMap((db) =>
        combineLatest([
          liveDocuments(db, collection(db, products(householdId)), this.log),
          liveDocuments(db, collection(db, stock(householdId)), this.log),
        ]),
      ),
      map(([p, s]) => ({
        products: p.documents.map(toProduct),
        batches: s.documents.map(toBatch),
        fromCache: p.fromCache || s.fromCache,
      })),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }
}

/** Every field written explicitly, nulls included (DESIGN 8.1). */
const productFields = (p: Product) => ({
  name: p.name,
  nameKey: p.nameKey,
  category: p.category,
  defaultLocation: p.defaultLocation,
  unit: p.unit,
  minQuantity: p.minQuantity,
  autoAddToGrocery: p.autoAddToGrocery,
  buyQuantity: p.buyQuantity,
  restockArmed: p.restockArmed,
  typicalShelfLifeDays: p.typicalShelfLifeDays,
  recentShelfLifeDays: p.recentShelfLifeDays,
  lastAddedQuantity: p.lastAddedQuantity,
  openedShelfLifeDays: p.openedShelfLifeDays,
});

const batchFields = (b: StockBatch) => ({
  productId: b.productId,
  productName: b.productName,
  unit: b.unit,
  category: b.category,
  quantity: b.quantity,
  expiryDate: b.expiryDate,
  dateType: b.dateType,
  location: b.location,
  note: b.note,
  openedOn: b.openedOn,
});

/** Firestore writes for PantryChange (PantryChangeMapper). */
export const pantryMapper: ChangeMapper = {
  write(batch: WriteBatch, db: Firestore, hid: string, actor: Actor, change: HouseholdChange): boolean {
    if (!isPantryChange(change)) return false;
    const product = (id: string) => doc(db, products(hid), id);
    const batchDoc = (id: string) => doc(db, stock(hid), id);
    const stamp = Stamps.updated(actor);
    switch (change.type) {
      case 'pantry.setRestockArmed':
        batch.update(product(change.productId), { restockArmed: change.armed, ...stamp });
        break;
      // Aliases are written only here and by addAlias (an array union), so edits never drop one.
      case 'pantry.createProduct':
        batch.set(product(change.product.id), {
          ...productFields(change.product),
          aliases: change.product.aliases,
          ...Stamps.created(actor),
          ...stamp,
        });
        break;
      case 'pantry.addAlias':
        batch.update(product(change.productId), { aliases: arrayUnion(change.alias), ...stamp });
        break;
      case 'pantry.updateProduct':
        batch.set(product(change.product.id), { ...productFields(change.product), ...stamp }, { merge: true });
        break;
      case 'pantry.createBatch':
        batch.set(batchDoc(change.batch.id), { ...batchFields(change.batch), ...Stamps.created(actor), ...stamp });
        break;
      case 'pantry.restoreBatch':
        batch.set(batchDoc(change.batch.id), {
          ...batchFields(change.batch),
          ...stamp,
          createdAt: change.batch.createdAt !== null ? Timestamp.fromMillis(change.batch.createdAt) : serverTimestamp(),
          createdBy: actor.uid,
        });
        break;
      case 'pantry.addToBatch':
        batch.update(batchDoc(change.batchId), { quantity: increment(change.delta), ...stamp });
        break;
      case 'pantry.updateBatch':
        batch.update(batchDoc(change.batchId), {
          quantity: change.edit.quantity,
          expiryDate: change.edit.expiryDate,
          dateType: change.edit.dateType,
          location: change.edit.location,
          note: change.edit.note,
          ...stamp,
        });
        break;
      case 'pantry.markOpened':
        batch.update(batchDoc(change.batchId), { openedOn: change.openedOn, expiryDate: change.expiryDate, ...stamp });
        break;
      case 'pantry.setOpenedShelfLife':
        batch.update(product(change.productId), { openedShelfLifeDays: change.days, ...stamp });
        break;
      case 'pantry.syncBatchProduct':
        batch.update(batchDoc(change.batchId), {
          productName: change.name,
          unit: change.unit,
          category: change.category,
          ...stamp,
        });
        break;
      // A tombstone, so other phones' change listeners see the deletion (DESIGN 9.8).
      case 'pantry.deleteBatch':
        batch.set(batchDoc(change.batchId), Tombstones.fields(actor), { merge: true });
        break;
      case 'pantry.logEvent':
        batch.set(doc(db, activity(hid), change.eventId), {
          type: change.eventType === 'added' ? 'stock_added' : 'discarded',
          actorUid: actor.uid,
          actorName: actor.name,
          productId: change.productId,
          params: { name: change.name, quantity: change.quantity, reason: change.reason },
          createdAt: serverTimestamp(),
        });
        break;
      case 'pantry.deleteEvent':
        batch.delete(doc(db, activity(hid), change.eventId));
        break;
    }
    return true;
  },
};
