// Grocery lists and items (feature/grocery/data in the app): the list names, changes-only item listeners and the
// writes of GroceryChange.
import {
  collection,
  deleteField,
  doc,
  getDocFromCache,
  getDocsFromServer,
  increment,
  serverTimestamp,
  Timestamp,
  type DocumentSnapshot,
  type Firestore,
  type WriteBatch,
} from 'firebase/firestore';
import { distinctUntilChanged, map, switchMap, type Observable } from 'rxjs';
import {
  GroceryRules,
  isGroceryChange,
  itemSourceOf,
  type GroceryItem,
  type GroceryList,
  type GroceryRepository,
} from '../domain/grocery/models';
import { CATEGORIES, categoryOf, unitOf, type Category } from '../domain/pantry/models';
import { compareStrings } from '../domain/time';
import type { Actor, HouseholdChange } from '../domain/write';
import { liveDocuments, Stamps, Tombstones, type SyncLog } from './delta-sync';
import type { FirebaseSdk } from './firebase';
import { mapped, querySnapshots, retryAfterOwnWrites } from './firestore-rx';
import { activity } from './pantry';
import type { ChangeMapper } from './writer';

export const lists = (hid: string) => `households/${hid}/groceryLists`;
export const items = (hid: string, listId: string) => `households/${hid}/groceryLists/${listId}/items`;

const num = (value: unknown): number | null => (typeof value === 'number' ? value : null);
const text = (value: unknown): string | null => (typeof value === 'string' ? value : null);

function toList(s: DocumentSnapshot): GroceryList {
  const order = (s.get('aisleOrder') as unknown[] | null) ?? [];
  return {
    id: s.id,
    name: text(s.get('name')) ?? '',
    isDefault: s.get('isDefault') === true,
    aisleOrder: order.filter((c): c is Category => (CATEGORIES as readonly unknown[]).includes(c)),
  };
}

// New fields are optional on read with defaults (DESIGN 8.1); `checked` absent means false (DESIGN 8.2).
export function toItem(s: DocumentSnapshot): GroceryItem {
  const unit = s.get('unit');
  const category = s.get('category');
  return {
    id: s.id,
    name: text(s.get('name')) ?? '',
    nameKey: text(s.get('nameKey')) ?? '',
    quantity: num(s.get('quantity')),
    unit: typeof unit === 'string' ? unitOf(unit) : null,
    note: text(s.get('note')),
    category: typeof category === 'string' ? categoryOf(category) : null,
    checked: s.get('checked') === true,
    checkedBy: text(s.get('checkedBy')),
    source: itemSourceOf(s.get('source')),
    productId: text(s.get('productId')),
    recipeId: text(s.get('recipeId')),
    createdBy: text(s.get('createdBy')),
    createdAt: (s.get('createdAt', { serverTimestamps: 'estimate' }) as Timestamp | null)?.toMillis?.() ?? null,
    pendingSync: s.metadata.hasPendingWrites,
    aliasOf: text(s.get('aliasOf')),
  };
}

export class FirestoreGroceryRepository implements GroceryRepository {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly log: SyncLog,
  ) {}

  observeLists(householdId: string): Observable<GroceryList[]> {
    return this.sdk.instance$.pipe(
      switchMap((db) => querySnapshots(collection(db, lists(householdId))).pipe(retryAfterOwnWrites(db))),
      map((snapshot) =>
        snapshot.docs
          .map(toList)
          .sort(
            (a, b) =>
              Number(b.isDefault) - Number(a.isDefault) || compareStrings(a.name.toLowerCase(), b.name.toLowerCase()),
          ),
      ),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }

  // Changes-only listeners (DESIGN 9.8); each item keeps its pending-sync flag from the cache.
  observeItems(householdId: string, listId: string): Observable<GroceryItem[]> {
    return this.sdk.instance$.pipe(
      switchMap((db) => liveDocuments(db, collection(db, items(householdId, listId)), this.log)),
      map((live) => live.documents.map(toItem)),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }

  async isCached(householdId: string, listId: string, itemId: string): Promise<boolean> {
    try {
      const snapshot = await getDocFromCache(doc(this.sdk.current, items(householdId, listId), itemId));
      return snapshot.exists() && !Tombstones.is(snapshot);
    } catch {
      return false; // not in the cache at all
    }
  }

  async itemIds(householdId: string, listId: string): Promise<string[]> {
    const snapshot = await mapped(getDocsFromServer(collection(this.sdk.current, items(householdId, listId))));
    return snapshot.docs.map((d) => d.id);
  }
}

/** Every field written explicitly, nulls included (DESIGN 8.1). */
const itemFields = (item: GroceryItem) => ({
  name: item.name,
  nameKey: item.nameKey,
  quantity: item.quantity,
  unit: item.unit,
  note: item.note,
  category: item.category,
  checked: item.checked,
  checkedBy: item.checkedBy,
  checkedAt: null,
  source: item.source,
  productId: item.productId,
  recipeId: item.recipeId,
  aliasOf: item.aliasOf,
});

/** Firestore writes for GroceryChange (GroceryChangeMapper). */
export const groceryMapper: ChangeMapper = {
  write(batch: WriteBatch, db: Firestore, hid: string, actor: Actor, change: HouseholdChange): boolean {
    if (!isGroceryChange(change)) return false;
    const list = (id: string) => doc(db, lists(hid), id);
    const item = (listId: string, id: string) => doc(db, items(hid, listId), id);
    const stamp = Stamps.updated(actor);
    switch (change.type) {
      case 'grocery.createList':
        batch.set(list(change.list.id), {
          name: change.list.name,
          isDefault: change.list.isDefault,
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
        break;
      case 'grocery.renameList':
        batch.update(list(change.listId), { name: change.name, updatedAt: serverTimestamp() });
        break;
      case 'grocery.setAisleOrder':
        batch.update(list(change.listId), { aisleOrder: change.order, updatedAt: serverTimestamp() });
        break;
      case 'grocery.deleteList':
        batch.delete(list(change.listId));
        break;
      case 'grocery.createItem':
        batch.set(item(change.listId, change.item.id), {
          ...itemFields(change.item),
          ...Stamps.created(actor),
          ...stamp,
        });
        break;
      case 'grocery.restoreItem':
        batch.set(item(change.listId, change.item.id), {
          ...itemFields(change.item),
          ...stamp,
          createdAt: change.item.createdAt !== null ? Timestamp.fromMillis(change.item.createdAt) : serverTimestamp(),
          createdBy: change.item.createdBy ?? actor.uid,
        });
        break;
      case 'grocery.updateItem':
        batch.update(item(change.listId, change.itemId), {
          quantity: change.quantity,
          unit: change.unit,
          note: change.note,
          category: change.category,
          ...stamp,
        });
        break;
      case 'grocery.renameItem':
        batch.update(item(change.listId, change.itemId), {
          name: change.name,
          nameKey: change.nameKey,
          productId: change.productId,
          ...stamp,
        });
        break;
      case 'grocery.addToItem':
        batch.update(item(change.listId, change.itemId), { quantity: increment(change.delta), ...stamp });
        break;
      case 'grocery.setChecked':
        batch.update(item(change.listId, change.itemId), {
          checked: change.checked,
          checkedBy: change.checked ? actor.uid : null,
          checkedAt: change.checked ? serverTimestamp() : null,
          ...stamp,
        });
        break;
      // A tombstone, so other phones' change listeners see the deletion (DESIGN 9.8).
      case 'grocery.deleteItem':
        if (change.tombstone) batch.set(item(change.listId, change.itemId), Tombstones.fields(actor), { merge: true });
        else batch.delete(item(change.listId, change.itemId));
        break;
      case 'grocery.logEvent':
        batch.set(doc(db, activity(hid), change.eventId), {
          type: change.event,
          actorUid: actor.uid,
          actorName: actor.name,
          params: { name: change.name, listName: change.listName, count: change.count },
          createdAt: serverTimestamp(),
        });
        break;
      case 'grocery.upsertLowStock': {
        // Merge-write with no `checked` field: an item already in the cart stays checked (DESIGN 9.6).
        const fields = {
          name: change.name,
          nameKey: change.nameKey,
          unit: change.unit,
          category: change.category,
          source: 'low_stock',
          productId: change.productId,
          quantity: change.quantity,
          // Coming back after being removed: no longer a tombstone.
          [Tombstones.FIELD]: deleteField(),
          ...stamp,
        };
        const created = change.writeCreated ? { ...Stamps.created(actor), note: null, recipeId: null } : {};
        batch.set(
          item(change.listId, GroceryRules.lowStockItemId(change.productId)),
          { ...fields, ...created },
          {
            merge: true,
          },
        );
        break;
      }
    }
    return true;
  },
};
