// Products and batches (feature/pantry/data in the app): changes-only listeners and the writes of PantryChange.
import {
  collection,
  deleteField,
  doc,
  getDocFromCache,
  increment,
  arrayUnion,
  serverTimestamp,
  Timestamp,
  type DocumentSnapshot,
  type Firestore,
  type WriteBatch,
} from 'firebase/firestore';
import { combineLatest, defer, distinctUntilChanged, map, NEVER, switchMap, type Observable } from 'rxjs';
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
import {
  cachedLiveDocuments,
  changedFields,
  liveDocuments,
  Stamps,
  Tombstones,
  type LiveDocuments,
  type SyncLog,
} from './delta-sync';
import type { FirebaseSdk } from './firebase';
import type { ChangeMapper } from './writer';
import { capTo, ReadCaps } from '../domain/caps';
import { SharedByKey, STOP_TIMEOUT_MS } from '../domain/shared';

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
    name: capTo(str(s.get('name')) ?? '', ReadCaps.NAME),
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
    updatedBy: str(s.get('updatedBy')),
  };
}

export function toBatch(s: DocumentSnapshot): StockBatch {
  return {
    id: s.id,
    productId: str(s.get('productId')) ?? '',
    productName: capTo(str(s.get('productName')) ?? '', ReadCaps.NAME),
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
    updatedBy: str(s.get('updatedBy')),
  };
}

export class FirestorePantryRepository implements PantryRepository {
  /** One pair of listeners per household and phone, shared by every screen (after v1, DESIGN 9.1). */
  private readonly shared: SharedByKey<string, PantryData>;

  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly log: SyncLog,
    stopTimeoutMs: number = STOP_TIMEOUT_MS,
    /** Emits while the page is off screen: the listeners then stop without waiting (domain/shared). */
    stopAtOnce$: Observable<unknown> = NEVER,
  ) {
    this.shared = new SharedByKey(stopTimeoutMs, stopAtOnce$);
  }

  observe(householdId: string): Observable<PantryData> {
    return this.shared.get(householdId, () => this.listen(householdId));
  }

  /**
   * Who deleted `batchId`, from the tombstone in the local cache (no read is billed); null when it isn't there (after
   * v1, DESIGN 9.2: the Edit window says who removed its item).
   */
  async removedBy(householdId: string, batchId: string): Promise<string | null> {
    try {
      const snapshot = await getDocFromCache(doc(this.sdk.current, stock(householdId), batchId));
      return str(snapshot.get('updatedBy'));
    } catch {
      return null;
    }
  }

  /**
   * The local cache only, for the restock engine (after v1, DESIGN 9.6): free, and it holds no server listener, so it
   * costs nothing when the app comes back after a long time. "Read from the server" here means a full read of each
   * collection is on record on this phone.
   */
  observeCached(householdId: string): Observable<PantryData> {
    return this.sdk.instance$.pipe(
      switchMap((db) =>
        combineLatest([
          cachedLiveDocuments(collection(db, products(householdId))),
          cachedLiveDocuments(collection(db, stock(householdId))),
          this.log.fullSyncs$(products(householdId)),
          this.log.fullSyncs$(stock(householdId)),
        ]),
      ),
      map(([p, s, pRead, sRead]) => ({
        products: p.map(toProduct),
        batches: s.map(toBatch),
        fromCache: true,
        serverRead: pRead !== null && sRead !== null,
      })),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }

  // Changes-only listeners (DESIGN 9.8); each batch keeps its pending-sync flag from the cache. The mapping runs once
  // per snapshot for every screen (after v1).
  private listen(householdId: string): Observable<PantryData> {
    return this.sdk.instance$.pipe(
      switchMap((db) =>
        combineLatest([
          this.serverRead(liveDocuments(db, collection(db, products(householdId)), this.log), products(householdId)),
          this.serverRead(liveDocuments(db, collection(db, stock(householdId)), this.log), stock(householdId)),
        ]),
      ),
      map(([[p, pRead], [s, sRead]]) => ({
        products: p.documents.map(toProduct),
        batches: s.documents.map(toBatch),
        fromCache: p.fromCache || s.fromCache,
        serverRead: pRead && sRead,
      })),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }

  /**
   * Pairs each snapshot with whether this collection has been read from the server: a full read on record (a warm
   * cache, so offline still counts) or a server snapshot since this listener started (DESIGN 9.6).
   */
  private serverRead(live: Observable<LiveDocuments>, path: string): Observable<[LiveDocuments, boolean]> {
    return defer(() => {
      let read = this.log.lastFullSync(path) !== null;
      return live.pipe(
        map((l): [LiveDocuments, boolean] => {
          read = read || !l.fromCache;
          return [l, read];
        }),
      );
    });
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
      // Only what changed, so an edit never puts back what another member changed meanwhile (DESIGN 9.4).
      case 'pantry.updateProduct': {
        const changed = changedFields(productFields(change.before), productFields(change.product));
        if (Object.keys(changed).length > 0)
          batch.set(product(change.product.id), { ...changed, ...stamp }, { merge: true });
        break;
      }
      case 'pantry.createBatch':
        batch.set(batchDoc(change.batch.id), { ...batchFields(change.batch), ...Stamps.created(actor), ...stamp });
        break;
      // Undo removes the tombstone and nothing else: the document keeps every field, another member's change and
      // fields a newer app added included, and its creator (DESIGN 8.6).
      case 'pantry.restoreBatch':
        batch.set(batchDoc(change.batch.id), Tombstones.restore(actor), { merge: true });
        break;
      case 'pantry.addToBatch':
        batch.update(batchDoc(change.batchId), {
          quantity: increment(change.delta),
          ...stamp,
          ...(change.revive ? { deletedAt: deleteField() } : {}),
        });
        break;
      case 'pantry.updateBatch': {
        const changed = changedFields(change.before, change.after);
        if (Object.keys(changed).length > 0) batch.update(batchDoc(change.batchId), { ...changed, ...stamp });
        break;
      }
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
